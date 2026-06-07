package com.uniovi.rag.application.service.runtime;

import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import java.util.Optional;

/**
 * Resolves the Ollama chat model id for a turn: explicit per-message override wins, then resolved {@link RagConfig}.
 */
public final class ChatGenerationModelSelector {

    private ChatGenerationModelSelector() {}

    /**
     * Request-scoped {@link ExecutionContext#chatModelOverride()} wins; otherwise uses {@code resolved.toRagConfig().llmModel()}.
     */
    public static Optional<String> effectiveChatModelId(ExecutionContext ctx) {
        if (ctx == null) {
            return Optional.empty();
        }
        Optional<String> o = ctx.chatModelOverride();
        if (o.isPresent()) {
            String t = o.get().trim();
            if (!t.isBlank()) {
                return Optional.of(t);
            }
        }
        RagConfig rag = ctx.resolved() != null ? ctx.resolved().toRagConfig() : null;
        if (rag != null && rag.llmModel() != null && !rag.llmModel().isBlank()) {
            return Optional.of(rag.llmModel().trim());
        }
        return Optional.empty();
    }
}
