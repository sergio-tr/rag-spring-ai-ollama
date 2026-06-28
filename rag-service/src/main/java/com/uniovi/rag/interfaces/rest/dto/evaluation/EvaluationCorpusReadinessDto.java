package com.uniovi.rag.interfaces.rest.dto.evaluation;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record EvaluationCorpusReadinessDto(
        UUID corpusId,
        UUID indexProjectId,
        int documentCount,
        int readyCount,
        /** Documents with READY status and on-disk source binary present. */
        int storageReadyCount,
        int processingCount,
        int failedCount,
        /** When set, benchmark preflight should block until resolved. */
        String primaryBlocker,
        String primaryBlockerMessage,
        UUID activeSnapshotId,
        boolean reindexRequired,
        /** Snapshot/index issue when documents are ready (informational; RAG may still auto-reindex). */
        String snapshotBlocker,
        /** Optional internal detail (e.g. {@code NO_ACTIVE_INDEX}) when {@link #snapshotBlocker()} is {@code REINDEX_REQUIRED}. */
        String snapshotBlockerDetailCode,
        List<UUID> selectedSnapshotIds,
        /** True when at least one document is READY and ingest is not blocking. */
        boolean runnable) {}
