package com.uniovi.rag.application.service.runtime;

import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.engine.ExecutionStageOutcome;
import com.uniovi.rag.domain.runtime.engine.ExecutionStageTrace;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.reasoning.StructuredAnswerPlan;
import com.uniovi.rag.infrastructure.observability.ObservabilitySupport;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.lang.Nullable;

import java.util.concurrent.TimeUnit;

/**
 * Shared LLM invocation and trace fragment helpers only (no retrieval, selection, or config logic).
 */
public abstract class AbstractExecutionWorkflow implements ExecutionWorkflow {

    protected final ChatClient chatClient;
    private final ObservabilitySupport observability;

    protected AbstractExecutionWorkflow(ChatClient chatClient, @Nullable ObservabilitySupport observability) {
        this.chatClient = chatClient;
        this.observability = observability;
    }

    protected String invokeChat(ExecutionContext ctx, String systemPrompt, String userMessage) {
        if (observability == null) {
            return invokeChatUnscoped(ctx, systemPrompt, userMessage);
        }
        return observability.recordExecutionWorkflowLlmInvocation(
                this.getClass().getSimpleName(),
                () -> invokeChatUnscoped(ctx, systemPrompt, userMessage));
    }

    private String invokeChatUnscoped(ExecutionContext ctx, String systemPrompt, String userMessage) {
        var builder = chatClient.prompt();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            builder = builder.system(systemPrompt);
        }
        var userSpec = builder.user(userMessage);
        var withModel =
                ChatGenerationModelSelector.effectiveChatModelId(ctx)
                        .map(m -> userSpec.options(OllamaOptions.builder().model(m).build()))
                        .orElse(userSpec);
        String out = withModel.call().content();
        return out != null ? out : "";
    }

    protected static String canonicalGenerationQuery(ExecutionContext ctx) {
        QueryPlan qp = ctx.queryPlan()
                .orElseThrow(() -> new IllegalStateException("QueryPlan missing on orchestrated workflow execution"));
        String q = qp.rewrittenQueryText();
        if (q == null || q.isBlank()) {
            throw new IllegalStateException("rewrittenQueryText must be non-blank");
        }
        return q;
    }

    protected static String answerPlanBlock(ExecutionContext ctx) {
        if (ctx == null) {
            return null;
        }
        StructuredAnswerPlan plan = ctx.structuredAnswerPlan().orElse(null);
        if (plan == null) {
            return null;
        }
        String block = plan.toPromptBlock(800);
        return block != null && !block.isBlank() ? block : null;
    }

    protected static ExecutionStageTrace stage(
            String name, long startNanos, ExecutionStageOutcome outcome, String message) {
        long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
        return new ExecutionStageTrace(name, ms, outcome, message != null ? message : "");
    }
}
