package com.uniovi.rag.application.service.runtime;

import com.uniovi.rag.application.service.runtime.retrieval.AdvancedRetrievalPipeline;
import com.uniovi.rag.domain.runtime.advisor.PackedContextSet;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.engine.ExecutionStageOutcome;
import com.uniovi.rag.domain.runtime.engine.ExecutionStageTrace;
import com.uniovi.rag.domain.runtime.engine.RagExecutionResult;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.retrieval.CuratedContextSet;
import com.uniovi.rag.infrastructure.observability.ObservabilitySupport;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Shared implementation for dense workflows that differ only by {@link #workflowName()}.
 */
abstract class AbstractDenseRagWorkflow extends AbstractExecutionWorkflow {

    private final AdvancedRetrievalPipeline advancedRetrievalPipeline;

    protected AbstractDenseRagWorkflow(
            ChatClient chatClient,
            AdvancedRetrievalPipeline advancedRetrievalPipeline,
            @Autowired(required = false) ObservabilitySupport observability) {
        super(chatClient, observability);
        this.advancedRetrievalPipeline = advancedRetrievalPipeline;
    }

    @Override
    public RagExecutionResult execute(ExecutionContext ctx) {
        QueryPlan plan = ctx.queryPlan().orElseThrow(() -> new IllegalStateException("QueryPlan required"));
        String q = canonicalGenerationQuery(ctx);
        long tLlm = System.nanoTime();

        Optional<PackedContextSet> packed = ctx.advisorPackedContextSet();
        if (packed.isPresent()) {
            String user = RuntimeAnswerPrompts.ragUserTurn(q, packed.get().promptContextText());
            String answer = invokeChat(ctx, ctx.effectiveSystemPrompt(), user);
            List<ExecutionStageTrace> stages = new ArrayList<>();
            stages.add(stage("llm", tLlm, ExecutionStageOutcome.SUCCESS, "from_advisor_packed_context"));
            return RagExecutionResult.withPlaceholderTrace(
                    answer,
                    workflowName(),
                    true,
                    false,
                    ctx.knowledgeSnapshotSelection().orderedSnapshotIds(),
                    null,
                    Optional.empty(),
                    stages);
        }

        CuratedContextSet curated = advancedRetrievalPipeline.retrieve(ctx, plan, workflowName());
        List<ExecutionStageTrace> stages = new ArrayList<>(curated.retrievalStageTraces());
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
}

