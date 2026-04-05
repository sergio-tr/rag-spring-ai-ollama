package com.uniovi.rag.application.port;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Loads raw JSON configuration layers for the RAG cascade (hexagonal port).
 * Implementations live in infrastructure (e.g. JPA).
 */
public interface ConfigurationSourcePort {

    Optional<Map<String, Object>> loadSystemDefaults();

    Optional<Map<String, Object>> loadUserDefault(UUID userId);

    Optional<Map<String, Object>> loadProject(UUID userId, UUID projectId);

    /**
     * Raw {@code rag_preset.values} and ordered {@code config_profile.payload} maps for {@code presetId}.
     * Does not merge layers; visibility matches preset rules for {@code userId}.
     */
    Optional<PresetProfileCompositionSources> loadPresetProfileCompositionSources(UUID userId, UUID presetId);
}
