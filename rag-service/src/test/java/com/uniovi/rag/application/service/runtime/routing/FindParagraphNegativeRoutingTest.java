package com.uniovi.rag.application.service.runtime.routing;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniovi.rag.application.service.runtime.clarification.ClarificationPolicyResolver;
import com.uniovi.rag.application.service.runtime.clarification.ClarificationQuestionGenerator;
import com.uniovi.rag.application.service.runtime.query.DefaultAmbiguityAssessmentService;
import com.uniovi.rag.application.service.runtime.tool.DefaultDeterministicToolResolver;
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
import com.uniovi.rag.domain.runtime.tool.DeterministicToolKind;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolOutcome;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** FIND_PARAGRAPH routing for topic-absence queries and undated count clarification contrast. */
class FindParagraphNegativeRoutingTest {

    private static final String GAS_LEAK_QUERY = "¿Qué se comentó respecto a la fuga de gas?";

    private final DefaultAmbiguityAssessmentService ambiguity = new DefaultAmbiguityAssessmentService();
    private final ClarificationPolicyResolver clarification =
            new ClarificationPolicyResolver(new ClarificationQuestionGenerator());
    private final DeterministicToolRoutingPolicy routingPolicy = new DeterministicToolRoutingPolicy();
    private final DefaultDeterministicToolResolver toolResolver = new DefaultDeterministicToolResolver();

    @Test
    void findParagraphAbsenceQuery_hasSufficientAmbiguity() {
        AmbiguityAssessment assessment =
                ambiguity.assess(
                        new NormalizedQuery("raw", GAS_LEAK_QUERY, List.of()),
                        Optional.of(QueryType.FIND_PARAGRAPH),
                        QueryType.FIND_PARAGRAPH.name(),
                        ClassifierStatus.OK,
                        StructuredRewriteResult.identityFallback(GAS_LEAK_QUERY, null),
                        EntityExtractionResult.emptyWithNote(null));

        assertThat(assessment.status()).isEqualTo(AmbiguityStatus.SUFFICIENT);
    }

    @Test
    void findParagraphAbsenceQuery_doesNotAskClarification() {
        ClarificationDecision decision =
                clarification.resolve(
                        ctx(GAS_LEAK_QUERY),
                        plan(GAS_LEAK_QUERY, QueryType.FIND_PARAGRAPH, AmbiguityStatus.SUFFICIENT));

        assertThat(decision.ask()).isFalse();
        assertThat(decision.terminalOutcome()).isEqualTo(ClarificationOutcome.NOT_NEEDED);
    }

    @Test
    void findParagraphAbsenceQuery_selectsDeterministicToolRoute() {
        assertThat(
                        routingPolicy
                                .resolve(
                                        ragWithTools(),
                                        plan(GAS_LEAK_QUERY, QueryType.FIND_PARAGRAPH, AmbiguityStatus.SUFFICIENT))
                                .primaryRouteKind())
                .isEqualTo(AdaptiveRouteKind.DETERMINISTIC_TOOL_ROUTE);
    }

    @Test
    void findParagraphAbsenceQuery_selectsFindParagraphTool() {
        var decision =
                toolResolver.resolve(
                        ctx(GAS_LEAK_QUERY),
                        plan(GAS_LEAK_QUERY, QueryType.FIND_PARAGRAPH, AmbiguityStatus.SUFFICIENT));

        assertThat(decision.outcome()).isEqualTo(DeterministicToolOutcome.SELECTED);
        assertThat(decision.selected()).isTrue();
        assertThat(decision.selectedToolKind()).contains(DeterministicToolKind.FIND_PARAGRAPH_TOOL);
    }

    @Test
    void undatedParticipantCountQuery_stillRequiresClarification() {
        String query = "¿Cuántos participantes asistieron?";
        AmbiguityAssessment assessment =
                ambiguity.assess(
                        new NormalizedQuery("raw", query, List.of()),
                        Optional.of(QueryType.COUNT_DOCUMENTS),
                        QueryType.COUNT_DOCUMENTS.name(),
                        ClassifierStatus.OK,
                        StructuredRewriteResult.identityFallback(query, null),
                        EntityExtractionResult.emptyWithNote(null));

        assertThat(assessment.status()).isEqualTo(AmbiguityStatus.MISSING_INFORMATION);

        ClarificationDecision decision =
                clarification.resolve(
                        ctx(query), plan(query, QueryType.COUNT_DOCUMENTS, AmbiguityStatus.MISSING_INFORMATION));

        assertThat(decision.ask()).isTrue();
    }

    private static QueryPlan plan(String query, QueryType queryType, AmbiguityStatus ambiguityStatus) {
        return new QueryPlan(
                QueryPlan.VERSION_P6_QU_CORE_V1,
                query,
                query,
                query,
                query,
                queryType.name(),
                Optional.of(queryType),
                ClassifierStatus.OK,
                queryType == QueryType.FIND_PARAGRAPH ? QueryIntent.FIND : QueryIntent.COUNT,
                Map.of(),
                List.of(),
                List.of(),
                EntityExtractionResult.emptyWithNote(null),
                StructuredRewriteResult.identityFallback(query, null),
                queryType == QueryType.FIND_PARAGRAPH
                        ? ExpectedAnswerShape.PARAGRAPH
                        : ExpectedAnswerShape.SCALAR_COUNT,
                new AmbiguityAssessment(ambiguityStatus, List.of(), List.of()),
                "corr",
                "default",
                List.of());
    }

    private static ExecutionContext ctx(String query) {
        RagConfig rag = ragWithTools();
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
        return new ExecutionContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                query,
                RuntimeOperationKind.CHAT_MESSAGE,
                resolved,
                "sys",
                KnowledgeSnapshotSelection.empty(),
                Optional.empty(),
                Optional.empty(),
                "corr",
                List.of("all"),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                query,
                query,
                Optional.empty(),
                ConversationMemoryOutcome.DISABLED_BY_CONFIG,
                List.of(),
                false,
                false,
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

    private static RagConfig ragWithTools() {
        return new RagConfig(
                false,
                false,
                true,
                false,
                false,
                false,
                false,
                true,
                true,
                false,
                false,
                false,
                false,
                false,
                true,
                5,
                0.2,
                "llm",
                "emb",
                "cls",
                "reason",
                false,
                RagConfig.DEFAULT_NAIVE_FULL_CORPUS_MAX_CHARS,
                RagConfig.DEFAULT_ADVANCED_RETRIEVAL_MAX_CONTEXT_CHARS,
                false,
                MaterializationStrategy.CHUNK_LEVEL);
    }
}
