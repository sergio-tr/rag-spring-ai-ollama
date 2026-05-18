package com.uniovi.rag.application.service.runtime.query;

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
import com.uniovi.rag.infrastructure.classifier.QueryClassifier;
import com.uniovi.rag.service.analyser.QueryAnalyser;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.ai.chat.client.ChatClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class DefaultQueryUnderstandingPipelineTest {

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
        when(classifier.classify(anyString(), eq("cls"))).thenReturn(QueryType.FILTER_AND_LIST);

        QueryAnalyser analyser = mock(QueryAnalyser.class);
        when(analyser.analyse(anyString())).thenReturn(new JSONObject("{\"date\":[],\"place\":[],\"attendees\":[],\"topics\":[],\"mentionedEntities\":[],\"answerType\":\"unknown\",\"comparisonType\":\"none\",\"temporalContext\":\"none\"}"));

        ChatClient chatClient = mock(ChatClient.class, Answers.RETURNS_DEEP_STUBS);
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
        when(chatClient.prompt().system(anyString()).user(anyString()).options(any()).call().content())
                .thenReturn(rewriteJson);

        DefaultQueryClassifierAdapter classifierAdapter = new DefaultQueryClassifierAdapter(classifier);
        DefaultNamedEntityExtractionAdapter nerAdapter = new DefaultNamedEntityExtractionAdapter(analyser);
        DefaultStructuredQueryRewriter rewriter = new DefaultStructuredQueryRewriter(chatClient);
        DefaultQueryIntentResolver intentResolver = new DefaultQueryIntentResolver();
        DefaultExpectedAnswerShapeResolver shapeResolver = new DefaultExpectedAnswerShapeResolver();
        DefaultAmbiguityAssessmentService ambiguity = new DefaultAmbiguityAssessmentService();

        DefaultQueryUnderstandingPipeline pipeline =
                new DefaultQueryUnderstandingPipeline(
                        classifierAdapter, nerAdapter, rewriter, intentResolver, shapeResolver, ambiguity);

        QueryPlan plan = pipeline.buildPlan(ctx(rag, "  list   all  items "));

        assertEquals(QueryPlan.VERSION_P12_MEMORY_CONVERSATIONAL_FLOW_V1, plan.queryPlanVersion());
        assertEquals("  list   all  items ", plan.rawUserQuery());
        assertEquals("list all items", plan.normalizedQueryText());
        assertEquals("list all items", plan.rewrittenQueryText());
        assertTrue(plan.queryIntent() != null);
        assertTrue(plan.pipelineNotes().size() >= 7);

        // Frozen stage ordering: first seven notes correspond to the QU stages in-order.
        assertTrue(plan.pipelineNotes().get(0).startsWith("qu_normalize "));
        assertTrue(plan.pipelineNotes().get(1).startsWith("qu_classify "));
        assertTrue(plan.pipelineNotes().get(1).contains("classifierModelId=cls"));
        verify(classifier).classify("list all items", "cls");
        assertTrue(plan.pipelineNotes().get(2).startsWith("qu_extract_entities "));
        assertTrue(plan.pipelineNotes().get(3).startsWith("qu_rewrite "));
        assertTrue(plan.pipelineNotes().get(4).startsWith("qu_resolve_intent "));
        assertTrue(plan.pipelineNotes().get(5).startsWith("qu_resolve_answer_shape "));
        assertTrue(plan.pipelineNotes().get(6).startsWith("qu_assess_ambiguity "));
    }

    @Test
    void classifierDisabled_whenToolsDisabled() {
        RagConfig rag = rag(false, false);

        QueryClassifier classifier = mock(QueryClassifier.class);
        QueryClassifierAdapter adapter = new DefaultQueryClassifierAdapter(classifier);

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
                        ambiguityAssessmentService);

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
                        ambiguityAssessmentService);

        QueryPlan plan = pipeline.buildPlan(ctx(cfg, "q"));

        assertTrue(plan.pipelineNotes().get(1).contains("qu_classify"));
        assertTrue(plan.pipelineNotes().get(1).contains("qu_status=FALLBACK"));

        assertTrue(plan.pipelineNotes().get(2).contains("qu_extract_entities"));
        assertTrue(plan.pipelineNotes().get(2).contains("qu_status=ERROR"));

        assertTrue(plan.pipelineNotes().get(3).contains("qu_rewrite"));
        assertTrue(plan.pipelineNotes().get(3).contains("qu_status=ERROR"));
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
                        ambiguityAssessmentService);

        QueryPlan plan = pipeline.buildPlan(ctx(cfg, "x"));
        assertTrue(plan.pipelineNotes().get(3).contains("qu_status=DISABLED"));
        assertTrue(plan.pipelineNotes().get(1).contains("qu_status=ERROR"));
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
                        ambiguityAssessmentService);

        QueryPlan plan = pipeline.buildPlan(ctx(cfg, "list"));
        assertTrue(plan.pipelineNotes().get(3).contains("qu_status=DISABLED"));
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
                        ambiguityAssessmentService);

        QueryPlan plan = pipeline.buildPlan(ctx(cfg, "q"));
        assertTrue(plan.pipelineNotes().get(2).contains("qu_status=DISABLED"));
    }
}

