package com.uniovi.rag.application.service.runtime.functioncalling;

import com.uniovi.rag.domain.config.capability.CapabilitySet;
import com.uniovi.rag.domain.config.indexing.ReindexImpact;
import com.uniovi.rag.domain.config.prompt.SystemPromptLayers;
import com.uniovi.rag.domain.config.runtime.ConfigProvenance;
import com.uniovi.rag.domain.config.runtime.ResolvedRuntimeConfig;
import com.uniovi.rag.domain.config.validation.CompatibilityResult;
import com.uniovi.rag.domain.knowledge.MaterializationStrategy;
import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.engine.KnowledgeSnapshotSelection;
import com.uniovi.rag.domain.runtime.engine.RuntimeOperationKind;
import com.uniovi.rag.domain.runtime.memory.ConversationMemoryOutcome;
import com.uniovi.rag.domain.runtime.query.*;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolKind;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DefaultFunctionCallingPolicyResolverTest {

    private final DefaultFunctionCallingPolicyResolver resolver = new DefaultFunctionCallingPolicyResolver();

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
                "t",
                List.of("all"),
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
                com.uniovi.rag.domain.runtime.routing.AdaptiveRoutingOutcome.DISABLED_BY_CONFIG,
                com.uniovi.rag.domain.runtime.routing.AdaptiveRouteKind.DIRECT_WORKFLOW_ROUTE,
                false,
                Optional.empty(),
                false,
                List.of());
    }

    private static RagConfig fcEnabled() {
        return new RagConfig(
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                true,
                false,
                false,
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

    private static QueryPlan countPlan() {
        EntityExtractionResult entities =
                new EntityExtractionResult(
                        List.of(), List.of(), List.of(), List.of(), List.of(),
                        Optional.empty(), Optional.empty(), Optional.empty(), List.of());
        StructuredRewriteResult rewrite =
                new StructuredRewriteResult(
                        "rewritten",
                        true,
                        List.of("OK"),
                        StructuredRewriteResult.STRATEGY_STRUCTURED_V1,
                        List.of(),
                        List.of(),
                        Optional.empty(),
                        Map.of(),
                        List.of());
        return new QueryPlan(
                QueryPlan.VERSION_P6_QU_CORE_V1,
                "raw",
                "raw",
                "raw",
                "rewritten",
                "L",
                Optional.empty(),
                ClassifierStatus.OK,
                QueryIntent.COUNT,
                Map.of(),
                List.of(),
                List.of(),
                entities,
                rewrite,
                ExpectedAnswerShape.SCALAR_COUNT,
                AmbiguityAssessment.sufficient(),
                "c",
                "m",
                List.of());
    }

    private static QueryPlan noToolPlan() {
        EntityExtractionResult entities =
                new EntityExtractionResult(
                        List.of(), List.of(), List.of(), List.of(), List.of(),
                        Optional.empty(), Optional.empty(), Optional.empty(), List.of());
        StructuredRewriteResult rewrite =
                new StructuredRewriteResult(
                        "rewritten",
                        true,
                        List.of("OK"),
                        StructuredRewriteResult.STRATEGY_STRUCTURED_V1,
                        List.of(),
                        List.of(),
                        Optional.empty(),
                        Map.of(),
                        List.of());
        return new QueryPlan(
                QueryPlan.VERSION_P6_QU_CORE_V1,
                "raw",
                "raw",
                "raw",
                "rewritten",
                "L",
                Optional.empty(),
                ClassifierStatus.OK,
                QueryIntent.UNKNOWN,
                Map.of(),
                List.of(),
                List.of(),
                entities,
                rewrite,
                ExpectedAnswerShape.UNKNOWN,
                AmbiguityAssessment.sufficient(),
                "c",
                "m",
                List.of());
    }

    @Test
    void exposesCountWhenIntentCount() {
        var d = resolver.resolve(ctx(fcEnabled()), countPlan());
        assertTrue(d.isPresent());
        assertTrue(d.get().exposedToolKinds().contains(DeterministicToolKind.COUNT_DOCUMENTS_TOOL));
    }

    @Test
    void emptyWhenNoToolMatches() {
        assertTrue(resolver.resolve(ctx(fcEnabled()), noToolPlan()).isEmpty());
    }

    @Test
    void countAndExplain_whenClassifierSaysSo() {
        EntityExtractionResult entities =
                new EntityExtractionResult(
                        List.of(), List.of(), List.of(), List.of(), List.of(),
                        Optional.empty(), Optional.empty(), Optional.empty(), List.of());
        StructuredRewriteResult rewrite =
                new StructuredRewriteResult(
                        "rewritten",
                        true,
                        List.of("OK"),
                        StructuredRewriteResult.STRATEGY_STRUCTURED_V1,
                        List.of(),
                        List.of(),
                        Optional.empty(),
                        Map.of(),
                        List.of());
        QueryPlan plan =
                new QueryPlan(
                QueryPlan.VERSION_P6_QU_CORE_V1,
                "raw",
                "raw",
                "raw",
                "rewritten",
                "L",
                Optional.of(QueryType.COUNT_AND_EXPLAIN),
                ClassifierStatus.OK,
                QueryIntent.UNKNOWN,
                Map.of(),
                List.of(),
                List.of(),
                entities,
                rewrite,
                ExpectedAnswerShape.UNKNOWN,
                AmbiguityAssessment.sufficient(),
                "c",
                "m",
                List.of());
        var d = resolver.resolve(ctx(fcEnabled()), plan);
        assertTrue(d.isPresent());
        assertTrue(d.get().exposedToolKinds().contains(DeterministicToolKind.COUNT_AND_EXPLAIN_TOOL));
    }
}
