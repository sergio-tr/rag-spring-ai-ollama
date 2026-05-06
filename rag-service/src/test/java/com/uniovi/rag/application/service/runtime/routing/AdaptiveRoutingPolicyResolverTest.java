package com.uniovi.rag.application.service.runtime.routing;

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
import com.uniovi.rag.domain.runtime.query.AmbiguityAssessment;
import com.uniovi.rag.domain.runtime.query.AmbiguityStatus;
import com.uniovi.rag.domain.runtime.query.ClassifierStatus;
import com.uniovi.rag.domain.runtime.query.EntityExtractionResult;
import com.uniovi.rag.domain.runtime.query.ExpectedAnswerShape;
import com.uniovi.rag.domain.runtime.query.QueryIntent;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.query.StructuredRewriteResult;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRouteKind;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRoutingDecision;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRoutingMode;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRoutingOutcome;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AdaptiveRoutingPolicyResolverTest {

    private final AdaptiveRoutingPolicyResolver resolver =
            new AdaptiveRoutingPolicyResolver(new RouteCapabilityEvaluator());

    @Test
    void resolve_disabledByConfig_setsModeDisabled_andCompatibilityWorkflow() {
        RagConfig rag = rag(false, true, true, true, true);
        ExecutionContext ctx = ctx(rag);
        AdaptiveRoutingDecision d = resolver.resolve(ctx, plan(AmbiguityStatus.SUFFICIENT));
        assertEquals(AdaptiveRoutingMode.DISABLED, d.mode());
        assertEquals(AdaptiveRouteKind.RETRIEVAL_WORKFLOW_ROUTE, d.primaryRouteKind());
        assertTrue(d.fallbackWorkflowRouteKind().isEmpty());
    }

    @Test
    void resolve_whenAmbiguityInsufficient_forcesWorkflowRoute() {
        RagConfig rag = rag(true, false, true, true, false);
        ExecutionContext ctx = ctx(rag);
        AdaptiveRoutingDecision d = resolver.resolve(ctx, plan(AmbiguityStatus.MISSING_INFORMATION));
        assertEquals(AdaptiveRoutingMode.ENABLED, d.mode());
        assertEquals(AdaptiveRouteKind.DIRECT_WORKFLOW_ROUTE, d.primaryRouteKind());
        assertTrue(d.fallbackWorkflowRouteKind().isEmpty());
    }

    @Test
    void resolve_selectsDeterministicToolRoute_thenWorkflowFallback() {
        RagConfig rag = rag(true, true, true, false, false);
        ExecutionContext ctx = ctx(rag);
        AdaptiveRoutingDecision d = resolver.resolve(ctx, plan(AmbiguityStatus.SUFFICIENT));
        assertEquals(AdaptiveRouteKind.DETERMINISTIC_TOOL_ROUTE, d.primaryRouteKind());
        assertEquals(Optional.of(AdaptiveRouteKind.RETRIEVAL_WORKFLOW_ROUTE), d.fallbackWorkflowRouteKind());
    }

    private static RagConfig rag(
            boolean adaptiveRoutingEnabled,
            boolean useRetrieval,
            boolean toolsEnabled,
            boolean functionCallingEnabled,
            boolean useAdvisor) {
        return new RagConfig(
                false,
                false,
                toolsEnabled,
                false,
                false,
                false,
                false,
                functionCallingEnabled,
                useRetrieval,
                useAdvisor,
                false,
                false,
                adaptiveRoutingEnabled,
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

    private static ExecutionContext ctx(RagConfig rag) {
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
                "q",
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
                "q",
                "q",
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

