package com.uniovi.rag.application.service.runtime.traceregressionsuiteexport;

import java.time.Instant;

/**
 * {@code manifest.json} for P32 regression-suite ZIP exports. {@link #schemaVersion()} is JSON integer {@code 1}.
 */
public record RegressionSuiteExportManifest(
        int schemaVersion,
        String exportKind,
        Instant generatedAt,
        String requestedByUserId,
        String selectorType,
        Object scope,
        String suiteOutcome,
        int requestedEntryCount,
        int processedEntryCount,
        long zipSizeBytes,
        boolean truncated) {}
