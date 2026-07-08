package com.uniovi.rag.application.service.config;

import com.uniovi.rag.infrastructure.persistence.RagPresetRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.RagPresetEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Deterministic default for conversation-level chat presets when none is persisted.
 *
 * <p>The UUID matches seeded {@code Demo_Best} in {@code V18__demo_rag_presets.sql}. This keeps retrieval enabled
 * by default for project-scoped chat when no preset is persisted.
 */
@Component
public class ChatPresetDefaults {

    /** Stable id from migration {@code V18__demo_rag_presets.sql} - preset name {@code Demo_Best}. */
    public static final UUID DETERMINISTIC_DEFAULT_CHAT_PRESET_ID =
            UUID.fromString("cafe0001-0001-4001-8001-000000000003");

    private final RagPresetRepository ragPresetRepository;

    public ChatPresetDefaults(RagPresetRepository ragPresetRepository) {
        this.ragPresetRepository = ragPresetRepository;
    }

    /** Returns persisted Demo_Best entity when present. */
    public Optional<RagPresetEntity> loadDeterministicDefaultPreset() {
        return ragPresetRepository.findById(DETERMINISTIC_DEFAULT_CHAT_PRESET_ID);
    }

    /** Preset id for API responses: persisted FK when set, otherwise deterministic default id for UX/consistency. */
    public UUID effectivePresetIdForApi(UUID persistedPresetId) {
        return persistedPresetId != null ? persistedPresetId : DETERMINISTIC_DEFAULT_CHAT_PRESET_ID;
    }
}
