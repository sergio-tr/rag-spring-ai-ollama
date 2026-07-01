package com.uniovi.rag.application.service.runtime.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.uniovi.rag.domain.MessageRole;
import com.uniovi.rag.domain.config.capability.CapabilitySet;
import com.uniovi.rag.domain.config.indexing.ReindexImpact;
import com.uniovi.rag.domain.config.prompt.SystemPromptLayers;
import com.uniovi.rag.domain.config.runtime.ConfigProvenance;
import com.uniovi.rag.domain.config.runtime.ResolvedRuntimeConfig;
import com.uniovi.rag.domain.config.validation.CompatibilityResult;
import com.uniovi.rag.domain.knowledge.MaterializationStrategy;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.engine.KnowledgeSnapshotSelection;
import com.uniovi.rag.domain.runtime.engine.RuntimeOperationKind;
import com.uniovi.rag.domain.runtime.memory.ConversationMemoryOutcome;
import com.uniovi.rag.domain.runtime.memory.ConversationMemoryTurn;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRouteKind;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRoutingOutcome;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MemoryGuardOrderTest {

    private static final List<ConversationMemoryTurn> ACTA_FEB_24_HISTORY =
            List.of(
                    new ConversationMemoryTurn(
                            UUID.randomUUID(),
                            1,
                            MessageRole.USER,
                            "¿Quiénes fueron los asistentes del acta del 24 de febrero de 2025?"),
                    new ConversationMemoryTurn(
                            UUID.randomUUID(),
                            2,
                            MessageRole.ASSISTANT,
                            "Asistieron 12 personas al acta del 24 de febrero de 2025."));

    @Test
    void presidentFollowUpUsesPreviousActaDateWithoutClarification() {
        String expanded =
                ConversationFollowUpResolver.expand(ACTA_FEB_24_HISTORY, "¿quién fue el presidente?")
                        .orElseThrow();
        assertThat(expanded)
                .containsIgnoringCase("presidente")
                .contains("24 de febrero de 2025");

        ConversationRecallGuard guard = guardWithHistory(ACTA_FEB_24_HISTORY);
        ExecutionContext ctx =
                ctxWithEffectiveQuery(
                        "¿quién fue el presidente?",
                        expanded,
                        ConversationMemoryOutcome.MEMORY_APPLIED);
        assertThat(guard.shouldShortCircuitAmbiguousActaQuery(ctx)).isFalse();
    }

    @Test
    void secretaryFollowUpUsesPreviousActaDateWithoutClarification() {
        String expanded =
                ConversationFollowUpResolver.expand(ACTA_FEB_24_HISTORY, "y quién fue la secretaria?")
                        .orElseThrow();
        assertThat(expanded)
                .containsIgnoringCase("secretaria")
                .contains("24 de febrero de 2025");

        ConversationRecallGuard guard = guardWithHistory(ACTA_FEB_24_HISTORY);
        ExecutionContext ctx =
                ctxWithEffectiveQuery(
                        "y quién fue la secretaria?",
                        expanded,
                        ConversationMemoryOutcome.MEMORY_APPLIED);
        assertThat(guard.shouldShortCircuitAmbiguousActaQuery(ctx)).isFalse();
    }

    @Test
    void startEndTimeFollowUpUsesPreviousActaDateWithoutClarification() {
        String expanded =
                ConversationFollowUpResolver.expand(
                                ACTA_FEB_24_HISTORY,
                                "¿a qué hora empezó y a qué hora terminó esa acta?")
                        .orElseThrow();
        assertThat(expanded).contains("24 de febrero de 2025");

        ConversationRecallGuard guard = guardWithHistory(ACTA_FEB_24_HISTORY);
        ExecutionContext ctx =
                ctxWithEffectiveQuery(
                        "¿a qué hora empezó y a qué hora terminó esa acta?",
                        expanded,
                        ConversationMemoryOutcome.MEMORY_APPLIED);
        assertThat(guard.shouldShortCircuitAmbiguousActaQuery(ctx)).isFalse();
    }

    @Test
    void recallGuardRunsAfterMemoryExpansion() {
        String raw = "¿quién fue el presidente?";
        String expanded =
                ConversationFollowUpResolver.expand(ACTA_FEB_24_HISTORY, raw).orElseThrow();

        ConversationHistoryLoader emptyHistory = mock(ConversationHistoryLoader.class);
        when(emptyHistory.loadEligibleHistory(any())).thenReturn(List.of());
        ConversationRecallGuard guard = new ConversationRecallGuard(emptyHistory);

        ExecutionContext withoutExpansion =
                ctxWithEffectiveQuery(raw, raw, ConversationMemoryOutcome.CONDENSE_FAILED_FALLBACK);
        assertThat(guard.shouldShortCircuitAmbiguousActaQuery(withoutExpansion)).isTrue();

        ExecutionContext withExpansion =
                ctxWithEffectiveQuery(raw, expanded, ConversationMemoryOutcome.MEMORY_APPLIED);
        assertThat(ConversationRecallGuard.effectiveQueryForActaGuard(withExpansion)).contains("24 de febrero de 2025");
        assertThat(guard.shouldShortCircuitAmbiguousActaQuery(withExpansion)).isFalse();
    }

    @Test
    void ambiguousGuardStillAsksClarificationWhenNoHistoryAnchorExists() {
        ConversationHistoryLoader loader = mock(ConversationHistoryLoader.class);
        when(loader.loadEligibleHistory(any())).thenReturn(List.of());
        ConversationRecallGuard guard = new ConversationRecallGuard(loader);

        ExecutionContext ctx =
                ctxWithEffectiveQuery(
                        "¿quién fue el presidente?",
                        "¿quién fue el presidente?",
                        ConversationMemoryOutcome.NO_HISTORY_AVAILABLE);

        assertThat(guard.shouldShortCircuitAmbiguousActaQuery(ctx)).isTrue();
        assertThat(ConversationRecallGuard.missingActaDateResponse())
                .containsIgnoringCase("fecha");
    }

    private static ConversationRecallGuard guardWithHistory(List<ConversationMemoryTurn> history) {
        ConversationHistoryLoader loader = mock(ConversationHistoryLoader.class);
        when(loader.loadEligibleHistory(any())).thenReturn(history);
        return new ConversationRecallGuard(loader);
    }

    private static ExecutionContext ctxWithEffectiveQuery(
            String userQuery, String effectivePlanningInput, ConversationMemoryOutcome memoryOutcome) {
        RagConfig rag = rag(true);
        ResolvedRuntimeConfig resolved =
                new ResolvedRuntimeConfig(
                        rag,
                        CapabilitySet.fromRagConfig(rag),
                        CompatibilityResult.ok(),
                        ReindexImpact.none(),
                        new SystemPromptLayers("", "", "", ""),
                        "sys",
                        new ConfigProvenance(null, null, null, List.of(), null, null),
                        rag);
        UUID id = UUID.randomUUID();
        return new ExecutionContext(
                id,
                id,
                id,
                userQuery,
                RuntimeOperationKind.CHAT_MESSAGE,
                resolved,
                "sys",
                KnowledgeSnapshotSelection.empty(),
                Optional.empty(),
                Optional.empty(),
                "c",
                List.of("all"),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                userQuery,
                effectivePlanningInput,
                Optional.empty(),
                memoryOutcome,
                List.of(),
                true,
                true,
                false,
                false,
                false,
                false,
                false,
                false,
                Optional.empty(),
                Optional.empty(),
                false,
                AdaptiveRoutingOutcome.DISABLED_BY_CONFIG,
                AdaptiveRouteKind.DIRECT_WORKFLOW_ROUTE,
                false,
                Optional.empty(),
                false,
                List.of());
    }

    private static RagConfig rag(boolean memoryEnabled) {
        return new RagConfig(
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                memoryEnabled,
                false,
                false,
                5,
                0.2,
                "l",
                "e",
                "c",
                "r",
                false,
                RagConfig.DEFAULT_NAIVE_FULL_CORPUS_MAX_CHARS,
                RagConfig.DEFAULT_ADVANCED_RETRIEVAL_MAX_CONTEXT_CHARS,
                MaterializationStrategy.CHUNK_LEVEL);
    }
}
