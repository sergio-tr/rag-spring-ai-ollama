package com.uniovi.rag.application.service.runtime.config;

import com.uniovi.rag.service.evaluation.preset.ExperimentalPresetCanonicalCatalog;

public record IndexCompatibilityResult(
        boolean compatible,
        boolean requiresReindex,
        String status,
        String reasonCode,
        String message
) {
    public static IndexCompatibilityResult ok() {
        return new IndexCompatibilityResult(true, false, "COMPATIBLE", null, null);
    }

    public static IndexCompatibilityResult noActiveIndex(String message) {
        return new IndexCompatibilityResult(false, true, "NO_ACTIVE_INDEX", "NO_ACTIVE_INDEX", message);
    }

    public static IndexCompatibilityResult requiresReindex(String reasonCode, String message) {
        return new IndexCompatibilityResult(false, true, "REQUIRES_REINDEX", reasonCode, message);
    }

    public static IndexCompatibilityResult check(
            ExperimentalPresetCanonicalCatalog.IndexRequirements req,
            boolean hasActiveIndex,
            IndexSnapshotCapabilities snap) {
        if (req == null || req.requiredMaterialization() == null) {
            return IndexCompatibilityResult.ok();
        }
        if (req.requiredMaterialization() == ExperimentalPresetCanonicalCatalog.RequiredMaterialization.NONE) {
            return IndexCompatibilityResult.ok();
        }
        if (!hasActiveIndex) {
            return noActiveIndex("No active index snapshot. This preset requires an indexed corpus; reindex the project.");
        }
        String snapStrategy = snap != null ? snap.materializationStrategy() : null;
        if (snapStrategy == null || snapStrategy.isBlank()) {
            return requiresReindex("INDEX_CAPABILITY_MISSING", "Active index snapshot is missing materializationStrategy; reindex the project.");
        }

        boolean strategyOk = satisfiesStrategy(req.requiredMaterialization(), snapStrategy);
        if (!strategyOk) {
            return requiresReindex(
                    "MATERIALIZATION_NOT_SUPPORTED",
                    "Preset requires " + req.requiredMaterialization().name()
                            + " but active snapshot is " + snapStrategy + ". Reindex is required.");
        }

        if (req.requiresMetadataSupport()) {
            Boolean supportsMetadata = snap != null ? snap.supportsMetadata() : null;
            if (supportsMetadata == null || !supportsMetadata) {
                return requiresReindex(
                        "METADATA_SUPPORT_REQUIRED",
                        "Preset requires index metadata support but active snapshot does not support metadata. Reindex is required.");
            }
        }

        return IndexCompatibilityResult.ok();
    }

    private static boolean satisfiesStrategy(
            ExperimentalPresetCanonicalCatalog.RequiredMaterialization required,
            String snapshotStrategyRaw) {
        String s = snapshotStrategyRaw.trim().toUpperCase();
        if ("HYBRID".equals(s)) {
            return true;
        }
        return switch (required) {
            case DOCUMENT_LEVEL -> "DOCUMENT_LEVEL".equals(s);
            case CHUNK_LEVEL -> "CHUNK_LEVEL".equals(s);
            case HYBRID -> "HYBRID".equals(s);
            case NONE -> true;
        };
    }
}

