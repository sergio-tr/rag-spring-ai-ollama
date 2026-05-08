package com.uniovi.rag.service.evaluation.preset;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Serializable index-aware execution plan for Lab RAG preset benchmarks (P0–P14).
 */
public final class LabPresetRunPlanModels {

    private LabPresetRunPlanModels() {}

    /** Version tag for deterministic thesis run-plan semantics (grouping + compatibility rules). */
    public static final int STRATEGY_VERSION = 1;

    public record LabPresetRunPlan(
            List<LabPresetRunGroup> groups,
            List<LabPresetRunPlanItem> items,
            List<String> requestedPresetCodes,
            List<String> executablePresetCodes,
            Map<String, String> skippedPresetCodes,
            UUID resolvedSnapshotId,
            String resolvedIndexProfileHash,
            boolean hasActiveSnapshot,
            int strategyVersion,
            Instant createdAt
    ) {
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("groups", groups.stream().map(LabPresetRunGroup::toMap).toList());
            m.put("items", items.stream().map(LabPresetRunPlanItem::toMap).toList());
            m.put("requestedPresetCodes", requestedPresetCodes);
            m.put("executablePresetCodes", executablePresetCodes);
            m.put("skippedPresetCodes", skippedPresetCodes);
            m.put("resolvedSnapshotId", resolvedSnapshotId != null ? resolvedSnapshotId.toString() : null);
            m.put("resolvedIndexProfileHash", resolvedIndexProfileHash);
            m.put("hasActiveSnapshot", hasActiveSnapshot);
            m.put("strategyVersion", strategyVersion);
            m.put("createdAt", createdAt != null ? createdAt.toString() : null);
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
            String compatibilityStatus,
            String reasonCode,
            String reason,
            String reindexAction,
            String reindexStatus,
            UUID groupSnapshotId,
            String groupIndexProfileHash,
            UUID reindexEventId,
            Instant startedAt,
            Instant completedAt,
            String errorCode,
            String errorReason
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
            m.put("compatibilityStatus", compatibilityStatus);
            m.put("reasonCode", reasonCode);
            m.put("reason", reason);
            m.put("reindexAction", reindexAction);
            m.put("reindexStatus", reindexStatus);
            m.put("groupSnapshotId", groupSnapshotId != null ? groupSnapshotId.toString() : null);
            m.put("groupIndexProfileHash", groupIndexProfileHash);
            m.put("reindexEventId", reindexEventId != null ? reindexEventId.toString() : null);
            m.put("startedAt", startedAt != null ? startedAt.toString() : null);
            m.put("completedAt", completedAt != null ? completedAt.toString() : null);
            m.put("errorCode", errorCode);
            m.put("errorReason", errorReason);
            return m;
        }
    }

    public record LabPresetRunPlanItem(
            String presetCode,
            LabPresetRunGroupKey groupKey,
            boolean labSelectable,
            boolean requiresMultiTurn,
            Map<String, Object> presetIndexRequirements,
            boolean compatible,
            boolean requiresReindex,
            String indexCompatibilityStatus,
            String reasonCode,
            String reason,
            UUID resolvedSnapshotId,
            String resolvedIndexProfileHash,
            Map<String, Object> activeSnapshotCapabilities,
            int runPlanVersion
    ) {
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("presetCode", presetCode);
            m.put("groupKey", groupKey != null ? groupKey.name() : null);
            m.put("labSelectable", labSelectable);
            m.put("requiresMultiTurn", requiresMultiTurn);
            m.put("presetIndexRequirements", presetIndexRequirements);
            m.put("compatible", compatible);
            m.put("requiresReindex", requiresReindex);
            m.put("indexCompatibilityStatus", indexCompatibilityStatus);
            m.put("reasonCode", reasonCode);
            m.put("reason", reason);
            m.put("resolvedSnapshotId", resolvedSnapshotId != null ? resolvedSnapshotId.toString() : null);
            m.put("resolvedIndexProfileHash", resolvedIndexProfileHash);
            m.put("activeSnapshotCapabilities", activeSnapshotCapabilities);
            m.put("runPlanVersion", runPlanVersion);
            return m;
        }
    }
}
