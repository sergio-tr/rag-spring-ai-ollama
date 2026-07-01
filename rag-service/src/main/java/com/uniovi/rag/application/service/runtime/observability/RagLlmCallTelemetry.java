package com.uniovi.rag.application.service.runtime.observability;

import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;

/** Structured, secret-safe LLM call telemetry for RAG runtime audits. */
public final class RagLlmCallTelemetry {

    private static final Logger log = LoggerFactory.getLogger(RagLlmCallTelemetry.class);

    private RagLlmCallTelemetry() {}

    public static void logStarted(
            @Nullable ExecutionContext ctx,
            String callName,
            LlmProvider provider,
            String model,
            int inputApproxChars,
            int contextApproxChars,
            @Nullable Integer retrievedChunkCount,
            @Nullable Double temperature) {
        log.info(
                "RAG_LLM_CALL traceId={} conversationId={} presetId={} callName={} model={} modelRole={} provider={} inputChars={} contextChars={} retrievedChunks={} temperature={} phase=started",
                traceId(ctx),
                conversationId(ctx),
                presetId(ctx),
                safe(callName),
                safe(model),
                modelRole(callName),
                provider,
                Math.max(0, inputApproxChars),
                Math.max(0, contextApproxChars),
                retrievedChunkCount != null ? retrievedChunkCount : -1,
                temperature != null ? temperature : "default");
    }

    public static void logCompleted(
            @Nullable ExecutionContext ctx,
            String callName,
            LlmProvider provider,
            String model,
            int inputApproxChars,
            int contextApproxChars,
            @Nullable Integer retrievedChunkCount,
            long latencyMs,
            String status) {
        log.info(
                "RAG_LLM_CALL traceId={} conversationId={} presetId={} callName={} model={} modelRole={} provider={} inputChars={} contextChars={} retrievedChunks={} latencyMs={} status={}",
                traceId(ctx),
                conversationId(ctx),
                presetId(ctx),
                safe(callName),
                safe(model),
                modelRole(callName),
                provider,
                Math.max(0, inputApproxChars),
                Math.max(0, contextApproxChars),
                retrievedChunkCount != null ? retrievedChunkCount : -1,
                latencyMs,
                safe(status));
    }

    public static void logFailed(
            @Nullable ExecutionContext ctx,
            String callName,
            LlmProvider provider,
            String model,
            int inputApproxChars,
            int contextApproxChars,
            long latencyMs,
            String errorType,
            String publicMessage) {
        log.warn(
                "RAG_LLM_CALL traceId={} conversationId={} presetId={} callName={} model={} modelRole={} provider={} inputChars={} contextChars={} latencyMs={} status=FAILED errorType={} message={}",
                traceId(ctx),
                conversationId(ctx),
                presetId(ctx),
                safe(callName),
                safe(model),
                modelRole(callName),
                provider,
                Math.max(0, inputApproxChars),
                Math.max(0, contextApproxChars),
                latencyMs,
                safe(errorType),
                publicMessage != null ? publicMessage : "");
    }

    public static int approxChars(@Nullable String text) {
        return text == null ? 0 : text.length();
    }

    private static String traceId(@Nullable ExecutionContext ctx) {
        if (ctx == null || ctx.correlationId() == null || ctx.correlationId().isBlank()) {
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

    private static String safe(@Nullable String value) {
        return value == null ? "" : value.trim();
    }

    private static String modelRole(String callName) {
        if (callName == null) {
            return "SECONDARY";
        }
        String op = callName.trim().toLowerCase();
        if ("primary-answer".equals(op) || "function-calling".equals(op) || "final-answer".equals(op)) {
            return "PRIMARY";
        }
        return "SECONDARY";
    }

    public static Optional<UUID> presetUuid(@Nullable ExecutionContext ctx) {
        if (ctx == null
                || ctx.resolved() == null
                || ctx.resolved().provenance() == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(ctx.resolved().provenance().presetId());
    }
}
