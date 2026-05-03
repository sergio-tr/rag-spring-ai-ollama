package com.uniovi.rag.application.service.runtime.traceregressionsuiterunexport;

import java.time.Instant;
import java.util.Map;

/** P43 ZIP {@code manifest.json} payload (schema version 1). */
public record RuntimeTraceRegressionSuiteRunExportManifest(
        int schemaVersion,
        String exportKind,
        Instant generatedAt,
        String requestedByUserId,
        String selectorType,
        Map<String, String> scope,
        String runId,
        String sourceType,
        String definitionId,
        String suiteOutcome,
        int requestedEntryCount,
        int processedEntryCount,
        int batchReturnedCount,
        int executionFailedCount,
        int batchNotAttemptedSubcount,
        long zipSizeBytes,
        boolean truncated) {}
