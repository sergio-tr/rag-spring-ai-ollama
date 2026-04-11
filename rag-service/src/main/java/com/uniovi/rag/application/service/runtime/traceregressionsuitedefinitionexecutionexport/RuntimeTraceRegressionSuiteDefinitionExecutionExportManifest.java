package com.uniovi.rag.application.service.runtime.traceregressionsuitedefinitionexecutionexport;

import java.time.Instant;

/**
 * {@code manifest.json} for P37 definition-execution ZIP exports. {@link #schemaVersion()} is JSON integer {@code 1}.
 */
public record RuntimeTraceRegressionSuiteDefinitionExecutionExportManifest(
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
