package com.uniovi.rag.application.port;

import com.fasterxml.jackson.databind.JsonNode;
import com.uniovi.rag.domain.runtime.RagConfig;

import java.util.UUID;

/**
 * Resolves effective {@link RagConfig} after system → user → project → optional preset/profile → conversation →
 * request JSON cascade.
 */
public interface RagConfigurationResolver {

    /**
     * @param userId          optional; when null, only system (+ runtime) layers apply
     * @param projectId       optional; requires userId for project layer
     * @param runtimeOverride optional JSON overriding merged config (chat-level)
     */
    RagConfig resolve(UUID userId, UUID projectId, JsonNode runtimeOverride);

    /**
     * @param presetId                    optional persisted preset; when non-null, loads preset/profile maps from DB
     * @param conversationRuntimeOverride optional JSON from {@code conversations.runtime_override_jsonb}
     * @param requestRuntimeOverride      terminal JSON (request body); wins over {@code conversationRuntimeOverride}
     */
    RagConfig resolve(
            UUID userId,
            UUID projectId,
            UUID presetId,
            JsonNode conversationRuntimeOverride,
            JsonNode requestRuntimeOverride);
}
