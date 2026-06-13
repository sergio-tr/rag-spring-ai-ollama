package com.uniovi.rag.application.service.evaluation;

import com.uniovi.rag.interfaces.rest.dto.evaluation.EvaluationCorpusReadinessDto;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Serializes and copies {@code evaluation_run.aggregates_json.corpusReadiness} without null map values.
 *
 * <p>Null blocker fields are valid domain state (runnable corpus); {@link Map#copyOf(Map)} rejects them, which caused
 * async progress emission to throw {@link NullPointerException}.
 */
public final class LabCorpusReadinessAggregates {

    public static final String AGG_KEY = "corpusReadiness";

    private LabCorpusReadinessAggregates() {}

    public static Map<String, Object> toSnapshot(UUID corpusId, EvaluationCorpusReadinessDto readiness) {
        if (corpusId == null || readiness == null) {
            return Map.of();
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("corpusId", corpusId.toString());
        snapshot.put("documentCount", readiness.documentCount());
        snapshot.put("readyCount", readiness.readyCount());
        snapshot.put("storageReadyCount", readiness.storageReadyCount());
        snapshot.put("processingCount", readiness.processingCount());
        snapshot.put("failedCount", readiness.failedCount());
        putIfNotNull(snapshot, "primaryBlocker", readiness.primaryBlocker());
        putIfNotNull(snapshot, "snapshotBlocker", readiness.snapshotBlocker());
        putIfNotNull(snapshot, "snapshotBlockerDetailCode", readiness.snapshotBlockerDetailCode());
        snapshot.put("reindexRequired", readiness.reindexRequired());
        List<String> snapshotIds =
                readiness.selectedSnapshotIds() != null
                        ? readiness.selectedSnapshotIds().stream().map(UUID::toString).toList()
                        : List.of();
        snapshot.put("selectedSnapshotIds", snapshotIds);
        return Collections.unmodifiableMap(snapshot);
    }

    /**
     * Null-safe copy for progress emission. Omits null-valued keys (including legacy rows written before write-side
     * normalization).
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> copyFromAggregates(Map<String, Object> aggregatesJson) {
        if (aggregatesJson == null || aggregatesJson.isEmpty()) {
            return Map.of();
        }
        Object raw = aggregatesJson.get(AGG_KEY);
        if (!(raw instanceof Map<?, ?> map) || map.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : map.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            out.put(String.valueOf(e.getKey()), e.getValue());
        }
        return out.isEmpty() ? Map.of() : Collections.unmodifiableMap(out);
    }

    /** Verifies the snapshot can be passed to {@link Map#copyOf(Map)} (regression guard). */
    public static void assertCopyOfSafe(Map<String, Object> snapshot) {
        if (snapshot == null || snapshot.isEmpty()) {
            return;
        }
        Map.copyOf(snapshot);
    }

    private static void putIfNotNull(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }
}
