package com.uniovi.rag.application.service.runtime;

import com.uniovi.rag.domain.config.capability.CapabilitySet;
import com.uniovi.rag.domain.config.indexing.ReindexImpact;
import com.uniovi.rag.domain.config.prompt.SystemPromptLayers;
import com.uniovi.rag.domain.config.runtime.ConfigProvenance;
import com.uniovi.rag.domain.config.runtime.ResolvedRuntimeConfig;
import com.uniovi.rag.domain.config.validation.CompatibilityResult;
import com.uniovi.rag.domain.knowledge.MaterializationStrategy;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.application.service.runtime.retrieval.AdvancedRetrievalPipeline;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.engine.KnowledgeSnapshotSelection;
import com.uniovi.rag.domain.runtime.engine.RuntimeOperationKind;
import com.uniovi.rag.domain.runtime.memory.ConversationMemoryOutcome;
import com.uniovi.rag.domain.runtime.query.*;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class CanonicalQueryUsageWorkflowTest {

    private static QueryPlan plan(String raw, String rewritten) {
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

    private static ExecutionContext ctxWithPlan(QueryPlan plan) {
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
                Optional.empty(),
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
                Optional.empty());
    }

    @Test
    void denseWorkflow_usesRewrittenQueryForRetrievalAndGeneration() {
        AdvancedRetrievalPipeline pipeline = mock(AdvancedRetrievalPipeline.class);
        com.uniovi.rag.domain.runtime.retrieval.CuratedContextSet curated =
                new com.uniovi.rag.domain.runtime.retrieval.CuratedContextSet(
                        List.of(),
                        "CTX",
                        new com.uniovi.rag.domain.runtime.retrieval.CompressionOutcome(0, 0, 0, List.of()),
                        List.of(),
                        new com.uniovi.rag.domain.runtime.retrieval.RetrievalDiagnostics(
                                com.uniovi.rag.domain.runtime.retrieval.RetrievalMode.DENSE_ONLY,
                                java.util.Optional.empty(),
                                "",
                                0,
                                0,
                                0,
                                0,
                                0,
                                0),
                        List.of(),
                        List.of());
        when(pipeline.retrieve(any(), any(), anyString())).thenReturn(curated);

        ChatClient chatClient = mock(ChatClient.class, org.mockito.Answers.RETURNS_DEEP_STUBS);
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content()).thenReturn("ANS");

        ChunkDenseRagWorkflow wf = new ChunkDenseRagWorkflow(chatClient, pipeline);

        QueryPlan qp = plan("raw user query", "rewritten query");
        ExecutionContext ctx = ctxWithPlan(qp);
        wf.execute(ctx);

        verify(pipeline).retrieve(eq(ctx), eq(qp), eq("ChunkDenseRagWorkflow"));
    }
}

