package com.uniovi.rag.domain.knowledge;

import java.time.Instant;
import java.util.UUID;

/**
 * Audit view of a row in {@code reindex_event} (map via
 * {@link com.uniovi.rag.infrastructure.persistence.mapper.ReindexEventMapper}).
 */
public record ReindexEvent(
        UUID id,
        UUID documentId,
        UUID projectId,
        UUID conversationId,
        String reason,
        String targetSignatureHash,
        ReindexEventStatus status,
        UUID asyncTaskId,
        Instant createdAt,
        Instant updatedAt) {}
