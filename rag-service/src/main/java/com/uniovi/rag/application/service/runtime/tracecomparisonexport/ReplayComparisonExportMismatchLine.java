package com.uniovi.rag.application.service.runtime.tracecomparisonexport;

/**
 * Single bounded mismatch line in {@code comparison.json}.
 */
public record ReplayComparisonExportMismatchLine(
        String fieldPath,
        String category,
        String originalSnippet,
        String replaySnippet) {}
