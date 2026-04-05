package com.uniovi.rag.application.port;

import com.fasterxml.jackson.databind.JsonNode;
import com.uniovi.rag.domain.runtime.RagConfig;

import java.util.UUID;

/**
 * Resolves effective {@link RagConfig} after system → user → project → runtime JSON cascade.
 */
public interface RagConfigurationResolver {

    /**
     * @param userId          optional; when null, only system (+ runtime) layers apply
     * @param projectId       optional; requires userId for project layer
     * @param runtimeOverride optional JSON overriding merged config (chat-level)
     */
    RagConfig resolve(UUID userId, UUID projectId, JsonNode runtimeOverride);
}
