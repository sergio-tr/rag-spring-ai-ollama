package com.uniovi.rag.application.port;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Raw preset and profile payloads for {@link ConfigurationSourcePort#loadPresetProfileCompositionSources(UUID, UUID)}.
 * No merge is applied; {@link com.uniovi.rag.domain.config.PresetProfilePayloadMerge} runs only in {@code ConfigResolver}.
 */
public record PresetProfileCompositionSources(
        Map<String, Object> presetValues, List<Map<String, Object>> orderedProfilePayloads, List<UUID> profileIds) {

    public PresetProfileCompositionSources {
        presetValues = presetValues != null ? Map.copyOf(presetValues) : Map.of();
        orderedProfilePayloads =
                orderedProfilePayloads != null ? List.copyOf(orderedProfilePayloads) : List.of();
        profileIds = profileIds != null ? List.copyOf(profileIds) : List.of();
    }
}
