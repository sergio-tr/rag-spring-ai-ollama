package com.uniovi.rag.domain.knowledge;

import java.time.Instant;
import java.util.UUID;

/**
 * API-stable view of a row in {@code knowledge_index_snapshot} (not JPA; map via
 * {@link com.uniovi.rag.infrastructure.persistence.mapper.KnowledgeIndexSnapshotMapper}).
 */
public record KnowledgeIndexSnapshot(
        UUID id,
        String signatureHash,
        KnowledgeSnapshotScopeType scopeType,
        UUID projectId,
        UUID conversationId,
        IndexSnapshotStatus status,
        UUID resolvedConfigSnapshotId,
        String resolvedConfigHash,
        Instant createdAt,
        Instant updatedAt) {}
