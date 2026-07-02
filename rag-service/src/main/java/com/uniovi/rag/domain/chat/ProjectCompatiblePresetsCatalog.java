package com.uniovi.rag.domain.chat;

import java.util.List;
import java.util.UUID;

public record ProjectCompatiblePresetsCatalog(
        UUID projectId,
        String effectiveEmbeddingModelId,
        boolean hasActiveIndex,
        long readyDocumentCount,
        RuntimeSnapshotCapabilities activeSnapshotCapabilities,
        List<CompatibleProductPreset> productPresets,
        List<CompatibleExperimentalPreset> experimentalPresets) {}
