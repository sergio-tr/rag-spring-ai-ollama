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
        boolean labOnly,
        boolean corpusRequired,
        boolean requiresSnapshot,
        boolean requiresProjectDocuments,
        boolean singleTurnBenchmarkSelectable,
        /** Ordinal stage P0=0 … P14=14 (thesis ladder). */
        int protocolStageIndex,
        /** Parent preset code in the cumulative ladder, or null for P0. */
        String parentPresetCode,
        /** Canonical terminal runtime JSON applied in Lab (same keys as Chat {@code rag_preset.values}). */
        String effectiveTerminalRuntimeJson) {}
