package com.uniovi.rag.application.service.runtime.llm;

import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.engine.RuntimeOperationKind;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;

/** Caps interactive RAG LLM timeouts for demo presets and chat turns. */
public final class RagLlmTimeoutPolicy {

    private static final Logger log = LoggerFactory.getLogger(RagLlmTimeoutPolicy.class);

    static final int INTERACTIVE_PRIMARY_CAP_MS = 20_000;
    static final int INTERACTIVE_SECONDARY_CAP_MS = 9_000;

    private RagLlmTimeoutPolicy() {}

    public static int effectiveTimeoutMs(
            @Nullable ExecutionContext ctx, String modelRole, @Nullable Integer configuredTimeoutMs) {
        if (!appliesInteractiveCap(ctx)) {
            return positiveOrDefault(configuredTimeoutMs, INTERACTIVE_PRIMARY_CAP_MS);
        }
        int cap = isPrimaryRole(modelRole) ? INTERACTIVE_PRIMARY_CAP_MS : INTERACTIVE_SECONDARY_CAP_MS;
        int configured = positiveOrDefault(configuredTimeoutMs, cap);
        int effective = Math.min(configured, cap);
        logPolicy(ctx, modelRole, configured, cap, effective);
        return effective;
    }

    private static boolean appliesInteractiveCap(@Nullable ExecutionContext ctx) {
        return ctx != null && ctx.operationKind() == RuntimeOperationKind.CHAT_MESSAGE;
    }

    private static boolean isPrimaryRole(String modelRole) {
        return modelRole != null && "PRIMARY".equalsIgnoreCase(modelRole.trim());
    }

    private static int positiveOrDefault(@Nullable Integer value, int defaultMs) {
        return value != null && value > 0 ? value : defaultMs;
    }

    private static void logPolicy(
            @Nullable ExecutionContext ctx,
            String modelRole,
            int configuredMs,
            int capMs,
            int effectiveMs) {
        log.info(
                "RAG_LLM_TIMEOUT_POLICY traceId={} conversationId={} presetId={} modelRole={} configuredMs={} capMs={} effectiveMs={}",
                traceId(ctx),
                conversationId(ctx),
                presetId(ctx),
                modelRole != null ? modelRole : "",
                configuredMs,
                capMs,
                effectiveMs);
    }

    private static String traceId(@Nullable ExecutionContext ctx) {
        return ctx != null && ctx.correlationId() != null ? ctx.correlationId() : "";
    }

    private static String conversationId(@Nullable ExecutionContext ctx) {
        return ctx != null && ctx.conversationId() != null ? ctx.conversationId().toString() : "";
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
