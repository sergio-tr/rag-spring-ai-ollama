package com.uniovi.rag.service.evaluation.preset;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Optional;

/**
 * Terminal JSON override for Lab RAG preset benchmarks while {@link com.uniovi.rag.application.service.runtime.ExecutionContextFactory#buildForLegacyHttp(String, String)} resolves runtime config.
 * <p>Scoped per preset batch; always cleared via try-with-resources.</p>
 */
public final class BenchmarkPresetEvaluationContext {

    private static final ThreadLocal<JsonNode> TERMINAL_OVERRIDE = new ThreadLocal<>();

    private BenchmarkPresetEvaluationContext() {}

    /** Active terminal merge JSON for legacy HTTP evaluation queries, if any. */
    public static Optional<JsonNode> currentTerminalOverride() {
        return Optional.ofNullable(TERMINAL_OVERRIDE.get());
    }

    /**
     * Installs a terminal runtime JSON layer (same keys as {@link com.uniovi.rag.domain.runtime.RagConfig#applyJsonOverrides}).
     */
    public static AutoCloseable open(JsonNode terminalRuntimeOverride) {
        if (terminalRuntimeOverride == null || terminalRuntimeOverride.isNull()) {
            return () -> {};
        }
        TERMINAL_OVERRIDE.set(terminalRuntimeOverride);
        return () -> TERMINAL_OVERRIDE.remove();
    }

    /** Removes any installed override (safe if none). */
    public static void clear() {
        TERMINAL_OVERRIDE.remove();
    }
}
