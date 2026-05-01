package com.uniovi.rag.application.model;

import java.util.List;
import java.util.UUID;

/**
 * Immutable context for SSE chat streaming after the user message has been persisted (application layer).
 */
public record StreamConversationContext(
        UUID conversationId, UUID userId, UUID projectId, List<String> documentFilter) {}
