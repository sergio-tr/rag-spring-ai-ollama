package com.uniovi.rag.application.port;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Optional;
import java.util.UUID;

/**
 * Loads {@code conversations.runtime_override_jsonb} as JSON for resolution (load-only port).
 */
public interface ConversationRuntimeOverrideLoader {

    /**
     * Returns empty when the conversation is missing, not owned by {@code userId}, or override is empty.
     */
    Optional<JsonNode> loadRuntimeOverride(UUID userId, UUID conversationId);
}
