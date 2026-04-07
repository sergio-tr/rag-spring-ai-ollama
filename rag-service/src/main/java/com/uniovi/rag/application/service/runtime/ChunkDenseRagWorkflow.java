package com.uniovi.rag.application.service.runtime;

import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.engine.ExecutionStageOutcome;
import com.uniovi.rag.domain.runtime.engine.ExecutionStageTrace;
import com.uniovi.rag.domain.runtime.engine.RagExecutionResult;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ChunkDenseRagWorkflow extends AbstractExecutionWorkflow {

    private final SnapshotBoundRetrievalService snapshotBoundRetrievalService;

    public ChunkDenseRagWorkflow(
            ChatClient chatClient, SnapshotBoundRetrievalService snapshotBoundRetrievalService) {
        super(chatClient);
        this.snapshotBoundRetrievalService = snapshotBoundRetrievalService;
    }

    @Override
    public RagExecutionResult execute(ExecutionContext ctx) {
        long t0 = System.nanoTime();
        List<ExecutionStageTrace> stages = new ArrayList<>();
        String q = canonicalGenerationQuery(ctx);
        String context =
                snapshotBoundRetrievalService.buildRetrievalContext(
                        ctx, q, SnapshotBoundRetrievalService.DenseLayout.CHUNK_SEPARATE);
        stages.add(stage("dense_retrieval", t0, ExecutionStageOutcome.SUCCESS, ""));
        long t1 = System.nanoTime();
        String user = RuntimeAnswerPrompts.ragUserTurn(q, context);
        String answer = invokeChat(ctx, ctx.effectiveSystemPrompt(), user);
        stages.add(stage("llm", t1, ExecutionStageOutcome.SUCCESS, ""));
        return RagExecutionResult.withPlaceholderTrace(
                answer,
                workflowName(),
                true,
                false,
                ctx.knowledgeSnapshotSelection().orderedSnapshotIds(),
                null,
                stages);
    }

    @Override
    public String workflowName() {
        return "ChunkDenseRagWorkflow";
    }
}
