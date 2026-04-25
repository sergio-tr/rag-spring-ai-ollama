package com.uniovi.rag.application.service.runtime.tracecomparisonexport;

import java.time.Instant;
import java.util.UUID;

/**
 * {@code manifest.json} for P21 replay-comparison ZIP exports.
 */
public record ReplayComparisonExportManifest(
        int schemaVersion,
        String exportKind,
        Instant generatedAt,
        UUID requestedByUserId,
        String selectorType,
        Scope scope,
        String comparisonOutcome,
        String replayOutcome,
        boolean exactMatch,
        int mismatchCount,
        boolean truncated) {

    public record Scope(UUID traceId, UUID conversationId, UUID messageId) {}
}
