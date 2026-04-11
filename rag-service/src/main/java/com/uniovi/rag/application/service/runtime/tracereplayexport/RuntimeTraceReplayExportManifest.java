package com.uniovi.rag.application.service.runtime.tracereplayexport;

import java.time.Instant;
import java.util.UUID;

/**
 * {@code manifest.json} for P23 standalone replay ZIP exports.
 */
public record RuntimeTraceReplayExportManifest(
        int schemaVersion,
        String exportKind,
        Instant generatedAt,
        UUID requestedByUserId,
        String selectorType,
        Scope scope,
        String replayOutcome,
        long zipSizeBytes,
        boolean truncated) {

    public record Scope(UUID traceId, UUID conversationId, UUID messageId) {}
}
