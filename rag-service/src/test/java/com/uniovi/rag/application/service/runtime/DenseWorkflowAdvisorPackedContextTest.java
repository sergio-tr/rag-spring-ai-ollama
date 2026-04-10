package com.uniovi.rag.application.service.runtime;

import com.uniovi.rag.application.service.runtime.retrieval.AdvancedRetrievalPipeline;
import com.uniovi.rag.domain.config.capability.CapabilitySet;
import com.uniovi.rag.domain.config.indexing.ReindexImpact;
import com.uniovi.rag.domain.config.prompt.SystemPromptLayers;
import com.uniovi.rag.domain.config.runtime.ConfigProvenance;
import com.uniovi.rag.domain.config.runtime.ResolvedRuntimeConfig;
import com.uniovi.rag.domain.config.validation.CompatibilityResult;
import com.uniovi.rag.domain.knowledge.MaterializationStrategy;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.domain.runtime.advisor.PackedContextSet;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.engine.KnowledgeSnapshotSelection;
import com.uniovi.rag.domain.runtime.engine.RuntimeOperationKind;
import com.uniovi.rag.domain.runtime.memory.ConversationMemoryOutcome;
import com.uniovi.rag.domain.runtime.query.AmbiguityAssessment;
import com.uniovi.rag.domain.runtime.query.ClassifierStatus;
import com.uniovi.rag.domain.runtime.query.EntityExtractionResult;
import com.uniovi.rag.domain.runtime.query.ExpectedAnswerShape;
import com.uniovi.rag.domain.runtime.query.QueryIntent;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.query.StructuredRewriteResult;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DenseWorkflowAdvisorPackedContextTest {

    @Test
    void document_dense_skips_retrieval_pipeline_when_advisor_packed_context_present() {
        AdvancedRetrievalPipeline pipeline = mock(AdvancedRetrievalPipeline.class);
        ChatClient chatClient = mock(ChatClient.class, org.mockito.Answers.RETURNS_DEEP_STUBS);
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content()).thenReturn("ANS");

        DocumentDenseRagWorkflow wf = new DocumentDenseRagWorkflow(chatClient, pipeline);

        QueryPlan qp = minimalPlan("raw", "rewritten");
        PackedContextSet packed =
                new PackedContextSet(List.of(), "s", 0, 0, List.of(), "FROM_ADVISOR");
        ExecutionContext ctx = ctxWithPlanAndPack(qp, packed);

        var result = wf.execute(ctx);
        assertEquals("ANS", result.answerText());

        verify(pipeline, never()).retrieve(any(), any(), anyString());
    }

    @Test
    void chunk_dense_skips_retrieval_pipeline_when_advisor_packed_context_present() {
        AdvancedRetrievalPipeline pipeline = mock(AdvancedRetrievalPipeline.class);
        ChatClient chatClient = mock(ChatClient.class, org.mockito.Answers.RETURNS_DEEP_STUBS);
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content()).thenReturn("ANS");

        ChunkDenseRagWorkflow wf = new ChunkDenseRagWorkflow(chatClient, pipeline);

        QueryPlan qp = minimalPlan("raw", "rewritten");
        PackedContextSet packed =
                new PackedContextSet(List.of(), "s", 0, 0, List.of(), "FROM_ADVISOR");
        ExecutionContext ctx = ctxWithPlanAndPack(qp, packed);

        var result = wf.execute(ctx);
        assertEquals("ANS", result.answerText());

        verify(pipeline, never()).retrieve(any(), eq(qp), eq("ChunkDenseRagWorkflow"));
    }

    private static QueryPlan minimalPlan(String raw, String rewritten) {
        EntityExtractionResult entities =
                new EntityExtractionResult(
                        List.of(), List.of(), List.of(), List.of(), List.of(),
                        Optional.empty(), Optional.empty(), Optional.empty(), List.of());
        StructuredRewriteResult rewrite =
                new StructuredRewriteResult(
                        rewritten,
                        true,
                        List.of("OK"),
                        StructuredRewriteResult.STRATEGY_STRUCTURED_V1,
                        List.of(),
                        List.of(),
                        Optional.of(QueryIntent.UNKNOWN.name()),
                        Map.of(),
                        List.of());
        return new QueryPlan(
                QueryPlan.VERSION_P6_QU_CORE_V1,
                raw,
                raw,
                raw,
                rewritten,
                "UNCLASSIFIED",
                Optional.empty(),
                ClassifierStatus.DISABLED,
                QueryIntent.UNKNOWN,
                Map.of(),
                List.of(),
                List.of(),
                entities,
                rewrite,
                ExpectedAnswerShape.UNKNOWN,
                AmbiguityAssessment.sufficient(),
                "corr",
                "default",
                List.of());
    }

    private static ExecutionContext ctxWithPlanAndPack(QueryPlan plan, PackedContextSet packed) {
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
                true,
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
                MaterializationStrategy.DOCUMENT_LEVEL);
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
                plan.rawUserQuery(),
                RuntimeOperationKind.CHAT_MESSAGE,
                resolved,
                "sys",
                new KnowledgeSnapshotSelection(
                        List.of(UUID.randomUUID()),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty()),
                Optional.empty(),
                Optional.empty(),
                "corr",
                List.of("all"),
                Optional.empty(),
                Optional.of(plan),
                Optional.of(packed),
                plan.rawUserQuery(),
                plan.rawUserQuery(),
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
}
