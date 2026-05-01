package com.uniovi.rag.interfaces.rest.dto.knowledge;

import com.uniovi.rag.application.service.knowledge.KnowledgeRebuildExecuteResult;

import java.util.UUID;

public record KnowledgeRebuildExecuteResponse(
        UUID resolvedConfigSnapshotId, UUID knowledgeSnapshotId, UUID asyncTaskId) {

    public static KnowledgeRebuildExecuteResponse from(KnowledgeRebuildExecuteResult r) {
        return new KnowledgeRebuildExecuteResponse(r.resolvedConfigSnapshotId(), r.knowledgeSnapshotId(), r.asyncTaskId());
    }
}
