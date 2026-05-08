package com.uniovi.rag.service.evaluation.preset;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Serializable index-aware execution plan for Lab RAG preset benchmarks (P0–P14).
 */
public final class LabPresetRunPlanModels {

    private LabPresetRunPlanModels() {}

    public record LabPresetRunPlan(
            List<LabPresetRunGroup> groups,
            List<String> requestedPresetCodes,
            List<String> executablePresetCodes,
            Map<String, String> skippedPresetCodes,
            UUID resolvedSnapshotId,
            String resolvedIndexProfileHash,
            boolean hasActiveSnapshot
    ) {
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("groups", groups.stream().map(LabPresetRunGroup::toMap).toList());
            m.put("requestedPresetCodes", requestedPresetCodes);
            m.put("executablePresetCodes", executablePresetCodes);
            m.put("skippedPresetCodes", skippedPresetCodes);
            m.put("resolvedSnapshotId", resolvedSnapshotId != null ? resolvedSnapshotId.toString() : null);
            m.put("resolvedIndexProfileHash", resolvedIndexProfileHash);
            m.put("hasActiveSnapshot", hasActiveSnapshot);
            return m;
        }
    }

    public record LabPresetRunGroup(
            LabPresetRunGroupKey groupKey,
            List<String> presetCodes,
            Map<String, Object> aggregateIndexRequirements,
            Map<String, Object> activeSnapshotCapabilities,
            UUID compatibleSnapshotId,
            boolean compatible,
            boolean requiresReindex,
            String reasonCode,
            String reason
    ) {
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("groupKey", groupKey.name());
            m.put("presetCodes", presetCodes);
            m.put("aggregateIndexRequirements", aggregateIndexRequirements);
            m.put("activeSnapshotCapabilities", activeSnapshotCapabilities);
            m.put("compatibleSnapshotId", compatibleSnapshotId != null ? compatibleSnapshotId.toString() : null);
            m.put("compatible", compatible);
            m.put("requiresReindex", requiresReindex);
            m.put("reasonCode", reasonCode);
            m.put("reason", reason);
            return m;
        }
    }
}
