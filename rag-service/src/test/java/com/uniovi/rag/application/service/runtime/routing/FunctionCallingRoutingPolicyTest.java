package com.uniovi.rag.application.service.runtime.routing;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniovi.rag.domain.knowledge.MaterializationStrategy;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FunctionCallingRoutingPolicyTest {

    private final FunctionCallingRoutingPolicy policy = new FunctionCallingRoutingPolicy();

    @Test
    void resolve_selectsFunctionCallingRoute_whenEnabledWithoutAdaptiveOrDeterministicRouting() {
        var decision = policy.resolve(p9Rag(), minimalPlan());

        assertThat(decision.primaryRouteKind()).isEqualTo(AdaptiveRouteKind.FUNCTION_CALLING_ROUTE);
        assertThat(decision.fallbackWorkflowRouteKind())
                .contains(AdaptiveRouteKind.RETRIEVAL_WORKFLOW_ROUTE);
    }

    @Test
    void resolve_selectsFunctionCallingRoute_whenDeterministicToolRoutingAlsoEnabled() {
        RagConfig rag =
                new RagConfig(
                        true,
                        true,
                        true,
                        true,
                        true,
                        true,
                        true,
                        true,
                        true,
                        false,
                        false,
                        false,
                        false,
                        false,
                        true,
                        12,
                        0.6,
                        "llm",
                        "emb",
                        "cls",
                        "SIMPLE",
                        false,
                        32_000,
                        24_000,
                        false,
                        MaterializationStrategy.HYBRID);

        var decision = policy.resolve(rag, minimalPlan());

        assertThat(decision.primaryRouteKind()).isEqualTo(AdaptiveRouteKind.FUNCTION_CALLING_ROUTE);
    }

    @Test
    void resolve_fallsBackToRetrieval_forExplicitActaFilenameFieldQuery() {
        QueryPlan plan =
                new QueryPlan(
                        QueryPlan.VERSION_P6_QU_CORE_V1,
                        "¿En qué fecha se celebró la reunión recogida en ACTA 1.pdf?",
                        "¿En qué fecha se celebró la reunión recogida en ACTA 1.pdf?",
                        "¿En qué fecha se celebró la reunión recogida en ACTA 1.pdf?",
                        "rw",
                        "lbl",
                        Optional.empty(),
                        ClassifierStatus.OK,
                        QueryIntent.FIND,
                        Map.of(),
                        List.of(),
                        List.of(),
                        EntityExtractionResult.emptyWithNote(""),
                        StructuredRewriteResult.identityDisabled("norm", ""),
                        ExpectedAnswerShape.UNKNOWN,
                        new AmbiguityAssessment(AmbiguityStatus.SUFFICIENT, List.of(), List.of()),
                        "corr",
                        "",
                        List.of());

        var decision = policy.resolve(p9Rag(), plan);

        assertThat(decision.primaryRouteKind()).isEqualTo(AdaptiveRouteKind.RETRIEVAL_WORKFLOW_ROUTE);
        assertThat(decision.reasons()).anyMatch(r -> r.contains("explicit_acta_filename_field_query"));
    }

    @Test
    void resolve_fallsBackToRetrieval_whenFunctionCallingNotApplicable() {
        QueryPlan topicPlan =
                new QueryPlan(
                        QueryPlan.VERSION_P6_QU_CORE_V1,
                        "en qué actas se habla sobre cámaras de seguridad",
                        "en qué actas se habla sobre cámaras de seguridad",
                        "en qué actas se habla sobre cámaras de seguridad",
                        "rw",
                        "lbl",
                        Optional.empty(),
                        ClassifierStatus.OK,
                        QueryIntent.FIND,
                        Map.of(),
                        List.of(),
                        List.of(),
                        EntityExtractionResult.emptyWithNote(""),
                        StructuredRewriteResult.identityDisabled("norm", ""),
                        ExpectedAnswerShape.UNKNOWN,
                        new AmbiguityAssessment(AmbiguityStatus.SUFFICIENT, List.of(), List.of()),
                        "corr",
                        "",
                        List.of());

        var decision = policy.resolve(p9Rag(), topicPlan);

        assertThat(decision.primaryRouteKind()).isEqualTo(AdaptiveRouteKind.RETRIEVAL_WORKFLOW_ROUTE);
        assertThat(decision.reasons()).anyMatch(r -> r.contains("function_calling_not_applicable"));
    }

    @Test
    void resolve_selectsFunctionCallingRoute_forApplicableCountQuery() {
        QueryPlan countPlan =
                new QueryPlan(
                        QueryPlan.VERSION_P6_QU_CORE_V1,
                        "¿Cuántas actas mencionan cámaras?",
                        "¿Cuántas actas mencionan cámaras?",
                        "¿Cuántas actas mencionan cámaras?",
                        "rw",
                        QueryType.COUNT_DOCUMENTS.name(),
                        Optional.of(QueryType.COUNT_DOCUMENTS),
                        ClassifierStatus.OK,
                        QueryIntent.COUNT,
                        Map.of(),
                        List.of(),
                        List.of(),
                        EntityExtractionResult.emptyWithNote(""),
                        StructuredRewriteResult.identityDisabled("norm", ""),
                        ExpectedAnswerShape.SCALAR_COUNT,
                        new AmbiguityAssessment(AmbiguityStatus.SUFFICIENT, List.of(), List.of()),
                        "corr",
                        "",
                        List.of());

        var decision = policy.resolve(p9Rag(), countPlan);

        assertThat(decision.primaryRouteKind()).isEqualTo(AdaptiveRouteKind.FUNCTION_CALLING_ROUTE);
    }

    private static RagConfig p9Rag() {
        return new RagConfig(
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                false,
                false,
                false,
                false,
                false,
                false,
                12,
                0.6,
                "llm",
                "emb",
                "cls",
                "SIMPLE",
                false,
                32_000,
                24_000,
                false,
                MaterializationStrategy.HYBRID);
    }

    private static QueryPlan minimalPlan() {
        return new QueryPlan(
                QueryPlan.VERSION_P6_QU_CORE_V1,
                "raw",
                "raw",
                "norm",
                "rw",
                "lbl",
                Optional.empty(),
                ClassifierStatus.OK,
                QueryIntent.COUNT,
                Map.of(),
                List.of(),
                List.of(),
                EntityExtractionResult.emptyWithNote(""),
                StructuredRewriteResult.identityDisabled("norm", ""),
                ExpectedAnswerShape.SCALAR_COUNT,
                new AmbiguityAssessment(AmbiguityStatus.SUFFICIENT, List.of(), List.of()),
                "corr",
                "",
                List.of());
    }
}
