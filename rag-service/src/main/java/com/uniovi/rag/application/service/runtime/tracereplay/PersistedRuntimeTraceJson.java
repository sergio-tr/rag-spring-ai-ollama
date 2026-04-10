package com.uniovi.rag.application.service.runtime.tracereplay;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Read-only projection helpers for fields embedded in {@code runtime_execution_trace.execution_trace_jsonb} (P15/P16).
 */
final class PersistedRuntimeTraceJson {

    private PersistedRuntimeTraceJson() {
    }

    static List<UUID> readUsedKnowledgeSnapshotIds(Map<String, Object> executionTraceJson) {
        if (executionTraceJson == null || executionTraceJson.isEmpty()) {
            return List.of();
        }
        Object raw = executionTraceJson.get("usedKnowledgeSnapshotIds");
        return parseUuidList(raw);
    }

    static String readDeterministicToolKind(Map<String, Object> executionTraceJson) {
        if (executionTraceJson == null || executionTraceJson.isEmpty()) {
            return "";
        }
        Object raw = executionTraceJson.get("deterministicToolKind");
        return raw == null ? "" : raw.toString().trim();
    }

    static List<UUID> parseUuidList(Object raw) {
        if (raw == null) {
            return List.of();
        }
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<UUID> out = new ArrayList<>();
        for (Object o : list) {
            if (o == null) {
                continue;
            }
            try {
                out.add(UUID.fromString(o.toString()));
            } catch (IllegalArgumentException ignored) {
                // skip invalid entries
            }
        }
        return List.copyOf(out);
    }
}
