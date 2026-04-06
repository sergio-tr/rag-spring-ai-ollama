package com.uniovi.rag.application.service.knowledge;

import java.util.UUID;

/** Successful execute outcome for knowledge rebuild/reindex. */
public record KnowledgeRebuildExecuteResult(
        UUID resolvedConfigSnapshotId, UUID knowledgeSnapshotId, UUID reindexEventId, UUID asyncTaskId) {

    public static KnowledgeRebuildExecuteResult of(
            UUID resolvedConfigSnapshotId, UUID knowledgeSnapshotId, UUID reindexEventId) {
        return new KnowledgeRebuildExecuteResult(resolvedConfigSnapshotId, knowledgeSnapshotId, reindexEventId, null);
    }
}
