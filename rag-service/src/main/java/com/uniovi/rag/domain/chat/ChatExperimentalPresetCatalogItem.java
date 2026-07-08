package com.uniovi.rag.domain.chat;

import java.util.List;
import java.util.Map;

public record ChatExperimentalPresetCatalogItem(
        String productPresetId,
        String code,
        String family,
        String label,
        String description,
        RuntimePresetIndexRequirements indexRequirements,
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
        int protocolStageIndex,
        String parentPresetCode,
        String effectiveTerminalRuntimeJson) {}
