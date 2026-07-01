package com.uniovi.rag.application.service.runtime.observability;

import java.util.Map;
import java.util.StringJoiner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;

/** Unified chat job latency summary for demo and ops audits. */
public final class ChatLatencyTelemetry {

    private static final Logger log = LoggerFactory.getLogger(ChatLatencyTelemetry.class);

    private ChatLatencyTelemetry() {}

    public static void logSummary(
            @Nullable String jobId,
            @Nullable String model,
            @Nullable String secondaryModel,
            Map<String, Object> fields) {
        if (fields == null || fields.isEmpty()) {
            return;
        }
        StringJoiner joiner = new StringJoiner(" ");
        joiner.add("CHAT_LATENCY");
        append(joiner, "traceId", fields.get("traceId"));
        append(joiner, "jobId", jobId);
        append(joiner, "conversationId", fields.get("conversationId"));
        append(joiner, "presetId", fields.get("presetId"));
        append(joiner, "presetCode", fields.get("presetCode"));
        append(joiner, "model", model);
        append(joiner, "secondaryModel", secondaryModel);
        append(joiner, "indexType", fields.get("indexType"));
        append(joiner, "totalMs", fields.get("totalMs"));
        append(joiner, "queueWaitMs", fields.get("queueWaitMs"));
        append(joiner, "executionContextMs", fields.get("executionContextMs"));
        append(joiner, "queryUnderstandingMs", fields.get("queryUnderstandingMs"));
        append(joiner, "condenseMs", fields.get("condenseMs"));
        append(joiner, "rewriteMs", fields.get("rewriteMs"));
        append(joiner, "retrievalMs", fields.get("retrievalMs"));
        append(joiner, "rankerMs", fields.get("rankerMs"));
        append(joiner, "contextPackingMs", fields.get("contextPackingMs"));
        append(joiner, "primaryAnswerMs", fields.get("primaryAnswerMs"));
        append(joiner, "judgeMs", fields.get("judgeMs"));
        append(joiner, "persistenceMs", fields.get("persistenceMs"));
        append(joiner, "sseDispatchMs", fields.get("sseDispatchMs"));
        append(joiner, "llmCallCount", fields.get("llmCallCount"));
        append(joiner, "secondaryLlmCallCount", fields.get("secondaryLlmCallCount"));
        append(joiner, "contextChars", fields.get("contextChars"));
        append(joiner, "retrievedChunks", fields.get("retrievedChunks"));
        append(joiner, "selectedChunks", fields.get("selectedChunks"));
        append(joiner, "budgetExceeded", fields.get("budgetExceeded"));
        append(joiner, "abstentionTriggered", fields.get("abstentionTriggered"));
        log.info(joiner.toString());
    }

    private static void append(StringJoiner joiner, String key, @Nullable Object value) {
        if (value == null) {
            return;
        }
        String s = String.valueOf(value).trim();
        if (!s.isEmpty()) {
            joiner.add(key + "=" + s);
        }
    }
}
