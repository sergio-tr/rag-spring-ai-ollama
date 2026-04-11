package com.uniovi.rag.application.service.runtime.traceregressionsuitedefinitionexport;

import java.time.Instant;
import java.util.Map;

/**
 * P38 ZIP {@code manifest.json} payload (schema version 1).
 */
public record RuntimeTraceRegressionSuiteDefinitionExportManifest(
        int schemaVersion,
        String exportKind,
        Instant generatedAt,
        String requestedByUserId,
        String selectorType,
        Map<String, String> scope,
        String definitionId,
        long zipSizeBytes,
        boolean truncated) {}
