package com.uniovi.rag.application.service.runtime.tracereplaybatchexport;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * {@code manifest.json} for P29 replay-batch ZIP exports. {@link #schemaVersion()} is always JSON integer {@code 1}.
 */
public record ReplayBatchExportManifest(
        int schemaVersion,
        String exportKind,
        Instant generatedAt,
        UUID requestedByUserId,
        String selectorType,
        Scope scope,
        String batchOutcome,
        int requestedCount,
        int selectedCount,
        int processedCount,
        long zipSizeBytes,
        boolean truncated) {

    /**
     * Discriminated by {@link ReplayBatchExportManifest#selectorType()}: for {@code BY_TRACE_IDS} only {@link #traceIds()}
     * is set; for {@code BY_CONVERSATION} {@link #conversationId()} and optional filters are set.
     */
    public record Scope(
            List<UUID> traceIds,
            UUID conversationId,
            Instant createdAtFrom,
            Instant createdAtTo,
            String workflowName) {}
}
