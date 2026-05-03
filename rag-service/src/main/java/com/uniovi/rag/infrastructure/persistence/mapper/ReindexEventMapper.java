package com.uniovi.rag.infrastructure.persistence.mapper;

import com.uniovi.rag.domain.knowledge.ReindexEvent;
import com.uniovi.rag.infrastructure.persistence.jpa.ReindexEventEntity;

/**
 * Entity ↔ domain {@link ReindexEvent}.
 */
public final class ReindexEventMapper {

    private ReindexEventMapper() {}

    public static ReindexEvent toDomain(ReindexEventEntity e) {
        return new ReindexEvent(
                e.getId(),
                e.getDocument() != null ? e.getDocument().getId() : null,
                e.getProject() != null ? e.getProject().getId() : null,
                e.getConversation() != null ? e.getConversation().getId() : null,
                e.getReason(),
                e.getTargetSignatureHash(),
                e.getStatus(),
                e.getAsyncTaskId(),
                e.getCreatedAt(),
                e.getUpdatedAt());
    }
}
