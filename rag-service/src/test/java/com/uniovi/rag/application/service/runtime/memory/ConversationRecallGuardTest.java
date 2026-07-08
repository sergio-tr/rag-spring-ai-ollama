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

class ConversationRecallGuardTest {

    @Test
    void shouldShortCircuit_forConversationMetaQueryWithoutHistory() {
        ConversationHistoryLoader loader = mock(ConversationHistoryLoader.class);
        when(loader.loadEligibleHistory(any())).thenReturn(List.of());
        ConversationRecallGuard guard = new ConversationRecallGuard(loader);

        ExecutionContext ctx =
                ctx("¿De qué hablamos antes?", ConversationMemoryOutcome.NO_HISTORY_AVAILABLE, false);
        assertThat(guard.shouldShortCircuit(ctx)).isTrue();
        assertThat(ConversationRecallGuard.noEligibleHistoryResponse())
                .containsIgnoringCase("no hemos")
                .containsIgnoringCase("primera");
    }

    @Test
    void shouldShortCircuit_evenWhenMemoryDisabled() {
        ConversationHistoryLoader loader = mock(ConversationHistoryLoader.class);
        when(loader.loadEligibleHistory(any())).thenReturn(List.of());
        ConversationRecallGuard guard = new ConversationRecallGuard(loader);

        ExecutionContext ctx =
                ctx("¿De qué hablamos antes?", ConversationMemoryOutcome.DISABLED_BY_CONFIG, false);
        assertThat(guard.shouldShortCircuit(ctx)).isTrue();
    }

    @Test
    void shouldNotShortCircuit_whenHistoryExists() {
        ConversationHistoryLoader loader = mock(ConversationHistoryLoader.class);
        when(loader.loadEligibleHistory(any()))
                .thenReturn(
                        List.of(
                                new ConversationMemoryTurn(UUID.randomUUID(), 1, MessageRole.USER, "hola"),
                                new ConversationMemoryTurn(
                                        UUID.randomUUID(), 2, MessageRole.ASSISTANT, "acta 25/02/2026")));
        ConversationRecallGuard guard = new ConversationRecallGuard(loader);

        ExecutionContext ctx =
                ctx(
                        "¿De qué hablamos antes y quién presidió esa reunión?",
                        ConversationMemoryOutcome.MEMORY_APPLIED,
                        true);
        assertThat(guard.shouldShortCircuit(ctx)).isFalse();
    }

    @Test
    void shouldShortCircuitAmbiguousParticipants_withoutLocalAnchor() {
        ConversationHistoryLoader loader = mock(ConversationHistoryLoader.class);
        when(loader.loadEligibleHistory(any())).thenReturn(List.of());
        ConversationRecallGuard guard = new ConversationRecallGuard(loader);

        ExecutionContext ctx =
                ctx(
                        "¿Cuántos participantes asistieron?",
                        ConversationMemoryOutcome.NO_HISTORY_AVAILABLE,
                        false);
        assertThat(guard.shouldShortCircuitAmbiguousActaQuery(ctx)).isTrue();
        assertThat(ConversationRecallGuard.missingActaDateResponse())
                .containsIgnoringCase("acta")
                .containsIgnoringCase("fecha");
    }

    @Test
    void shouldNotShortCircuitAmbiguousParticipants_whenSameConversationHasDateAnchor() {
        ConversationHistoryLoader loader = mock(ConversationHistoryLoader.class);
        when(loader.loadEligibleHistory(any()))
                .thenReturn(
                        List.of(
                                new ConversationMemoryTurn(
                                        UUID.randomUUID(),
                                        1,
                                        MessageRole.USER,
                                        "¿Cuántos participantes asistieron a la reunión del 25/02/2026?"),
                                new ConversationMemoryTurn(
                                        UUID.randomUUID(), 2, MessageRole.ASSISTANT, "17 participantes")));
        ConversationRecallGuard guard = new ConversationRecallGuard(loader);

        ExecutionContext ctx =
                ctx(
                        "¿Quiénes fueron ellos?",
                        ConversationMemoryOutcome.MEMORY_APPLIED,
                        true);
        assertThat(guard.shouldShortCircuitAmbiguousActaQuery(ctx)).isFalse();
    }

    @Test
    void fdFl03_compoundAugustFilter_shouldNotShortCircuitToClarification() {
        ConversationHistoryLoader loader = mock(ConversationHistoryLoader.class);
        when(loader.loadEligibleHistory(any())).thenReturn(List.of());
        ConversationRecallGuard guard = new ConversationRecallGuard(loader);

        String query =
                "¿Qué reuniones celebradas en agosto hablaron sobre videovigilancia y tuvieron más de 18 asistentes?";
        ExecutionContext ctx = ctx(query, ConversationMemoryOutcome.NO_HISTORY_AVAILABLE, false);

        assertThat(guard.shouldShortCircuitAmbiguousActaQuery(ctx)).isFalse();
        assertThat(ConversationRecallGuard.isAmbiguousActaScopedWithoutDate(query)).isFalse();
    }

    @Test
    void corpusWideAscensorList_shouldNotShortCircuitToClarification() {
        ConversationHistoryLoader loader = mock(ConversationHistoryLoader.class);
        when(loader.loadEligibleHistory(any())).thenReturn(List.of());
        ConversationRecallGuard guard = new ConversationRecallGuard(loader);

        String query = "dime las actas donde se comentan problemas del ascensor";
        ExecutionContext ctx = ctx(query, ConversationMemoryOutcome.NO_HISTORY_AVAILABLE, false);

        assertThat(guard.shouldShortCircuitAmbiguousActaQuery(ctx)).isFalse();
        assertThat(ConversationRecallGuard.isAmbiguousActaScopedWithoutDate(query)).isFalse();
    }

    @Test
    void explicitActaPdfReference_shouldNotShortCircuitAcuerdoQuestion() {
        ConversationHistoryLoader loader = mock(ConversationHistoryLoader.class);
        when(loader.loadEligibleHistory(any())).thenReturn(List.of());
        ConversationRecallGuard guard = new ConversationRecallGuard(loader);

        String query = "¿Qué acuerdo se tomó sobre el ascensor en ACTA 6.pdf?";
        ExecutionContext ctx = ctx(query, ConversationMemoryOutcome.NO_HISTORY_AVAILABLE, false);

        assertThat(guard.shouldShortCircuitAmbiguousActaQuery(ctx)).isFalse();
        assertThat(ConversationRecallGuard.isAmbiguousActaScopedWithoutDate(query)).isFalse();
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

    private static ExecutionContext ctx(
            String query, ConversationMemoryOutcome memoryOutcome, boolean memoryEnabled) {
        RagConfig rag = rag(memoryEnabled);
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
                query,
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
                query,
                query,
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
}
