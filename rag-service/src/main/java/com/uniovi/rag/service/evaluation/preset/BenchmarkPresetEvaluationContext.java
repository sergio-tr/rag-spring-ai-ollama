package com.uniovi.rag.service.evaluation.preset;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Terminal JSON override for Lab RAG preset benchmarks while {@link com.uniovi.rag.application.service.runtime.ExecutionContextFactory#buildForLegacyHttp(String, String)} resolves runtime config.
 * <p>Scoped per preset batch; always cleared via try-with-resources.</p>
 */
public final class BenchmarkPresetEvaluationContext {

    private static final ThreadLocal<JsonNode> TERMINAL_OVERRIDE = new ThreadLocal<>();
    private static final ThreadLocal<LabRuntimeContext> LAB_CONTEXT = new ThreadLocal<>();

    private BenchmarkPresetEvaluationContext() {}

    /** Active terminal merge JSON for legacy HTTP evaluation queries, if any. */
    public static Optional<JsonNode> currentTerminalOverride() {
        return Optional.ofNullable(TERMINAL_OVERRIDE.get());
    }

    /** Active Lab-only runtime context (project/snapshot binding) for legacy HTTP evaluation queries, if any. */
    public static Optional<LabRuntimeContext> currentLabRuntimeContext() {
        return Optional.ofNullable(LAB_CONTEXT.get());
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

    /**
     * Installs both terminal runtime override and Lab runtime binding (project + explicit snapshot ids).
     *
     * <p>Lab-only: never used by normal chat runtime.</p>
     */
    public static AutoCloseable openLab(
            JsonNode terminalRuntimeOverride,
            UUID runId,
            UUID projectId,
            List<UUID> snapshotIds,
            String groupKey,
            String presetCode,
            boolean forcedSnapshotSelection) {
        AutoCloseable a = open(terminalRuntimeOverride);
        LAB_CONTEXT.set(
                new LabRuntimeContext(
                        runId,
                        projectId,
                        snapshotIds != null ? List.copyOf(snapshotIds) : List.of(),
                        groupKey,
                        presetCode,
                        forcedSnapshotSelection));
        return () -> {
            try {
                a.close();
            } finally {
                LAB_CONTEXT.remove();
            }
        };
    }

    /** Removes any installed override (safe if none). */
    public static void clear() {
        TERMINAL_OVERRIDE.remove();
        LAB_CONTEXT.remove();
    }

    public record LabRuntimeContext(
            UUID runId,
            UUID projectId,
            List<UUID> snapshotIds,
            String groupKey,
            String presetCode,
            boolean forcedSnapshotSelection
    ) {
        public LabRuntimeContext {
            snapshotIds = snapshotIds != null ? List.copyOf(snapshotIds) : List.of();
        }
    }
}
