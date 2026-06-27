package com.uniovi.rag.application.service.runtime.llm;

import com.uniovi.rag.domain.llm.ResolvedLlmConfig;
import java.util.Optional;

/**
 * Request-turn binding for {@link ResolvedLlmConfig} resolved at {@code ExecutionContextFactory} time.
 * Cleared by the runtime entry point after orchestration completes (same thread).
 */
public final class OrchestrationLlmConfigScope {

    private static final ThreadLocal<ResolvedLlmConfig> CURRENT = new ThreadLocal<>();

    private OrchestrationLlmConfigScope() {}

    public static void bind(ResolvedLlmConfig config) {
        if (config == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(config);
        }
    }

    public static Optional<ResolvedLlmConfig> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static void clear() {
        CURRENT.remove();
    }
}
