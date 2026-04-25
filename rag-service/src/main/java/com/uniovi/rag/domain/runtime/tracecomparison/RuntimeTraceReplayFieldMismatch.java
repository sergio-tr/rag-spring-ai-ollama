package com.uniovi.rag.domain.runtime.tracecomparison;

/**
 * One bounded field-level mismatch within a P19 comparison result.
 */
public record RuntimeTraceReplayFieldMismatch(
        String fieldPath,
        RuntimeTraceReplayMismatchCategory category,
        String originalSnippet,
        String replaySnippet) {

    public RuntimeTraceReplayFieldMismatch {
        fieldPath = fieldPath != null ? fieldPath : "";
        category = category != null ? category : RuntimeTraceReplayMismatchCategory.FIELD_VALUE_MISMATCH;
        originalSnippet = originalSnippet != null ? originalSnippet : "";
        replaySnippet = replaySnippet != null ? replaySnippet : "";
    }
}
