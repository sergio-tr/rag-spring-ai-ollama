package com.uniovi.rag.infrastructure.observability;

import com.uniovi.rag.application.service.runtime.ChatExecutionTelemetryMapper;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.engine.ExecutionTrace;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Builds privacy-safe Micrometer span attributes from runtime execution context and traces.
 */
public final class RuntimeObservabilityAttributes {

    private RuntimeObservabilityAttributes() {}

    public static Map<String, String> fromExecutionContext(ExecutionContext ctx) {
        if (ctx == null) {
            return Map.of();
        }
        Map<String, String> m = new LinkedHashMap<>();
        putUuid(m, "conversationId", ctx.conversationId());
        putUuid(m, "projectId", ctx.projectId());
        if (ctx.correlationId() != null && !ctx.correlationId().isBlank()) {
            m.put("correlationId", ctx.correlationId());
        }
        if (ctx.resolved() != null && ctx.resolved().provenance() != null && ctx.resolved().provenance().presetId() != null) {
            m.put("presetId", ctx.resolved().provenance().presetId().toString());
        }
        if (ctx.resolved() != null && ctx.resolved().provenance() != null && ctx.resolved().provenance().snapshotId() != null) {
            m.put("snapshotId", ctx.resolved().provenance().snapshotId().toString());
        }
        if (ctx.resolved() != null && ctx.resolved().compatibility() != null) {
            int blocking =
                    ctx.resolved().compatibility().errors() != null
                            ? ctx.resolved().compatibility().errors().size()
                            : 0;
            m.put("blockingIssueCount", String.valueOf(blocking));
        }
        return TelemetryRedaction.safeAttributes(m);
    }

    public static Map<String, String> fromExecutionTrace(ExecutionTrace trace) {
        if (trace == null) {
            return Map.of();
        }
        Map<String, Object> tel = ChatExecutionTelemetryMapper.fromTrace(trace);
        Map<String, String> m = new LinkedHashMap<>();
        copyString(tel, m, "classifierModelId");
        copyString(tel, m, "classifierStatus");
        copyString(tel, m, "classifierFallbackReason");
        copyString(tel, m, "predictedQueryType");
        copyString(tel, m, "workflowName", "workflowFamily");
        copyString(tel, m, "topSourceDate");
        copyString(tel, m, "abstentionReason");
        if (tel.get("sourceCount") != null) {
            m.put("sourceCount", String.valueOf(tel.get("sourceCount")));
        }
        if (tel.get("dateMismatchDetected") != null) {
            m.put("dateMismatch", String.valueOf(tel.get("dateMismatchDetected")));
        }
        if (tel.get("retrievalAfterCompressionCount") != null) {
            m.put("retrievedChunkCount", String.valueOf(tel.get("retrievalAfterCompressionCount")));
        } else if (tel.get("contextChunkCount") != null) {
            m.put("retrievedChunkCount", String.valueOf(tel.get("contextChunkCount")));
        }
        if (trace.promptContextCharCount() > 0) {
            m.put("promptCharCount", String.valueOf(trace.promptContextCharCount()));
        }
        return TelemetryRedaction.safeAttributes(m);
    }

    private static void putUuid(Map<String, String> m, String key, UUID id) {
        if (id != null) {
            m.put(key, id.toString());
        }
    }

    private static void copyString(Map<String, Object> from, Map<String, String> to, String key) {
        copyString(from, to, key, key);
    }

    private static void copyString(Map<String, Object> from, Map<String, String> to, String fromKey, String toKey) {
        Object v = from.get(fromKey);
        if (v != null) {
            String s = String.valueOf(v);
            if (!s.isBlank()) {
                to.put(toKey, s);
            }
        }
    }
}
