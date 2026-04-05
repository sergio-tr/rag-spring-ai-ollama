package com.uniovi.rag.domain.config.runtime;

import java.util.List;
import java.util.UUID;

/**
 * Traceability for which persisted rows participated in resolution.
 */
public record ConfigProvenance(
        UUID systemConfigurationRowId,
        UUID userConfigurationId,
        UUID projectConfigurationId,
        List<UUID> profileIds,
        UUID presetId,
        UUID snapshotId
) {
}
