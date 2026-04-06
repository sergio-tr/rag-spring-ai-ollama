package com.uniovi.rag.interfaces.rest.dto.knowledge;

import com.uniovi.rag.domain.knowledge.IndexSnapshotStatus;
import com.uniovi.rag.domain.knowledge.KnowledgeIndexSnapshot;
import com.uniovi.rag.domain.knowledge.KnowledgeSnapshotScopeType;

import java.time.Instant;
import java.util.UUID;

public record KnowledgeSnapshotSummaryResponse(
        UUID id,
        String signatureHash,
        KnowledgeSnapshotScopeType scopeType,
        IndexSnapshotStatus status,
        Instant createdAt,
        UUID resolvedConfigSnapshotId) {

    public static KnowledgeSnapshotSummaryResponse fromDomain(KnowledgeIndexSnapshot k) {
        return new KnowledgeSnapshotSummaryResponse(
                k.id(),
                k.signatureHash(),
                k.scopeType(),
                k.status(),
                k.createdAt(),
                k.resolvedConfigSnapshotId());
    }
}
