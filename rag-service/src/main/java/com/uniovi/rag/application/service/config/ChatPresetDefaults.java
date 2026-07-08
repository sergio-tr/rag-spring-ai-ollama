package com.uniovi.rag.application.service.config;

import com.uniovi.rag.infrastructure.persistence.RagPresetRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.RagPresetEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Deterministic default for conversation-level chat presets when none is persisted.
 *
 * <p>The UUID matches seeded {@code P3} (chunk-level dense retrieval) in {@code V44__tfg_experimental_presets_p0_p14.sql}.
 * RAG-3 recommended P3 as the production default; Demo_Best remains in the catalog for manual selection.
 */
@Component
public class ChatPresetDefaults {

    /** Stable id from migration {@code V44} - experimental preset code {@code P3}. */
    public static final UUID DETERMINISTIC_DEFAULT_CHAT_PRESET_ID =
            UUID.fromString("cafe0001-0001-4001-8001-000000000013");

    private final RagPresetRepository ragPresetRepository;

    public ChatPresetDefaults(RagPresetRepository ragPresetRepository) {
        this.ragPresetRepository = ragPresetRepository;
    }

    /** Returns persisted P3 default preset entity when present. */
    public Optional<RagPresetEntity> loadDeterministicDefaultPreset() {
        return ragPresetRepository.findById(DETERMINISTIC_DEFAULT_CHAT_PRESET_ID);
    }

    /** Preset id for API responses: persisted FK when set, otherwise deterministic default id for UX/consistency. */
    public UUID effectivePresetIdForApi(UUID persistedPresetId) {
        return persistedPresetId != null ? persistedPresetId : DETERMINISTIC_DEFAULT_CHAT_PRESET_ID;
    }
}
