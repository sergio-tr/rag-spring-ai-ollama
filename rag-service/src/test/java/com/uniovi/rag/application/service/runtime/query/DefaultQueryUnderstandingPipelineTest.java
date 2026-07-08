package com.uniovi.rag.application.service.runtime.query;

import com.uniovi.rag.testsupport.config.TestConfigurablePromptResolver;
import com.uniovi.rag.testsupport.llm.ChatGenerationModelSelectorTestSupport;
import com.uniovi.rag.configuration.RagClassifierProperties;
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
import com.uniovi.rag.domain.runtime.query.ClassifierStatus;
import com.uniovi.rag.domain.runtime.query.EntityExtractionResult;
import com.uniovi.rag.domain.runtime.query.ExpectedAnswerShape;
import com.uniovi.rag.domain.runtime.query.NormalizedQuery;
import com.uniovi.rag.domain.runtime.query.QueryIntent;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.query.StructuredRewriteResult;
import com.uniovi.rag.infrastructure.classifier.ClassifierInferenceResponse;
import com.uniovi.rag.infrastructure.classifier.QueryClassifier;
import com.uniovi.rag.application.service.runtime.query.analyser.QueryAnalyser;
import com.uniovi.rag.application.service.runtime.query.expand.QueryExpander;
import com.uniovi.rag.infrastructure.observability.RuntimeObservability;
import io.micrometer.tracing.Tracer;
import org.springframework.beans.factory.ObjectProvider;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import com.uniovi.rag.application.service.llm.ProviderAwareSecondaryLlmExecutor;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class DefaultQueryUnderstandingPipelineTest {

    private static QueryExpansionStage noOpExpansionStage() {
        QueryExpander expander = mock(QueryExpander.class);
        when(expander.expand(anyString())).thenAnswer(inv -> inv.getArgument(0));
        return new QueryExpansionStage(expander);
    }

    private static RagConfig rag(boolean nerEnabled, boolean toolsEnabled) {
        return new RagConfig(
                false,
                nerEnabled,
                toolsEnabled,
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
                5,
                0.2,
                "llm",
                "emb",
                "cls",
                "reason",
                false,
                RagConfig.DEFAULT_NAIVE_FULL_CORPUS_MAX_CHARS,
                RagConfig.DEFAULT_ADVANCED_RETRIEVAL_MAX_CONTEXT_CHARS,
                MaterializationStrategy.CHUNK_LEVEL);
    }

    private static ExecutionContext ctx(RagConfig rag, String q) {
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
                q,
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
                q,
                q,
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
                Optional.empty());
    }

    private static ExecutionContext ctxPlanning(RagConfig rag, String rawUserQuery, String effectivePlanningInput) {
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
                rawUserQuery,
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
                "",
                effectivePlanningInput,
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
                Optional.empty());
    }

    @Test
    void buildPlan_alwaysProducesValidQueryPlan_andFrozenStageOrder() {
        RagConfig rag = rag(true, true);

        QueryClassifier classifier = mock(QueryClassifier.class);
        when(classifier.classifyInference(anyString(), eq("cls")))
                .thenReturn(new ClassifierInferenceResponse("FILTER_AND_LIST", 0.9, null, List.of()));

        QueryAnalyser analyser = mock(QueryAnalyser.class);
        when(analyser.analyse(anyString())).thenReturn(new JSONObject("{\"date\":[],\"place\":[],\"attendees\":[],\"topics\":[],\"mentionedEntities\":[],\"answerType\":\"unknown\",\"comparisonType\":\"none\",\"temporalContext\":\"none\"}"));

        ProviderAwareSecondaryLlmExecutor secondaryLlmExecutor = mock(ProviderAwareSecondaryLlmExecutor.class);
        String rewriteJson = """
                {
                  "rewrittenQueryText": "list all items",
                  "targetEntities": [],
                  "targetAttributes": [],
                  "targetAction": "LIST",
                  "slotFilling": {},
                  "constraints": []
                }
                """;
        when(secondaryLlmExecutor.complete(
                        any(ExecutionContext.class),
                        eq("query-rewrite"),
                        anyString(),
                        anyString(),
                        eq(ProviderAwareSecondaryLlmExecutor.SECONDARY_TASK_DEFAULT_TEMPERATURE)))
                .thenReturn(rewriteJson);

        DefaultQueryClassifierAdapter classifierAdapter = classifierAdapter(classifier);
        DefaultNamedEntityExtractionAdapter nerAdapter = new DefaultNamedEntityExtractionAdapter(analyser);
        DefaultStructuredQueryRewriter rewriter = new DefaultStructuredQueryRewriter(secondaryLlmExecutor, TestConfigurablePromptResolver.defaultsOnly());
        DefaultQueryIntentResolver intentResolver = new DefaultQueryIntentResolver();
        DefaultExpectedAnswerShapeResolver shapeResolver = new DefaultExpectedAnswerShapeResolver();
        DefaultAmbiguityAssessmentService ambiguity = new DefaultAmbiguityAssessmentService();

        DefaultQueryUnderstandingPipeline pipeline =
                new DefaultQueryUnderstandingPipeline(
                        classifierAdapter, nerAdapter, rewriter, intentResolver, shapeResolver, ambiguity, noOpExpansionStage());

        QueryPlan plan = pipeline.buildPlan(ctx(rag, "  list   all  items "));

        assertEquals(QueryPlan.VERSION_P12_MEMORY_CONVERSATIONAL_FLOW_V1, plan.queryPlanVersion());
        assertEquals("  list   all  items ", plan.rawUserQuery());
        assertEquals("list all items", plan.normalizedQueryText());
        assertEquals("list all items", plan.rewrittenQueryText());
        assertTrue(plan.queryIntent() != null);
        assertTrue(plan.pipelineNotes().size() >= 8);

        // Frozen stage ordering: first eight notes correspond to the QU stages in-order.
        assertTrue(plan.pipelineNotes().get(0).startsWith("qu_normalize "));
        assertTrue(plan.pipelineNotes().get(1).startsWith("qu_expand "));
        assertTrue(plan.pipelineNotes().get(2).startsWith("qu_classify "));
        assertTrue(plan.pipelineNotes().get(2).contains("classifierModelId=cls"));
        verify(classifier).classifyInference("list all items", "cls");
        assertTrue(plan.pipelineNotes().get(3).startsWith("qu_extract_entities "));
        assertTrue(plan.pipelineNotes().get(4).startsWith("qu_rewrite "));
        assertTrue(plan.pipelineNotes().get(5).startsWith("qu_resolve_intent "));
        assertTrue(plan.pipelineNotes().get(6).startsWith("qu_resolve_answer_shape "));
        assertTrue(plan.pipelineNotes().get(7).startsWith("qu_assess_ambiguity "));
    }

    @Test
    void classifierDisabled_whenToolsDisabled() {
        RagConfig rag = rag(false, false);

        QueryClassifier classifier = mock(QueryClassifier.class);
        QueryClassifierAdapter adapter = classifierAdapter(classifier);

        var outcome = adapter.classify(ctx(rag, "q"), "q");
        assertEquals("UNCLASSIFIED", outcome.classifierLabel());
        assertTrue(outcome.classifierQueryType().isEmpty());
        assertEquals(ClassifierStatus.DISABLED, outcome.classifierStatus());
        verifyNoInteractions(classifier);
    }

    @Test
    void buildPlan_blankEffectiveInputFallsBackToRaw_andRecordsWhitespaceNotes() {
        RagConfig cfg = rag(true, true);
        QueryClassifierAdapter classifierAdapter = mock(QueryClassifierAdapter.class);
        when(classifierAdapter.classify(any(), anyString()))
                .thenReturn(
                        new QueryClassifierAdapter.ClassifierOutcome(
                                "FILTER_AND_LIST",
                                Optional.of(QueryType.FILTER_AND_LIST),
                                ClassifierStatus.OK,
                                "cls",
                                "OK"));

        NamedEntityExtractionAdapter ner = mock(NamedEntityExtractionAdapter.class);
        when(ner.extract(any(), anyString())).thenReturn(EntityExtractionResult.emptyWithNote(""));

        StructuredQueryRewriter rewriter = mock(StructuredQueryRewriter.class);
        when(rewriter.rewrite(any(), any(), anyString(), any(), any(), any()))
                .thenAnswer(
                        inv ->
                                StructuredRewriteResult.identityDisabled(
                                        ((NormalizedQuery) inv.getArgument(1)).normalizedText(), null));

        QueryIntentResolver intentResolver = mock(QueryIntentResolver.class);
        when(intentResolver.resolve(any(), any(), anyString(), any(), any(), any()))
                .thenReturn(QueryIntent.LIST);

        ExpectedAnswerShapeResolver shapeResolver = mock(ExpectedAnswerShapeResolver.class);
        when(shapeResolver.resolve(any(), any())).thenReturn(ExpectedAnswerShape.LIST);

        AmbiguityAssessmentService ambiguityAssessmentService = mock(AmbiguityAssessmentService.class);
        when(ambiguityAssessmentService.assess(any(), any(), anyString(), any(), any(), any()))
                .thenReturn(AmbiguityAssessment.sufficient());

        DefaultQueryUnderstandingPipeline pipeline =
                new DefaultQueryUnderstandingPipeline(
                        classifierAdapter,
                        ner,
                        rewriter,
                        intentResolver,
                        shapeResolver,
                        ambiguityAssessmentService,
                        noOpExpansionStage());

        ExecutionContext executionContext = ctxPlanning(cfg, null, "   ");
        QueryPlan plan = pipeline.buildPlan(executionContext);

        assertEquals("", plan.rawUserQuery());
        assertEquals("", plan.normalizedQueryText());
        assertTrue(plan.pipelineNotes().getFirst().contains("blank_query"));

        executionContext = ctxPlanning(cfg, "unused", "  hello   world  ");
        plan = pipeline.buildPlan(executionContext);
        assertEquals("unused", plan.rawUserQuery());
        assertEquals("hello world", plan.normalizedQueryText());
        assertTrue(String.join(" ", plan.pipelineNotes()).contains("whitespace_normalized"));
    }

    @Test
    void buildPlan_stageStatuses_reflectClassifierNerAndRewriteOutcomes() {
        RagConfig cfg = rag(true, true);

        QueryClassifierAdapter classifierAdapter = mock(QueryClassifierAdapter.class);
        when(classifierAdapter.classify(any(), anyString()))
                .thenReturn(
                        new QueryClassifierAdapter.ClassifierOutcome(
                                "UNCLASSIFIED",
                                Optional.empty(),
                                ClassifierStatus.INVALID_OUTPUT,
                                "mid",
                                "bad"));

        NamedEntityExtractionAdapter ner = mock(NamedEntityExtractionAdapter.class);
        when(ner.extract(any(), anyString()))
                .thenReturn(EntityExtractionResult.emptyWithNote("FALLBACK: ner"));

        StructuredQueryRewriter rewriter = mock(StructuredQueryRewriter.class);
        when(rewriter.rewrite(any(), any(), anyString(), any(), any(), any()))
                .thenReturn(StructuredRewriteResult.identityFallback("norm", "bad json"));

        QueryIntentResolver intentResolver = mock(QueryIntentResolver.class);
        when(intentResolver.resolve(any(), any(), anyString(), any(), any(), any()))
                .thenReturn(QueryIntent.UNKNOWN);

        ExpectedAnswerShapeResolver shapeResolver = mock(ExpectedAnswerShapeResolver.class);
        when(shapeResolver.resolve(any(), any())).thenReturn(ExpectedAnswerShape.UNKNOWN);

        AmbiguityAssessmentService ambiguityAssessmentService = mock(AmbiguityAssessmentService.class);
        when(ambiguityAssessmentService.assess(any(), any(), anyString(), any(), any(), any()))
                .thenReturn(AmbiguityAssessment.sufficient());

        DefaultQueryUnderstandingPipeline pipeline =
                new DefaultQueryUnderstandingPipeline(
                        classifierAdapter,
                        ner,
                        rewriter,
                        intentResolver,
                        shapeResolver,
                        ambiguityAssessmentService,
                        noOpExpansionStage());

        QueryPlan plan = pipeline.buildPlan(ctx(cfg, "q"));

        assertTrue(plan.pipelineNotes().get(2).contains("qu_classify"));
        assertTrue(plan.pipelineNotes().get(2).contains("qu_status=FALLBACK"));

        assertTrue(plan.pipelineNotes().get(3).contains("qu_extract_entities"));
        assertTrue(plan.pipelineNotes().get(3).contains("qu_status=ERROR"));

        assertTrue(plan.pipelineNotes().get(4).contains("qu_rewrite"));
        assertTrue(plan.pipelineNotes().get(4).contains("qu_status=ERROR"));
    }

    @Test
    void buildPlan_toolsDisabled_marksRewriteDisabled_andSkipsRealRewriterIntegration() {
        RagConfig cfg = rag(true, false);

        QueryClassifierAdapter classifierAdapter = mock(QueryClassifierAdapter.class);
        when(classifierAdapter.classify(any(), anyString()))
                .thenReturn(
                        new QueryClassifierAdapter.ClassifierOutcome(
                                "UNCLASSIFIED",
                                Optional.empty(),
                                ClassifierStatus.UNAVAILABLE,
                                "mid",
                                "down"));

        NamedEntityExtractionAdapter ner = mock(NamedEntityExtractionAdapter.class);
        when(ner.extract(any(), anyString())).thenReturn(EntityExtractionResult.emptyWithNote(""));

        StructuredQueryRewriter rewriter = mock(StructuredQueryRewriter.class);
        when(rewriter.rewrite(any(), any(), anyString(), any(), any(), any()))
                .thenReturn(StructuredRewriteResult.identityDisabled("norm", "tools off"));

        QueryIntentResolver intentResolver = mock(QueryIntentResolver.class);
        when(intentResolver.resolve(any(), any(), anyString(), any(), any(), any()))
                .thenReturn(QueryIntent.UNKNOWN);

        ExpectedAnswerShapeResolver shapeResolver = mock(ExpectedAnswerShapeResolver.class);
        when(shapeResolver.resolve(any(), any())).thenReturn(ExpectedAnswerShape.UNKNOWN);

        AmbiguityAssessmentService ambiguityAssessmentService = mock(AmbiguityAssessmentService.class);
        when(ambiguityAssessmentService.assess(any(), any(), anyString(), any(), any(), any()))
                .thenReturn(AmbiguityAssessment.sufficient());

        DefaultQueryUnderstandingPipeline pipeline =
                new DefaultQueryUnderstandingPipeline(
                        classifierAdapter,
                        ner,
                        rewriter,
                        intentResolver,
                        shapeResolver,
                        ambiguityAssessmentService,
                        noOpExpansionStage());

        QueryPlan plan = pipeline.buildPlan(ctx(cfg, "x"));
        assertTrue(plan.pipelineNotes().get(4).contains("qu_status=DISABLED"));
        assertTrue(plan.pipelineNotes().get(2).contains("qu_status=ERROR"));
    }

    @Test
    void buildPlan_rewriteNotesDisabled_branch_whenToolsEnabled() {
        RagConfig cfg = rag(true, true);

        QueryClassifierAdapter classifierAdapter = mock(QueryClassifierAdapter.class);
        when(classifierAdapter.classify(any(), anyString()))
                .thenReturn(
                        new QueryClassifierAdapter.ClassifierOutcome(
                                "OK",
                                Optional.of(QueryType.FILTER_AND_LIST),
                                ClassifierStatus.OK,
                                "m",
                                "OK"));

        NamedEntityExtractionAdapter ner = mock(NamedEntityExtractionAdapter.class);
        when(ner.extract(any(), anyString())).thenReturn(EntityExtractionResult.emptyWithNote(""));

        StructuredQueryRewriter rewriter = mock(StructuredQueryRewriter.class);
        when(rewriter.rewrite(any(), any(), anyString(), any(), any(), any()))
                .thenReturn(StructuredRewriteResult.identityDisabled("norm", "skipped"));

        QueryIntentResolver intentResolver = mock(QueryIntentResolver.class);
        when(intentResolver.resolve(any(), any(), anyString(), any(), any(), any()))
                .thenReturn(QueryIntent.LIST);

        ExpectedAnswerShapeResolver shapeResolver = mock(ExpectedAnswerShapeResolver.class);
        when(shapeResolver.resolve(any(), any())).thenReturn(ExpectedAnswerShape.LIST);

        AmbiguityAssessmentService ambiguityAssessmentService = mock(AmbiguityAssessmentService.class);
        when(ambiguityAssessmentService.assess(any(), any(), anyString(), any(), any(), any()))
                .thenReturn(AmbiguityAssessment.sufficient());

        DefaultQueryUnderstandingPipeline pipeline =
                new DefaultQueryUnderstandingPipeline(
                        classifierAdapter,
                        ner,
                        rewriter,
                        intentResolver,
                        shapeResolver,
                        ambiguityAssessmentService,
                        noOpExpansionStage());

        QueryPlan plan = pipeline.buildPlan(ctx(cfg, "list"));
        assertTrue(plan.pipelineNotes().get(4).contains("qu_status=DISABLED"));
    }

    @Test
    void buildPlan_nerDisabled_marksEntityStageDisabled() {
        RagConfig cfg = rag(false, true);

        QueryClassifierAdapter classifierAdapter = mock(QueryClassifierAdapter.class);
        when(classifierAdapter.classify(any(), anyString()))
                .thenReturn(
                        new QueryClassifierAdapter.ClassifierOutcome(
                                "OK",
                                Optional.of(QueryType.FILTER_AND_LIST),
                                ClassifierStatus.OK,
                                "m",
                                "OK"));

        NamedEntityExtractionAdapter ner = mock(NamedEntityExtractionAdapter.class);
        when(ner.extract(any(), anyString())).thenReturn(EntityExtractionResult.emptyWithNote("note"));

        StructuredQueryRewriter rewriter = mock(StructuredQueryRewriter.class);
        when(rewriter.rewrite(any(), any(), anyString(), any(), any(), any()))
                .thenReturn(StructuredRewriteResult.identityDisabled("norm", null));

        QueryIntentResolver intentResolver = mock(QueryIntentResolver.class);
        when(intentResolver.resolve(any(), any(), anyString(), any(), any(), any()))
                .thenReturn(QueryIntent.LIST);

        ExpectedAnswerShapeResolver shapeResolver = mock(ExpectedAnswerShapeResolver.class);
        when(shapeResolver.resolve(any(), any())).thenReturn(ExpectedAnswerShape.LIST);

        AmbiguityAssessmentService ambiguityAssessmentService = mock(AmbiguityAssessmentService.class);
        when(ambiguityAssessmentService.assess(any(), any(), anyString(), any(), any(), any()))
                .thenReturn(AmbiguityAssessment.sufficient());

        DefaultQueryUnderstandingPipeline pipeline =
                new DefaultQueryUnderstandingPipeline(
                        classifierAdapter,
                        ner,
                        rewriter,
                        intentResolver,
                        shapeResolver,
                        ambiguityAssessmentService,
                        noOpExpansionStage());

        QueryPlan plan = pipeline.buildPlan(ctx(cfg, "q"));
        assertTrue(plan.pipelineNotes().get(3).contains("qu_status=DISABLED"));
    }

    @Test
    void buildPlan_expansionEnabled_passesExpandedTextToClassifier() {
        RagConfig cfg =
                new RagConfig(
                        true,
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
                        false,
                        false,
                        false,
                        5,
                        0.2,
                        "llm",
                        "emb",
                        "cls",
                        "reason",
                        false,
                        RagConfig.DEFAULT_NAIVE_FULL_CORPUS_MAX_CHARS,
                        RagConfig.DEFAULT_ADVANCED_RETRIEVAL_MAX_CONTEXT_CHARS,
                        MaterializationStrategy.STRUCTURED_SEARCH);

        QueryExpander expander = mock(QueryExpander.class);
        when(expander.expand("hello")).thenReturn("expanded hello");
        QueryExpansionStage expansionStage = new QueryExpansionStage(expander);

        QueryClassifierAdapter classifierAdapter = mock(QueryClassifierAdapter.class);
        when(classifierAdapter.classify(any(), eq("expanded hello")))
                .thenReturn(
                        new QueryClassifierAdapter.ClassifierOutcome(
                                "OK",
                                Optional.empty(),
                                ClassifierStatus.OK,
                                "m",
                                "OK"));

        NamedEntityExtractionAdapter ner = mock(NamedEntityExtractionAdapter.class);
        when(ner.extract(any(), eq("expanded hello"))).thenReturn(EntityExtractionResult.emptyWithNote(""));

        StructuredQueryRewriter rewriter = mock(StructuredQueryRewriter.class);
        when(rewriter.rewrite(any(), any(), anyString(), any(), any(), any()))
                .thenReturn(StructuredRewriteResult.identityDisabled("expanded hello", null));

        QueryIntentResolver intentResolver = mock(QueryIntentResolver.class);
        when(intentResolver.resolve(any(), any(), anyString(), any(), any(), any()))
                .thenReturn(QueryIntent.UNKNOWN);

        ExpectedAnswerShapeResolver shapeResolver = mock(ExpectedAnswerShapeResolver.class);
        when(shapeResolver.resolve(any(), any())).thenReturn(ExpectedAnswerShape.UNKNOWN);

        AmbiguityAssessmentService ambiguityAssessmentService = mock(AmbiguityAssessmentService.class);
        when(ambiguityAssessmentService.assess(any(), any(), anyString(), any(), any(), any()))
                .thenReturn(AmbiguityAssessment.sufficient());

        DefaultQueryUnderstandingPipeline pipeline =
                new DefaultQueryUnderstandingPipeline(
                        classifierAdapter,
                        ner,
                        rewriter,
                        intentResolver,
                        shapeResolver,
                        ambiguityAssessmentService,
                        expansionStage);

        QueryPlan plan = pipeline.buildPlan(ctx(cfg, "hello"));

        assertTrue(plan.queryExpansion().applied());
        assertEquals("expanded hello", plan.queryExpansion().expandedQuery());
        assertTrue(plan.pipelineNotes().stream().anyMatch(n -> n.contains("qu_expand") && n.contains("applied=true")));
        verify(classifierAdapter).classify(any(), eq("expanded hello"));
        verify(ner).extract(any(), eq("expanded hello"));
    }

    @SuppressWarnings("unchecked")
    private static DefaultQueryClassifierAdapter classifierAdapter(QueryClassifier classifier) {
        ObjectProvider<RuntimeObservability> obs = mock(ObjectProvider.class);
        ObjectProvider<Tracer> tracer = mock(ObjectProvider.class);
        RagClassifierProperties props = new RagClassifierProperties();
        return new DefaultQueryClassifierAdapter(classifier, props, obs, tracer);
    }
}

