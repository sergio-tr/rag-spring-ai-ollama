package com.uniovi.rag.application.service.runtime;

import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.engine.ExecutionStageOutcome;
import com.uniovi.rag.domain.runtime.engine.ExecutionStageTrace;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.api.OllamaOptions;

import java.util.concurrent.TimeUnit;

/**
 * Shared LLM invocation and trace fragment helpers only (no retrieval, selection, or config logic).
 */
public abstract class AbstractExecutionWorkflow implements ExecutionWorkflow {

    protected final ChatClient chatClient;

    protected AbstractExecutionWorkflow(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    protected String invokeChat(ExecutionContext ctx, String systemPrompt, String userMessage) {
        var spec = chatClient.prompt();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            spec = spec.system(systemPrompt);
        }
        spec = spec.user(userMessage);
        if (ctx.chatModelOverride().isPresent()) {
            String m = ctx.chatModelOverride().get().trim();
            if (!m.isBlank()) {
                spec = spec.options(OllamaOptions.builder().model(m).build());
            }
        }
        String out = spec.call().content();
        return out != null ? out : "";
    }

    protected static ExecutionStageTrace stage(
            String name, long startNanos, ExecutionStageOutcome outcome, String message) {
        long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
        return new ExecutionStageTrace(name, ms, outcome, message != null ? message : "");
    }
}
