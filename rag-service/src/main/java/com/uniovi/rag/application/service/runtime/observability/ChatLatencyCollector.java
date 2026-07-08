package com.uniovi.rag.application.service.runtime.observability;

import com.uniovi.rag.application.service.runtime.optimization.RagLlmCallBudgetEnforcer;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.lang.Nullable;

/** Per-turn latency accumulator for unified {@code CHAT_LATENCY} summary logs. */
public final class ChatLatencyCollector {

    private static final ThreadLocal<Mutable> CURRENT = new ThreadLocal<>();

    private ChatLatencyCollector() {}

    public static void bind(@Nullable ExecutionContext ctx) {
        if (ctx == null) {
            CURRENT.remove();
            return;
        }
        CURRENT.set(new Mutable(ctx));
    }

    public static void clear() {
        CURRENT.remove();
    }

    public static void recordStage(String stage, long durationMs) {
        Mutable state = CURRENT.get();
        if (state == null || stage == null || durationMs < 0) {
            return;
        }
        state.recordStage(stage.trim().toLowerCase(Locale.ROOT), durationMs);
    }

    public static Map<String, Object> buildSummaryFields(
            long totalMs,
            long persistenceMs,
            long sseDispatchMs,
            long queueWaitMs,
            @Nullable Map<String, Object> chatTelemetry) {
        Mutable state = CURRENT.get();
        RagLlmCallBudgetEnforcer.Snapshot budget =
                state != null ? state.budgetSnapshot() : RagLlmCallBudgetEnforcer.snapshot();
        Map<String, Object> fields = new LinkedHashMap<>();
        if (state != null) {
            fields.putAll(state.stageFields());
            state.applyContextFields(fields);
        }
        fields.put("totalMs", totalMs);
        fields.put("queueWaitMs", queueWaitMs);
        fields.put("persistenceMs", persistenceMs);
        fields.put("sseDispatchMs", sseDispatchMs);
        fields.put("llmCallCount", budget.usedTotal());
        fields.put("secondaryLlmCallCount", budget.usedSecondary());
        fields.put("budgetExceeded", budget.budgetExceeded());
        if (chatTelemetry != null) {
            if (chatTelemetry.get("contextChars") != null) {
                fields.put("contextChars", chatTelemetry.get("contextChars"));
            }
            if (chatTelemetry.get("retrievedChunks") != null) {
                fields.put("retrievedChunks", chatTelemetry.get("retrievedChunks"));
            }
            if (chatTelemetry.get("selectedChunks") != null) {
                fields.put("selectedChunks", chatTelemetry.get("selectedChunks"));
            }
            Object abstention = chatTelemetry.get("abstentionTriggered");
            if (abstention == null) {
                abstention = chatTelemetry.get("judgeFinalOutcome");
            }
            fields.put(
                    "abstentionTriggered",
                    Boolean.TRUE.equals(abstention)
                            || "REJECTED_NO_RETRY".equals(String.valueOf(abstention)));
        }
        return fields;
    }

    private static final class Mutable {
        private final ExecutionContext ctx;
        private long queryUnderstandingMs;
        private long condenseMs;
        private long rewriteMs;
        private long retrievalMs;
        private long rankerMs;
        private long contextPackingMs;
        private long primaryAnswerMs;
        private long judgeMs;
        private long executionContextMs;

        Mutable(ExecutionContext ctx) {
            this.ctx = ctx;
        }

        void recordStage(String stage, long durationMs) {
            switch (stage) {
                case "query_understanding" -> queryUnderstandingMs += durationMs;
                case "memory_condense", "conversation-condense" -> condenseMs += durationMs;
                case "query_rewrite", "query-rewrite" -> rewriteMs += durationMs;
                case "retrieval", "retrieval_fusion", "retrieval_hybrid" -> retrievalMs += durationMs;
                case "retrieval_rerank" -> rankerMs += durationMs;
                case "context_pack", "advisor_pack" -> contextPackingMs += durationMs;
                case "primary-answer", "workflow_generation" -> primaryAnswerMs += durationMs;
                case "judge_finalize", "runtime-judge", "answer_quality_advisor" -> judgeMs += durationMs;
                case "execution_context" -> executionContextMs += durationMs;
                default -> {
                    if (stage.contains("retrieval")) {
                        retrievalMs += durationMs;
                    } else if (stage.contains("judge")) {
                        judgeMs += durationMs;
                    } else if (stage.contains("condense") || stage.contains("memory")) {
                        condenseMs += durationMs;
                    } else if (stage.contains("rewrite")) {
                        rewriteMs += durationMs;
                    } else if (stage.contains("pack")) {
                        contextPackingMs += durationMs;
                    }
                }
            }
        }

        Map<String, Object> stageFields() {
            Map<String, Object> m = new LinkedHashMap<>();
            putIfPositive(m, "queryUnderstandingMs", queryUnderstandingMs);
            putIfPositive(m, "condenseMs", condenseMs);
            putIfPositive(m, "rewriteMs", rewriteMs);
            putIfPositive(m, "retrievalMs", retrievalMs);
            putIfPositive(m, "rankerMs", rankerMs);
            putIfPositive(m, "contextPackingMs", contextPackingMs);
            putIfPositive(m, "primaryAnswerMs", primaryAnswerMs);
            putIfPositive(m, "judgeMs", judgeMs);
            putIfPositive(m, "executionContextMs", executionContextMs);
            return m;
        }

        void applyContextFields(Map<String, Object> fields) {
            fields.put("traceId", ctx.correlationId() != null ? ctx.correlationId() : "");
            if (ctx.conversationId() != null) {
                fields.put("conversationId", ctx.conversationId().toString());
            }
            if (ctx.resolved() != null && ctx.resolved().provenance() != null) {
                if (ctx.resolved().provenance().presetId() != null) {
                    fields.put("presetId", ctx.resolved().provenance().presetId().toString());
                }
            }
            if (ctx.resolved() != null) {
                fields.put(
                        "indexType",
                        ctx.resolved().toRagConfig().materializationStrategy() != null
                                ? ctx.resolved().toRagConfig().materializationStrategy().name()
                                : "");
            }
        }

        RagLlmCallBudgetEnforcer.Snapshot budgetSnapshot() {
            return RagLlmCallBudgetEnforcer.snapshot();
        }

        private static void putIfPositive(Map<String, Object> m, String key, long value) {
            if (value > 0) {
                m.put(key, value);
            }
        }
    }
}
