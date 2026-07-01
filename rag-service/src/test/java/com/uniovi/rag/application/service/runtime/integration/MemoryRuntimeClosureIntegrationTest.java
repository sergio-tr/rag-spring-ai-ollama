package com.uniovi.rag.application.service.runtime.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.uniovi.rag.application.service.runtime.clarification.ClarificationPolicyResolver;
import com.uniovi.rag.application.service.runtime.clarification.ClarificationQuestionGenerator;
import com.uniovi.rag.application.service.runtime.memory.ConversationFollowUpResolver;
import com.uniovi.rag.application.service.runtime.memory.ConversationHistoryLoader;
import com.uniovi.rag.application.service.runtime.memory.ConversationMemoryAnchorMetadata;
import com.uniovi.rag.application.service.runtime.memory.ConversationRecallGuard;
import com.uniovi.rag.application.service.runtime.query.DefaultAmbiguityAssessmentService;
import com.uniovi.rag.domain.MessageRole;
import com.uniovi.rag.domain.config.capability.CapabilitySet;
import com.uniovi.rag.domain.config.indexing.ReindexImpact;
import com.uniovi.rag.domain.config.prompt.SystemPromptLayers;
import com.uniovi.rag.domain.config.runtime.ConfigProvenance;
import com.uniovi.rag.domain.config.runtime.ResolvedRuntimeConfig;
import com.uniovi.rag.domain.config.validation.CompatibilityResult;
import com.uniovi.rag.domain.knowledge.MaterializationStrategy;
import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.domain.runtime.clarification.ClarificationDecision;
import com.uniovi.rag.domain.runtime.clarification.ClarificationOutcome;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.engine.KnowledgeSnapshotSelection;
import com.uniovi.rag.domain.runtime.engine.RuntimeOperationKind;
import com.uniovi.rag.domain.runtime.memory.ConversationMemoryOutcome;
import com.uniovi.rag.domain.runtime.memory.ConversationMemoryTurn;
import com.uniovi.rag.domain.runtime.query.AmbiguityAssessment;
import com.uniovi.rag.domain.runtime.query.AmbiguityStatus;
import com.uniovi.rag.domain.runtime.query.ClassifierStatus;
import com.uniovi.rag.domain.runtime.query.EntityExtractionResult;
import com.uniovi.rag.domain.runtime.query.ExpectedAnswerShape;
import com.uniovi.rag.domain.runtime.query.NormalizedQuery;
import com.uniovi.rag.domain.runtime.query.QueryIntent;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.query.StructuredRewriteResult;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRouteKind;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRoutingOutcome;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * runtime closure: persisted assistant structured anchor must drive follow-up expansion,
 * ambiguity assessment, recall guard, and clarification policy without date clarification.
 */
class MemoryRuntimeClosureIntegrationTest {

    private static final List<ConversationMemoryTurn> ANCHORED_ACTA_FEB_24_HISTORY =
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
                            "Asistieron 18 personas al acta del 24 de febrero de 2025.",
                            Map.of(
                                    ConversationMemoryAnchorMetadata.ANCHORED_ACTA_DATE,
                                    "2025-02-24",
                                    ConversationMemoryAnchorMetadata.LAST_REFERENCED_DATE,
                                    "2025-02-24")));

    private final DefaultAmbiguityAssessmentService ambiguity = new DefaultAmbiguityAssessmentService();
    private final ClarificationPolicyResolver clarification =
            new ClarificationPolicyResolver(new ClarificationQuestionGenerator());

    @Test
    void runtimeFollowUpPresidentUsesPersistedAssistantAnchor() {
        String raw = "¿quién fue el presidente?";
        String expanded = expand(raw);

        assertThat(expanded).contains("2025-02-24").containsIgnoringCase("presidente");

        AmbiguityAssessment assessment = assessExpanded(expanded);
        assertThat(assessment.status()).isEqualTo(AmbiguityStatus.SUFFICIENT);

        ExecutionContext ctx = ctxWithEffectiveQuery(raw, expanded);
        assertThat(guardWithHistory(ANCHORED_ACTA_FEB_24_HISTORY).shouldShortCircuitAmbiguousActaQuery(ctx))
                .isFalse();

        ClarificationDecision decision = clarification.resolve(ctx, plan(expanded, assessment));
        assertThat(decision.ask()).isFalse();
        assertThat(decision.terminalOutcome()).isEqualTo(ClarificationOutcome.NOT_NEEDED);
    }

    @Test
    void runtimeFollowUpSecretaryUsesPersistedAssistantAnchor() {
        String raw = "y quién fue la secretaria?";
        String expanded = expand(raw);

        assertThat(expanded).contains("2025-02-24").containsIgnoringCase("secretaria");

        AmbiguityAssessment assessment = assessExpanded(expanded);
        assertThat(assessment.status()).isEqualTo(AmbiguityStatus.SUFFICIENT);

        ExecutionContext ctx = ctxWithEffectiveQuery(raw, expanded);
        assertThat(guardWithHistory(ANCHORED_ACTA_FEB_24_HISTORY).shouldShortCircuitAmbiguousActaQuery(ctx))
                .isFalse();

        ClarificationDecision decision = clarification.resolve(ctx, plan(expanded, assessment));
        assertThat(decision.ask()).isFalse();
    }

    @Test
    void runtimeFollowUpStartEndTimeUsesPersistedAssistantAnchor() {
        String raw = "¿a qué hora empezó y a qué hora terminó esa acta?";
        String expanded = expand(raw);

        assertThat(expanded).contains("2025-02-24");

        AmbiguityAssessment assessment = assessExpanded(expanded);
        assertThat(assessment.status()).isEqualTo(AmbiguityStatus.SUFFICIENT);

        ExecutionContext ctx = ctxWithEffectiveQuery(raw, expanded);
        assertThat(guardWithHistory(ANCHORED_ACTA_FEB_24_HISTORY).shouldShortCircuitAmbiguousActaQuery(ctx))
                .isFalse();

        ClarificationDecision decision = clarification.resolve(ctx, plan(expanded, assessment));
        assertThat(decision.ask()).isFalse();
    }

    @Test
    void pendingClarificationIsNotCreatedWhenStructuredAnchorExists() {
        String raw = "¿quién fue el presidente?";
        String expanded = expand(raw);

        AmbiguityAssessment rawAssessment = assessExpanded(raw);
        assertThat(rawAssessment.status()).isEqualTo(AmbiguityStatus.MISSING_INFORMATION);

        AmbiguityAssessment expandedAssessment = assessExpanded(expanded);
        assertThat(expandedAssessment.status()).isEqualTo(AmbiguityStatus.SUFFICIENT);

        ExecutionContext ctx = ctxWithEffectiveQuery(raw, expanded);
        ClarificationDecision decision = clarification.resolve(ctx, plan(expanded, expandedAssessment));

        assertThat(decision.ask()).isFalse();
        assertThat(decision.terminalOutcome()).isEqualTo(ClarificationOutcome.NOT_NEEDED);
        assertThat(ctx.pendingClarificationLoadedForTrace()).isFalse();
        assertThat(ctx.validPendingExistedAtLoad()).isFalse();
    }

    private static String expand(String raw) {
        return ConversationFollowUpResolver.expand(ANCHORED_ACTA_FEB_24_HISTORY, raw).orElseThrow();
    }

    private AmbiguityAssessment assessExpanded(String effectivePlanningInput) {
        return ambiguity.assess(
                new NormalizedQuery("raw", effectivePlanningInput, List.of()),
                Optional.of(QueryType.GET_FIELD),
                QueryType.GET_FIELD.name(),
                ClassifierStatus.OK,
                StructuredRewriteResult.identityFallback(effectivePlanningInput, null),
                EntityExtractionResult.emptyWithNote(null));
    }

    private static ConversationRecallGuard guardWithHistory(List<ConversationMemoryTurn> history) {
        ConversationHistoryLoader loader = mock(ConversationHistoryLoader.class);
        when(loader.loadEligibleHistory(any())).thenReturn(history);
        return new ConversationRecallGuard(loader);
    }

    private static QueryPlan plan(String normalizedText, AmbiguityAssessment assessment) {
        return new QueryPlan(
                QueryPlan.VERSION_P11_QU_CLARIFICATION_CORE_V1,
                "raw",
                normalizedText,
                normalizedText,
                "rw",
                QueryType.GET_FIELD.name(),
                Optional.of(QueryType.GET_FIELD),
                ClassifierStatus.OK,
                QueryIntent.UNKNOWN,
                Map.of(),
                List.of(),
                List.of(),
                EntityExtractionResult.emptyWithNote(null),
                StructuredRewriteResult.identityFallback(normalizedText, null),
                ExpectedAnswerShape.UNKNOWN,
                assessment,
                "c",
                "",
                List.of());
    }

    private static ExecutionContext ctxWithEffectiveQuery(String userQuery, String effectivePlanningInput) {
        RagConfig rag =
                new RagConfig(
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        true,
                        true,
                        false,
                        true,
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
                ConversationMemoryOutcome.MEMORY_APPLIED,
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
