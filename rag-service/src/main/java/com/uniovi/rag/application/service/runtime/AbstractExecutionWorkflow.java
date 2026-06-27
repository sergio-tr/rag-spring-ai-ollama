package com.uniovi.rag.application.service.runtime;

import com.uniovi.rag.application.service.runtime.llm.RagLlmChatInvoker;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.engine.ExecutionStageOutcome;
import com.uniovi.rag.domain.runtime.engine.ExecutionStageTrace;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.reasoning.StructuredAnswerPlan;
import com.uniovi.rag.infrastructure.observability.ObservabilitySupport;
import org.springframework.lang.Nullable;

import java.util.concurrent.TimeUnit;

/**
 * Shared LLM invocation and trace fragment helpers only (no retrieval, selection, or config logic).
 */
public abstract class AbstractExecutionWorkflow implements ExecutionWorkflow {

    protected final RagLlmChatInvoker llmChatInvoker;
    private final ObservabilitySupport observability;

    protected AbstractExecutionWorkflow(RagLlmChatInvoker llmChatInvoker, @Nullable ObservabilitySupport observability) {
        this.llmChatInvoker = llmChatInvoker;
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
        return llmChatInvoker.invoke(ctx, systemPrompt, userMessage);
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
