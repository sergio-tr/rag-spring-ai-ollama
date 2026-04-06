package com.uniovi.rag.application.service.knowledge;

import java.util.UUID;

/**
 * Outcome of {@link ReindexService#executeKnowledgeReindexDecision} (synchronous rebuild path).
 */
public record KnowledgeReindexExecutionResult(UUID reindexEventId, UUID knowledgeSnapshotId) {

    public static final KnowledgeReindexExecutionResult NONE = new KnowledgeReindexExecutionResult(null, null);
}
