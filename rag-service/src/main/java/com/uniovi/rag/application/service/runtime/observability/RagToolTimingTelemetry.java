package com.uniovi.rag.application.service.runtime.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;

/** Tool-level timing for metadata deterministic paths. */
public final class RagToolTimingTelemetry {

    private static final Logger log = LoggerFactory.getLogger(RagToolTimingTelemetry.class);

    private RagToolTimingTelemetry() {}

    public static void logTool(
            @Nullable String toolKind,
            String stage,
            long durationMs,
            String outcome,
            @Nullable String detail) {
        ChatLatencyCollector.recordStage("metadata_tool_" + stage, durationMs);
        log.info(
                "RAG_TOOL_TIMING toolKind={} stage={} durationMs={} outcome={} detail={}",
                toolKind != null ? toolKind : "",
                stage != null ? stage : "",
                durationMs,
                outcome != null ? outcome : "",
                detail != null ? detail : "");
    }

    public static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }
}
