package com.uniovi.rag.application.service.runtime.routing;

import static org.assertj.core.api.Assertions.assertThat;

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
