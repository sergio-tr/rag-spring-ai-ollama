package com.uniovi.rag.interfaces.rest.dto;

import java.util.List;
import java.util.UUID;

/**
 * Project-scoped preset catalog with index compatibility for Chat preset selectors.
 */
public record ProjectCompatiblePresetsDto(
        UUID projectId,
        String effectiveEmbeddingModelId,
        boolean hasActiveIndex,
        long readyDocumentCount,
        RuntimeSnapshotCapabilitiesDto activeSnapshotCapabilities,
        List<CompatibleProductPresetDto> productPresets,
        List<CompatibleExperimentalPresetDto> experimentalPresets
) {}
