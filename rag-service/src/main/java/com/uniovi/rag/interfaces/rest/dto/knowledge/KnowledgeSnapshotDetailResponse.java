package com.uniovi.rag.interfaces.rest.dto.knowledge;

import com.uniovi.rag.domain.knowledge.IndexSnapshotStatus;
import com.uniovi.rag.domain.knowledge.KnowledgeIndexSnapshot;
import com.uniovi.rag.domain.knowledge.KnowledgeSnapshotScopeType;

import java.time.Instant;
import java.util.UUID;

public record KnowledgeSnapshotDetailResponse(
        UUID id,
        String signatureHash,
        KnowledgeSnapshotScopeType scopeType,
        IndexSnapshotStatus status,
        long documentCount,
        Instant createdAt,
        Instant updatedAt,
        UUID resolvedConfigSnapshotId,
        String resolvedConfigHash) {

    public static KnowledgeSnapshotDetailResponse fromDomain(KnowledgeIndexSnapshot k, long documentCount) {
        return new KnowledgeSnapshotDetailResponse(
                k.id(),
                k.signatureHash(),
                k.scopeType(),
                k.status(),
                documentCount,
                k.createdAt(),
                k.updatedAt(),
                k.resolvedConfigSnapshotId(),
                k.resolvedConfigHash());
    }
}
