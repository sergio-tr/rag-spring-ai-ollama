package com.uniovi.rag.interfaces.rest.dto.knowledge;

import com.uniovi.rag.domain.knowledge.IndexSnapshotStatus;
import com.uniovi.rag.domain.knowledge.KnowledgeIndexSnapshot;
import com.uniovi.rag.domain.knowledge.KnowledgeSnapshotScopeType;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record KnowledgeActiveSnapshotResponse(
        UUID id,
        String signatureHash,
        KnowledgeSnapshotScopeType scopeType,
        IndexSnapshotStatus status,
        String indexProfileHash,
        Map<String, Object> indexProfile,
        Instant createdAt,
        Instant updatedAt
) {
    public static KnowledgeActiveSnapshotResponse fromDomain(KnowledgeIndexSnapshot s) {
        return new KnowledgeActiveSnapshotResponse(
                s.id(),
                s.signatureHash(),
                s.scopeType(),
                s.status(),
                s.indexProfileHash(),
                s.indexProfileJsonb() != null ? s.indexProfileJsonb() : Map.of(),
                s.createdAt(),
                s.updatedAt());
    }
}

