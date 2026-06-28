package com.uniovi.rag.application.service.runtime.routing;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniovi.rag.domain.knowledge.MaterializationStrategy;
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

class AdvisorRoutingPolicyTest {

    private final AdvisorRoutingPolicy policy = new AdvisorRoutingPolicy();

    @Test
    void resolve_selectsAdvisorRoute_whenEnabledWithoutAdaptiveDeterministicOrFunctionCalling() {
        var decision = policy.resolve(p10Rag(), minimalPlan());

        assertThat(decision.primaryRouteKind()).isEqualTo(AdaptiveRouteKind.ADVISOR_ROUTE);
        assertThat(decision.fallbackWorkflowRouteKind())
                .contains(AdaptiveRouteKind.RETRIEVAL_WORKFLOW_ROUTE);
    }

    @Test
    void resolve_disabled_whenFunctionCallingEnabled() {
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
                        true,
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

        var decision = policy.resolve(rag, minimalPlan());

        assertThat(decision.primaryRouteKind()).isEqualTo(AdaptiveRouteKind.RETRIEVAL_WORKFLOW_ROUTE);
        assertThat(decision.reasons()).contains("functionCallingEnabled=true");
    }

    @Test
    void resolve_disabled_whenDeterministicToolRoutingEnabled() {
        RagConfig rag =
                new RagConfig(
                        true,
                        true,
                        true,
                        true,
                        true,
                        true,
                        true,
                        false,
                        true,
                        true,
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

        assertThat(decision.primaryRouteKind()).isEqualTo(AdaptiveRouteKind.RETRIEVAL_WORKFLOW_ROUTE);
        assertThat(decision.reasons()).contains("deterministicToolRoutingEnabled=true");
    }

    private static RagConfig p10Rag() {
        return new RagConfig(
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                false,
                true,
                true,
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
