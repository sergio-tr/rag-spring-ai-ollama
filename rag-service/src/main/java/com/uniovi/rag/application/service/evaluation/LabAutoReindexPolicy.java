package com.uniovi.rag.application.service.evaluation;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Explicit, reproducible policy for controlled Lab auto-reindex behavior.
 *
 * <p>This is intentionally conservative: in the current system snapshot builds mutate the ACTIVE snapshot
 * and delete vectors per document, so auto-reindex must be explicitly opted-in by the user.
 */
public record LabAutoReindexPolicy(
        boolean enabled,
        boolean allowActiveSnapshotMutation,
        boolean reuseCompatibleActiveSnapshot,
        boolean failOnReindexFailure) {

    public static LabAutoReindexPolicy disabled() {
        return new LabAutoReindexPolicy(false, false, true, true);
    }

    public static LabAutoReindexPolicy fromRequest(StartBenchmarkRunRequest req) {
        if (req == null || !req.autoReindexEffective()) {
            return disabled();
        }
        return new LabAutoReindexPolicy(
                true,
                req.allowActiveSnapshotMutationEffective(),
                req.reuseCompatibleActiveSnapshotEffective(),
                req.failOnReindexFailureEffective());
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enabled", enabled);
        m.put("allowActiveSnapshotMutation", allowActiveSnapshotMutation);
        m.put("reuseCompatibleActiveSnapshot", reuseCompatibleActiveSnapshot);
        m.put("failOnReindexFailure", failOnReindexFailure);
        return m;
    }
}

