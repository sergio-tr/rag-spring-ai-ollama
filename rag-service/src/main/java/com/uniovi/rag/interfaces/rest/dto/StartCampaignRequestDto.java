package com.uniovi.rag.interfaces.rest.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Phase R6: Starts a multi-run evaluation campaign from the Lab UI.
 * <p>
 * The backend will fan out into one or more {@code evaluation_run} child runs depending on the kind.
 */
public record StartCampaignRequestDto(
        String name,
        String campaignKind,
        UUID datasetId,
        UUID corpusId,
        UUID projectId,
        List<String> llmModelIds,
        List<String> embeddingModelIds,
        List<UUID> indexSnapshotIds,
        List<String> experimentalPresetCodes,
        Map<String, Object> baseConfig
) {
    public StartCampaignRequestDto {
        llmModelIds = llmModelIds == null ? List.of() : llmModelIds;
        embeddingModelIds = embeddingModelIds == null ? List.of() : embeddingModelIds;
        indexSnapshotIds = indexSnapshotIds == null ? List.of() : indexSnapshotIds;
        experimentalPresetCodes = experimentalPresetCodes == null ? List.of() : experimentalPresetCodes;
        baseConfig = baseConfig == null ? Map.of() : baseConfig;
    }
}

