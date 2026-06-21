package com.uniovi.rag.application.service.runtime.tool;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.application.service.evaluation.metrics.GoldSubsetManifest;
import com.uniovi.rag.application.service.evaluation.metrics.GoldSubsetManifestLoader;
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
import com.uniovi.rag.domain.runtime.tool.DeterministicToolOutcome;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;

class GoldSubsetDeterministicToolResolverTest {

    private DefaultDeterministicToolResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new DefaultDeterministicToolResolver();
        DeterministicToolBenchmarkContext.clearRunScope();
    }

    @AfterEach
    void tearDown() {
        DeterministicToolBenchmarkContext.clearRunScope();
    }

    @Test
    void applicableGoldSubsetItems_selectToolUnderWeakLiveSignals() throws Exception {
        GoldSubsetManifest manifest =
                new ObjectMapper()
                        .readValue(
                                getClass()
                                        .getResourceAsStream("/evaluation/gold-subset-v1.json"),
                                GoldSubsetManifest.class);
        RagConfig rag = baseRag(true);
        int applicable = 0;
        int selected = 0;
        for (GoldSubsetManifest.Entry entry : manifest.entries()) {
            if (!DeterministicToolApplicability.isApplicableQueryType(parseType(entry.queryTypeExpected()))) {
                continue;
            }
            applicable++;
            QueryPlan plan = weakLivePlanForGoldEntry(entry);
            var decision = resolver.resolve(ctx(rag), plan);
            if (decision.selected()) {
                selected++;
            }
        }
        assertThat(applicable).isGreaterThanOrEqualTo(12);
        assertThat(selected * 100.0 / applicable).isGreaterThanOrEqualTo(80.0);
    }

    @Test
    void applicableGoldSubsetItems_selectToolUnderLowConfidence() throws Exception {
        GoldSubsetManifest manifest =
                new ObjectMapper()
                        .readValue(
                                getClass()
                                        .getResourceAsStream("/evaluation/gold-subset-v1.json"),
                                GoldSubsetManifest.class);
        RagConfig rag = baseRag(true);
        int applicable = 0;
        int selected = 0;
        for (GoldSubsetManifest.Entry entry : manifest.entries()) {
            if (!DeterministicToolApplicability.isApplicableQueryType(parseType(entry.queryTypeExpected()))) {
                continue;
            }
            applicable++;
            QueryPlan plan = planForGoldEntry(entry);
            var decision = resolver.resolve(ctx(rag), plan);
            if (decision.selected()) {
                selected++;
            }
        }
        assertThat(applicable).isGreaterThanOrEqualTo(12);
        assertThat(selected * 100.0 / applicable).isGreaterThanOrEqualTo(80.0);
    }

    private static QueryType parseType(String raw) {
        return QueryType.valueOf(raw.trim());
    }

    private static QueryPlan weakLivePlanForGoldEntry(GoldSubsetManifest.Entry entry) {
        return new QueryPlan(
                QueryPlan.VERSION_P12_MEMORY_CONVERSATIONAL_FLOW_V1,
                entry.question(),
                entry.question(),
                entry.question(),
                entry.question(),
                "UNCLASSIFIED",
                Optional.empty(),
                ClassifierStatus.LOW_CONFIDENCE,
                QueryIntent.UNKNOWN,
                Map.of(),
                List.of(),
                List.of(),
                EntityExtractionResult.emptyWithNote(""),
                StructuredRewriteResult.identityFallback(entry.question(), "simulated-live"),
                ExpectedAnswerShape.UNKNOWN,
                AmbiguityAssessment.sufficient(),
                "cid-" + entry.datasetQuestionId(),
                "",
                List.of());
    }

    private static QueryPlan planForGoldEntry(GoldSubsetManifest.Entry entry) {
        QueryType expected = parseType(entry.queryTypeExpected());
        QueryIntent intent = intentFor(expected, entry.question());
        ExpectedAnswerShape shape = shapeFor(expected);
        Map<String, String> slots = new LinkedHashMap<>();
        if (expected == QueryType.COUNT_AND_EXPLAIN) {
            slots.put("explain", "true");
        }
        return new QueryPlan(
                QueryPlan.VERSION_P6_QU_CORE_V1,
                entry.question(),
                entry.question(),
                entry.question(),
                entry.question(),
                "UNCLASSIFIED",
                Optional.empty(),
                ClassifierStatus.LOW_CONFIDENCE,
                intent,
                slots,
                List.of(),
                List.of(),
                EntityExtractionResult.emptyWithNote(""),
                StructuredRewriteResult.identityDisabled(entry.question(), ""),
                shape,
                AmbiguityAssessment.sufficient(),
                "cid-" + entry.datasetQuestionId(),
                "",
                List.of());
    }

    private static QueryIntent intentFor(QueryType type, String question) {
        return switch (type) {
            case COUNT_DOCUMENTS -> QueryIntent.COUNT;
            case BOOLEAN_QUERY -> QueryIntent.BOOLEAN_CHECK;
            case FIND_PARAGRAPH -> QueryIntent.FIND;
            case COUNT_AND_EXPLAIN -> QueryIntent.COUNT;
            case GET_DURATION -> QueryIntent.FIND;
            case FILTER_AND_LIST -> QueryIntent.LIST;
            default -> QueryIntent.UNKNOWN;
        };
    }

    private static ExpectedAnswerShape shapeFor(QueryType type) {
        return switch (type) {
            case COUNT_DOCUMENTS -> ExpectedAnswerShape.SCALAR_COUNT;
            case BOOLEAN_QUERY -> ExpectedAnswerShape.SCALAR_BOOLEAN;
            case FIND_PARAGRAPH, GET_DURATION, COUNT_AND_EXPLAIN -> ExpectedAnswerShape.PARAGRAPH;
            case FILTER_AND_LIST -> ExpectedAnswerShape.LIST;
            default -> ExpectedAnswerShape.UNKNOWN;
        };
    }

    private static RagConfig baseRag(boolean toolsEnabled) {
        return new RagConfig(
                false,
                false,
                toolsEnabled,
                false,
                false,
                false,
                false,
                false,
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
                RuntimeOperationKind.LAB_PROCESS,
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
}
