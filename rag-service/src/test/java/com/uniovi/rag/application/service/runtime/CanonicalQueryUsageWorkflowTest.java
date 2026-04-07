package com.uniovi.rag.application.service.runtime;

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
                        5,
                        0.2,
                        "llm",
                        "emb",
                        "cls",
                        "reason",
                        false,
                        RagConfig.DEFAULT_NAIVE_FULL_CORPUS_MAX_CHARS,
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
                KnowledgeSnapshotSelection.empty(),
                Optional.empty(),
                Optional.empty(),
                "corr",
                List.of("all"),
                Optional.empty(),
                Optional.of(plan));
    }

    @Test
    void denseWorkflow_usesRewrittenQueryForRetrievalAndGeneration() {
        SnapshotBoundRetrievalService retrieval = mock(SnapshotBoundRetrievalService.class);
        when(retrieval.buildRetrievalContext(any(), anyString(), any())).thenReturn("CTX");

        ChatClient chatClient = mock(ChatClient.class, org.mockito.Answers.RETURNS_DEEP_STUBS);
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content()).thenReturn("ANS");

        ChunkDenseRagWorkflow wf = new ChunkDenseRagWorkflow(chatClient, retrieval);

        QueryPlan qp = plan("raw user query", "rewritten query");
        ExecutionContext ctx = ctxWithPlan(qp);
        wf.execute(ctx);

        verify(retrieval).buildRetrievalContext(eq(ctx), eq("rewritten query"), any());
    }
}

