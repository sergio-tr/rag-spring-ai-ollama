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
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.infrastructure.classifier.QueryClassifier;
import com.uniovi.rag.service.analyser.QueryAnalyser;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class DefaultQueryUnderstandingPipelineTest {

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
                Optional.empty());
    }

    @Test
    void buildPlan_alwaysProducesValidQueryPlan_andFrozenStageOrder() {
        RagConfig rag =
                new RagConfig(
                        false,
                        true,
                        true,
                        false,
                        false,
                        false,
                        false,
                        false,
                        true,
                        false,
                        5,
                        0.2,
                        "llm",
                        "emb",
                        "cls",
                        "reason",
                        false,
                        RagConfig.DEFAULT_NAIVE_FULL_CORPUS_MAX_CHARS,
                        MaterializationStrategy.CHUNK_LEVEL);

        QueryClassifier classifier = mock(QueryClassifier.class);
        when(classifier.classify(anyString())).thenReturn(QueryType.FILTER_AND_LIST);

        QueryAnalyser analyser = mock(QueryAnalyser.class);
        when(analyser.analyse(anyString())).thenReturn(new JSONObject("{\"date\":[],\"place\":[],\"attendees\":[],\"topics\":[],\"mentionedEntities\":[],\"answerType\":\"unknown\",\"comparisonType\":\"none\",\"temporalContext\":\"none\"}"));

        ChatClient chatClient = mock(ChatClient.class, org.mockito.Answers.RETURNS_DEEP_STUBS);
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

        assertEquals(QueryPlan.VERSION_P6_QU_CORE_V1, plan.queryPlanVersion());
        assertEquals("  list   all  items ", plan.rawUserQuery());
        assertEquals("list all items", plan.normalizedQueryText());
        assertEquals("list all items", plan.rewrittenQueryText());
        assertTrue(plan.queryIntent() != null);
        assertTrue(plan.pipelineNotes().size() >= 7);

        // Frozen stage ordering: first seven notes correspond to the QU stages in-order.
        assertTrue(plan.pipelineNotes().get(0).startsWith("qu_normalize "));
        assertTrue(plan.pipelineNotes().get(1).startsWith("qu_classify "));
        assertTrue(plan.pipelineNotes().get(2).startsWith("qu_extract_entities "));
        assertTrue(plan.pipelineNotes().get(3).startsWith("qu_rewrite "));
        assertTrue(plan.pipelineNotes().get(4).startsWith("qu_resolve_intent "));
        assertTrue(plan.pipelineNotes().get(5).startsWith("qu_resolve_answer_shape "));
        assertTrue(plan.pipelineNotes().get(6).startsWith("qu_assess_ambiguity "));
    }

    @Test
    void classifierDisabled_whenToolsDisabled() {
        RagConfig rag =
                new RagConfig(
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        true,
                        false,
                        5,
                        0.2,
                        "llm",
                        "emb",
                        "cls",
                        "reason",
                        false,
                        RagConfig.DEFAULT_NAIVE_FULL_CORPUS_MAX_CHARS,
                        MaterializationStrategy.CHUNK_LEVEL);

        QueryClassifier classifier = mock(QueryClassifier.class);
        QueryClassifierAdapter adapter = new DefaultQueryClassifierAdapter(classifier);

        var outcome = adapter.classify(ctx(rag, "q"), "q");
        assertEquals("UNCLASSIFIED", outcome.classifierLabel());
        assertTrue(outcome.classifierQueryType().isEmpty());
        assertEquals(com.uniovi.rag.domain.runtime.query.ClassifierStatus.DISABLED, outcome.classifierStatus());
        verifyNoInteractions(classifier);
    }
}

