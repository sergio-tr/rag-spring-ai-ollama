package com.uniovi.rag.interfaces.rest.dto;

import java.util.List;
import java.util.Map;

public record ExperimentalPresetCatalogItemDto(
        String productPresetId,
        String code,
        String family,
        String label,
        String description,
        RuntimePresetIndexRequirementsDto indexRequirements,
        List<String> requiredCapabilities,
        boolean supported,
        String supportStatus,
        String reasonIfUnsupported,
        boolean requiresMultiTurn,
        Map<String, Object> mapsToRuntimeCapabilities,
        List<String> allowedOutcomes,
        boolean chatSelectable,
        boolean labSelectable,
        boolean labOnly) {}
