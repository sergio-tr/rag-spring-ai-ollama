package com.uniovi.rag.application.service.runtime.advisor;

import com.uniovi.rag.domain.config.capability.CapabilitySet;
import com.uniovi.rag.domain.config.indexing.ReindexImpact;
import com.uniovi.rag.domain.config.prompt.SystemPromptLayers;
import com.uniovi.rag.domain.config.runtime.ConfigProvenance;
import com.uniovi.rag.domain.config.runtime.ResolvedRuntimeConfig;
import com.uniovi.rag.domain.config.validation.CompatibilityResult;
import com.uniovi.rag.domain.knowledge.MaterializationStrategy;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.domain.runtime.advisor.AdvisorDecision;
import com.uniovi.rag.domain.runtime.advisor.AdvisorSuppressionReason;
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
import com.uniovi.rag.domain.runtime.routing.AdaptiveRoutingOutcome;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AdvisorPolicyResolverTest {

    private final AdvisorPolicyResolver resolver = new AdvisorPolicyResolver();

    @Test
    void disabled_when_useAdvisor_false() {
        QueryPlan p = plan(AmbiguityStatus.SUFFICIENT);
        AdvisorDecision d = resolver.resolve(ctx(rag(false, true), p), p);
        assertFalse(d.selected());
        assertEquals(AdvisorSuppressionReason.DISABLED_BY_CONFIG, d.suppressionReason().orElseThrow());
    }

    @Test
    void suppressed_when_useRetrieval_false() {
        QueryPlan p = plan(AmbiguityStatus.SUFFICIENT);
        AdvisorDecision d = resolver.resolve(ctx(rag(true, false), p), p);
        assertFalse(d.selected());
        assertEquals(AdvisorSuppressionReason.WORKFLOW_NOT_SUPPORTED, d.suppressionReason().orElseThrow());
    }

    @Test
    void suppressed_when_ambiguity_insufficient() {
        QueryPlan p = plan(AmbiguityStatus.MISSING_INFORMATION);
        AdvisorDecision d = resolver.resolve(ctx(rag(true, true), p), p);
        assertFalse(d.selected());
        assertEquals(AdvisorSuppressionReason.SUPPRESSED_BY_AMBIGUITY, d.suppressionReason().orElseThrow());
    }

    @Test
    void selected_when_dense_and_gates_ok() {
        QueryPlan p = plan(AmbiguityStatus.SUFFICIENT);
        AdvisorDecision d = resolver.resolve(ctx(rag(true, true), p), p);
        assertTrue(d.selected());
        assertEquals(AdvisorDecision.EXECUTABLE_KINDS_5_2, d.executableKinds());
        assertTrue(d.suppressionReason().isEmpty());
    }

    private static RagConfig rag(boolean useAdvisor, boolean useRetrieval) {
        return new RagConfig(
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                useRetrieval,
                useAdvisor,
                false,
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

    private static ExecutionContext ctx(RagConfig rag, QueryPlan plan) {
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
                "q",
                RuntimeOperationKind.CHAT_MESSAGE,
                resolved,
                "sys",
                KnowledgeSnapshotSelection.empty(),
                Optional.empty(),
                Optional.empty(),
                "c",
                List.of("all"),
                Optional.empty(),
                Optional.of(plan),
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

    private static QueryPlan plan(AmbiguityStatus amb) {
        return new QueryPlan(
                QueryPlan.VERSION_P6_QU_CORE_V1,
                "raw",
                "raw",
                "norm",
                "rewritten",
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
                new AmbiguityAssessment(amb, List.of(), List.of()),
                "cid",
                "",
                List.of());
    }
}
