package com.uniovi.rag.application.service.runtime;

import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.engine.ExecutionStageOutcome;
import com.uniovi.rag.domain.runtime.engine.ExecutionStageTrace;
import com.uniovi.rag.domain.runtime.engine.RagExecutionResult;
import com.uniovi.rag.domain.runtime.policy.AnswerGroundingPolicy;
import com.uniovi.rag.infrastructure.observability.ObservabilitySupport;
import com.uniovi.rag.application.service.runtime.llm.RagLlmChatInvoker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class DirectLlmWorkflow extends AbstractExecutionWorkflow {

    public DirectLlmWorkflow(
            RagLlmChatInvoker llmChatInvoker, @Autowired(required = false) ObservabilitySupport observability) {
        super(llmChatInvoker, observability);
    }

    @Override
    public RagExecutionResult execute(ExecutionContext ctx) {
        long t0 = System.nanoTime();
        List<ExecutionStageTrace> stages = new ArrayList<>();
        String q = canonicalGenerationQuery(ctx);
        RagConfig rag = ctx.resolved().toRagConfig();
        AnswerGroundingPolicy policy = AnswerGroundingPolicySelector.from(rag);
        String answer;
        boolean abstention = false;
        String abstentionReason = "";

        if (policy == AnswerGroundingPolicy.DIRECT_UNGROUNDED_BASELINE) {
            String user = RuntimeAnswerPrompts.directBaselineUserTurn(q, answerPlanBlock(ctx));
            answer = invokeChat(ctx, ctx.effectiveSystemPrompt(), user);
            stages.add(stage("llm", t0, ExecutionStageOutcome.SUCCESS, "direct_baseline"));
        } else if (RuntimeAnswerPrompts.requiresStrictDocumentGrounding(q)) {
            answer = RuntimeAnswerPrompts.documentBoundRequiresRetrievalMessageFor(q);
            abstention = true;
            abstentionReason = "document_bound_requires_retrieval";
            stages.add(stage("llm", t0, ExecutionStageOutcome.SKIPPED, "document_bound_requires_retrieval"));
        } else {
            String user = RuntimeAnswerPrompts.ragUserTurn(q, "", policy, false, Optional.empty(), answerPlanBlock(ctx));
            answer = invokeChat(ctx, ctx.effectiveSystemPrompt(), user);
            stages.add(stage("llm", t0, ExecutionStageOutcome.SUCCESS, ""));
        }

        stages.add(RuntimeAnswerPrompts.runtimeAnswerMetaStage(policy, 0, 0, abstention, abstentionReason));

        return RagExecutionResult.withPlaceholderTrace(
                answer,
                workflowName(),
                false,
                false,
                List.of(),
                null,
                stages);
    }

    @Override
    public String workflowName() {
        return "DirectLlmWorkflow";
    }
}
