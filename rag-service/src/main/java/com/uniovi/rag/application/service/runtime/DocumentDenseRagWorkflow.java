package com.uniovi.rag.application.service.runtime;

import com.uniovi.rag.application.service.runtime.retrieval.AdvancedRetrievalPipeline;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.engine.ExecutionStageOutcome;
import com.uniovi.rag.domain.runtime.engine.ExecutionStageTrace;
import com.uniovi.rag.domain.runtime.engine.RagExecutionResult;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.retrieval.CuratedContextSet;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class DocumentDenseRagWorkflow extends AbstractExecutionWorkflow {

    private final AdvancedRetrievalPipeline advancedRetrievalPipeline;

    public DocumentDenseRagWorkflow(ChatClient chatClient, AdvancedRetrievalPipeline advancedRetrievalPipeline) {
        super(chatClient);
        this.advancedRetrievalPipeline = advancedRetrievalPipeline;
    }

    @Override
    public RagExecutionResult execute(ExecutionContext ctx) {
        QueryPlan plan = ctx.queryPlan().orElseThrow(() -> new IllegalStateException("QueryPlan required"));
        CuratedContextSet curated = advancedRetrievalPipeline.retrieve(ctx, plan, workflowName());
        List<ExecutionStageTrace> stages = new ArrayList<>(curated.retrievalStageTraces());
        String q = canonicalGenerationQuery(ctx);
        long tLlm = System.nanoTime();
        String user = RuntimeAnswerPrompts.ragUserTurn(q, curated.promptContextText());
        String answer = invokeChat(ctx, ctx.effectiveSystemPrompt(), user);
        stages.add(stage("llm", tLlm, ExecutionStageOutcome.SUCCESS, ""));
        return RagExecutionResult.withPlaceholderTrace(
                answer,
                workflowName(),
                true,
                false,
                ctx.knowledgeSnapshotSelection().orderedSnapshotIds(),
                null,
                Optional.ofNullable(curated.diagnostics()),
                stages);
    }

    @Override
    public String workflowName() {
        return "DocumentDenseRagWorkflow";
    }
}
