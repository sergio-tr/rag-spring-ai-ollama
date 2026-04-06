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
public class DirectLlmWorkflow extends AbstractExecutionWorkflow {

    public DirectLlmWorkflow(ChatClient chatClient) {
        super(chatClient);
    }

    @Override
    public RagExecutionResult execute(ExecutionContext ctx) {
        long t0 = System.nanoTime();
        List<ExecutionStageTrace> stages = new ArrayList<>();
        String answer = invokeChat(ctx, ctx.effectiveSystemPrompt(), ctx.userQuery());
        stages.add(stage("llm", t0, ExecutionStageOutcome.SUCCESS, ""));
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
