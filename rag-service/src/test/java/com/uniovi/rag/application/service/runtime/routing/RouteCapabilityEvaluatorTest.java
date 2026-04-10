package com.uniovi.rag.application.service.runtime.routing;

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
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class RouteCapabilityEvaluatorTest {

    private final RouteCapabilityEvaluator evaluator = new RouteCapabilityEvaluator();

    @Test
    void evaluate_requiresAmbiguitySufficient_for_nonWorkflow_routes() {
        RagConfig rag =
                new RagConfig(
                        false, false, true, false, false, false, false,
                        true,
                        true,
                        true,
                        false,
                        false,
                        true,
                        5, 0.2, "l", "e", "c", "r",
                        false,
                        RagConfig.DEFAULT_NAIVE_FULL_CORPUS_MAX_CHARS,
                        RagConfig.DEFAULT_ADVANCED_RETRIEVAL_MAX_CONTEXT_CHARS,
                        MaterializationStrategy.CHUNK_LEVEL);

        QueryPlan insufficient = plan(AmbiguityStatus.MISSING_INFORMATION);
        RouteCapabilityEvaluator.RouteCapabilities caps = evaluator.evaluate(rag, insufficient);
        assertFalse(caps.ambiguitySufficient());
        assertFalse(caps.deterministicToolsEligible());
        assertFalse(caps.functionCallingEligible());
        assertFalse(caps.advisorEligible());
        assertTrue(caps.directWorkflowValid());
        assertTrue(caps.retrievalWorkflowValid());
    }

    @Test
    void evaluate_advisorEligible_requiresUseAdvisor_andUseRetrieval() {
        QueryPlan sufficient = plan(AmbiguityStatus.SUFFICIENT);

        RagConfig noRetrieval =
                new RagConfig(
                        false, false, false, false, false, false, false,
                        false,
                        false,
                        true,
                        false,
                        false,
                        true,
                        5, 0.2, "l", "e", "c", "r",
                        false,
                        RagConfig.DEFAULT_NAIVE_FULL_CORPUS_MAX_CHARS,
                        RagConfig.DEFAULT_ADVANCED_RETRIEVAL_MAX_CONTEXT_CHARS,
                        MaterializationStrategy.CHUNK_LEVEL);
        assertFalse(evaluator.evaluate(noRetrieval, sufficient).advisorEligible());

        RagConfig ok =
                new RagConfig(
                        false, false, false, false, false, false, false,
                        false,
                        true,
                        true,
                        false,
                        false,
                        true,
                        5, 0.2, "l", "e", "c", "r",
                        false,
                        RagConfig.DEFAULT_NAIVE_FULL_CORPUS_MAX_CHARS,
                        RagConfig.DEFAULT_ADVANCED_RETRIEVAL_MAX_CONTEXT_CHARS,
                        MaterializationStrategy.CHUNK_LEVEL);
        assertTrue(evaluator.evaluate(ok, sufficient).advisorEligible());
    }

    private static QueryPlan plan(AmbiguityStatus status) {
        return new QueryPlan(
                QueryPlan.VERSION_P6_QU_CORE_V1,
                "raw",
                "raw",
                "norm",
                "rw",
                "lbl",
                Optional.empty(),
                ClassifierStatus.OK,
                QueryIntent.UNKNOWN,
                Map.of(),
                List.of(),
                List.of(),
                EntityExtractionResult.emptyWithNote(""),
                StructuredRewriteResult.identityDisabled("norm", ""),
                ExpectedAnswerShape.UNKNOWN,
                new AmbiguityAssessment(status, List.of(), List.of()),
                "corr",
                "",
                List.of());
    }
}

