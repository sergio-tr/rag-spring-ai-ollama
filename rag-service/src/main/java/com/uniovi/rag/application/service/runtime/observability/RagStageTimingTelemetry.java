package com.uniovi.rag.application.service.runtime.observability;

import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;

/** High-level RAG stage timing for latency audits. */
public final class RagStageTimingTelemetry {

    private static final Logger log = LoggerFactory.getLogger(RagStageTimingTelemetry.class);

    private RagStageTimingTelemetry() {}

    public static void logStage(
            @Nullable ExecutionContext ctx, String stage, long durationMs, String outcome, @Nullable String detail) {
        ChatLatencyCollector.recordStage(stage, durationMs);
        log.info(
                "RAG_STAGE_TIMING traceId={} conversationId={} presetId={} stage={} durationMs={} outcome={} detail={}",
                traceId(ctx),
                conversationId(ctx),
                presetId(ctx),
                stage,
                durationMs,
                outcome != null ? outcome : "",
                detail != null ? detail : "");
    }

    public static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    private static String traceId(@Nullable ExecutionContext ctx) {
        if (ctx == null || ctx.correlationId() == null) {
            return "";
        }
        return ctx.correlationId();
    }

    private static String conversationId(@Nullable ExecutionContext ctx) {
        if (ctx == null || ctx.conversationId() == null) {
            return "";
        }
        return ctx.conversationId().toString();
    }

    private static String presetId(@Nullable ExecutionContext ctx) {
        if (ctx == null
                || ctx.resolved() == null
                || ctx.resolved().provenance() == null
                || ctx.resolved().provenance().presetId() == null) {
            return "";
        }
        return ctx.resolved().provenance().presetId().toString();
    }
}
