package com.uniovi.rag.application.service.runtime.tracecomparisonbatchexport;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * {@code manifest.json} for P26 replay-comparison batch ZIP exports. {@link #schemaVersion()} is always JSON integer
 * {@code 1} (never a string).
 */
public record ReplayComparisonBatchExportManifest(
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
     * Discriminated by {@link ReplayComparisonBatchExportManifest#selectorType()}: for {@code BY_TRACE_IDS} only
     * {@link #traceIds()} is set; for {@code BY_CONVERSATION} only {@link #conversationId()} and optional filters are
     * set.
     */
    public record Scope(
            List<UUID> traceIds,
            UUID conversationId,
            Instant createdAtFrom,
            Instant createdAtTo,
            String workflowName) {}
}
