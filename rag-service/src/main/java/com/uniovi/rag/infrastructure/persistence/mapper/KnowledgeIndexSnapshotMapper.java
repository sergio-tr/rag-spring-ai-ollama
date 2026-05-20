package com.uniovi.rag.infrastructure.persistence.mapper;

import com.uniovi.rag.domain.knowledge.KnowledgeIndexSnapshot;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeIndexSnapshotEntity;

/**
 * Entity ↔ domain {@link KnowledgeIndexSnapshot} (read path for API mapping).
 */
public final class KnowledgeIndexSnapshotMapper {

    private KnowledgeIndexSnapshotMapper() {}

    public static KnowledgeIndexSnapshot toDomain(KnowledgeIndexSnapshotEntity e) {
        return new KnowledgeIndexSnapshot(
                e.getId(),
                e.getSignatureHash(),
                e.getScopeType(),
                e.getProject().getId(),
                e.getConversation() != null ? e.getConversation().getId() : null,
                e.getStatus(),
                e.getResolvedConfigSnapshotId(),
                e.getResolvedConfigHash(),
                e.getIndexProfileJsonb(),
                e.getIndexProfileHash(),
                e.getEmbeddingDimensions(),
                e.getCreatedAt(),
                e.getUpdatedAt());
    }
}
