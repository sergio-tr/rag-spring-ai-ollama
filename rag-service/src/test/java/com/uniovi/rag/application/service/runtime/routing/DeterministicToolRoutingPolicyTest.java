package com.uniovi.rag.application.service.runtime.routing;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniovi.rag.application.service.runtime.query.QueryPlanSlotEnricher;
import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.domain.runtime.query.AmbiguityAssessment;
import com.uniovi.rag.domain.runtime.query.AmbiguityStatus;
import com.uniovi.rag.domain.runtime.query.ClassifierStatus;
import com.uniovi.rag.domain.runtime.query.EntityExtractionResult;
import com.uniovi.rag.domain.runtime.query.ExpectedAnswerShape;
import com.uniovi.rag.domain.runtime.query.QueryIntent;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.query.StructuredRewriteResult;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRouteKind;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRoutingMode;
import com.uniovi.rag.domain.knowledge.MaterializationStrategy;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DeterministicToolRoutingPolicyTest {

    private final DeterministicToolRoutingPolicy policy = new DeterministicToolRoutingPolicy();

    @Test
    void selectsDeterministicRoute_whenEnabledAndAmbiguitySufficient() {
        var decision = policy.resolve(rag(true), plan(AmbiguityStatus.SUFFICIENT));
        assertThat(decision.mode()).isEqualTo(AdaptiveRoutingMode.ENABLED);
        assertThat(decision.primaryRouteKind()).isEqualTo(AdaptiveRouteKind.DETERMINISTIC_TOOL_ROUTE);
        assertThat(decision.fallbackWorkflowRouteKind())
                .contains(AdaptiveRouteKind.RETRIEVAL_WORKFLOW_ROUTE);
    }

    @Test
    void fallsBackToWorkflow_whenAmbiguityInsufficient() {
        var decision = policy.resolve(rag(true), plan(AmbiguityStatus.MISSING_INFORMATION));
        assertThat(decision.primaryRouteKind()).isEqualTo(AdaptiveRouteKind.RETRIEVAL_WORKFLOW_ROUTE);
    }

    @Test
    void disabled_whenFlagOff() {
        var decision = policy.resolve(rag(false), plan(AmbiguityStatus.SUFFICIENT));
        assertThat(decision.mode()).isEqualTo(AdaptiveRoutingMode.DISABLED);
        assertThat(decision.primaryRouteKind()).isEqualTo(AdaptiveRouteKind.RETRIEVAL_WORKFLOW_ROUTE);
    }

    @Test
    void structuredGetFieldRoutesDeterministically_whenFunctionCallingEnabled() {
        String question = "dime los participantes del acta del 25 de febrero de 2026";
        Map<String, String> slots = QueryPlanSlotEnricher.enrich(question, Optional.of(QueryType.GET_FIELD), Map.of());
        QueryPlan plan =
                new QueryPlan(
                        QueryPlan.VERSION_P12_MEMORY_CONVERSATIONAL_FLOW_V1,
                        question,
                        question,
                        question,
                        question,
                        "GET_FIELD",
                        Optional.of(QueryType.GET_FIELD),
                        ClassifierStatus.OK,
                        QueryIntent.EXTRACT_FIELD,
                        slots,
                        List.of(),
                        List.of(),
                        EntityExtractionResult.emptyWithNote(""),
                        new StructuredRewriteResult(
                                question,
                                true,
                                List.of(),
                                StructuredRewriteResult.STRATEGY_STRUCTURED_V1,
                                List.of(),
                                List.of(),
                                Optional.of("EXTRACT_FIELD"),
                                Map.of(),
                                List.of()),
                        ExpectedAnswerShape.FIELD_VALUE,
                        new AmbiguityAssessment(AmbiguityStatus.SUFFICIENT, List.of(), List.of()),
                        "cid",
                        "cls",
                        List.of());

        var decision = policy.resolve(demoBestRag(), plan);

        assertThat(decision.primaryRouteKind()).isEqualTo(AdaptiveRouteKind.DETERMINISTIC_TOOL_ROUTE);
        assertThat(decision.reasons()).anyMatch(r -> r.contains("structured_classifier_field_route"));
    }

    @Test
    void structuredGetFieldRoutesDeterministically_whenAmbiguityConflictingButClassifierAuthoritative() {
        String question = "dime los participantes del acta del 25 de febrero de 2026";
        var decision =
                policy.resolve(
                        demoBestRag(),
                        new QueryPlan(
                                QueryPlan.VERSION_P12_MEMORY_CONVERSATIONAL_FLOW_V1,
                                question,
                                question,
                                question,
                                question,
                                "GET_FIELD",
                                Optional.of(QueryType.GET_FIELD),
                                ClassifierStatus.OK,
                                QueryIntent.EXTRACT_FIELD,
                                Map.of("field", "attendees"),
                                List.of(),
                                List.of(),
                                EntityExtractionResult.emptyWithNote(""),
                                StructuredRewriteResult.identityDisabled(question, "test"),
                                ExpectedAnswerShape.FIELD_VALUE,
                                new AmbiguityAssessment(AmbiguityStatus.CONFLICTING_CUES, List.of("CONFLICT"), List.of()),
                                "cid",
                                "cls",
                                List.of()));

        assertThat(decision.primaryRouteKind()).isEqualTo(AdaptiveRouteKind.DETERMINISTIC_TOOL_ROUTE);
    }

    @Test
    void structuredSummarizeMeetingRoutesDeterministically_whenFunctionCallingEnabled() {
        String question = "Resume la reunión del 25 de febrero de 2026";
        var decision =
                policy.resolve(
                        demoBestRag(),
                        new QueryPlan(
                                QueryPlan.VERSION_P12_MEMORY_CONVERSATIONAL_FLOW_V1,
                                question,
                                question,
                                question,
                                question,
                                "SUMMARIZE_MEETING",
                                Optional.of(QueryType.SUMMARIZE_MEETING),
                                ClassifierStatus.OK,
                                QueryIntent.SUMMARIZE,
                                Map.of(),
                                List.of(),
                                List.of(),
                                EntityExtractionResult.emptyWithNote(""),
                                StructuredRewriteResult.identityDisabled(question, "test"),
                                ExpectedAnswerShape.SUMMARY,
                                new AmbiguityAssessment(AmbiguityStatus.SUFFICIENT, List.of(), List.of()),
                                "cid",
                                "cls",
                                List.of()));

        assertThat(decision.primaryRouteKind()).isEqualTo(AdaptiveRouteKind.DETERMINISTIC_TOOL_ROUTE);
        assertThat(decision.reasons()).anyMatch(r -> r.contains("structured_classifier_field_route"));
    }

    @Test
    void structuredBooleanQueryRoutesDeterministically_whenFunctionCallingEnabled() {
        String question = "Confirma si Jorge Moreno Navarro aparece en el acta del 25 de agosto de 2026";
        var decision =
                policy.resolve(
                        demoBestRag(),
                        new QueryPlan(
                                QueryPlan.VERSION_P12_MEMORY_CONVERSATIONAL_FLOW_V1,
                                question,
                                question,
                                question,
                                question,
                                "BOOLEAN_QUERY",
                                Optional.of(QueryType.BOOLEAN_QUERY),
                                ClassifierStatus.OK,
                                QueryIntent.BOOLEAN_CHECK,
                                Map.of(),
                                List.of(),
                                List.of(),
                                EntityExtractionResult.emptyWithNote(""),
                                StructuredRewriteResult.identityDisabled(question, "test"),
                                ExpectedAnswerShape.SCALAR_BOOLEAN,
                                new AmbiguityAssessment(AmbiguityStatus.SUFFICIENT, List.of(), List.of()),
                                "cid",
                                "cls",
                                List.of()));

        assertThat(decision.primaryRouteKind()).isEqualTo(AdaptiveRouteKind.DETERMINISTIC_TOOL_ROUTE);
        assertThat(decision.reasons()).anyMatch(r -> r.contains("structured_classifier_field_route"));
    }

    private static RagConfig demoBestRag() {
        return new RagConfig(
                false,
                false,
                true,
                true,
                true,
                false,
                false,
                true,
                true,
                true,
                false,
                false,
                false,
                false,
                true,
                12,
                0.55,
                "llm",
                "emb",
                "cls",
                "COT",
                false,
                32000,
                24000,
                false,
                MaterializationStrategy.CHUNK_LEVEL);
    }

    private static RagConfig rag(boolean deterministicToolRoutingEnabled) {
        return new RagConfig(
                false,
                false,
                true,
                true,
                true,
                false,
                false,
                false,
                true,
                false,
                false,
                false,
                false,
                false,
                deterministicToolRoutingEnabled,
                10,
                0.7,
                "llm",
                "emb",
                "cls",
                "SIMPLE",
                false,
                32000,
                24000,
                false,
                MaterializationStrategy.CHUNK_LEVEL);
    }

    private static QueryPlan plan(AmbiguityStatus ambiguity) {
        return new QueryPlan(
                QueryPlan.VERSION_P12_MEMORY_CONVERSATIONAL_FLOW_V1,
                "q",
                "q",
                "q",
                "q",
                "COUNT_DOCUMENTS",
                Optional.empty(),
                ClassifierStatus.OK,
                QueryIntent.COUNT,
                Map.of(),
                List.of(),
                List.of(),
                EntityExtractionResult.emptyWithNote(""),
                StructuredRewriteResult.identityDisabled("q", "test"),
                ExpectedAnswerShape.SCALAR_COUNT,
                new AmbiguityAssessment(ambiguity, List.of(), List.of()),
                "cid",
                "cls",
                List.of());
    }
}
