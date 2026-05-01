package com.uniovi.rag.interfaces.rest.dto.tracecomparison;

import com.uniovi.rag.domain.runtime.tracecomparison.RuntimeTraceReplayFieldMismatch;
import com.uniovi.rag.domain.runtime.tracecomparison.RuntimeTraceReplayMismatchCategory;

/**
 * Bounded mismatch line for P20 replay-comparison HTTP responses.
 */
public record RuntimeTraceReplayComparisonMismatchDto(
        String fieldPath,
        String category,
        String originalSnippet,
        String replaySnippet) {

    static final int MAX_SNIPPET_CHARS = 256;

    public static RuntimeTraceReplayComparisonMismatchDto fromFieldMismatch(RuntimeTraceReplayFieldMismatch m) {
        RuntimeTraceReplayMismatchCategory c = m.category() != null ? m.category() : RuntimeTraceReplayMismatchCategory.FIELD_VALUE_MISMATCH;
        return new RuntimeTraceReplayComparisonMismatchDto(
                m.fieldPath() != null ? m.fieldPath() : "",
                c.name(),
                snippet(m.originalSnippet()),
                snippet(m.replaySnippet()));
    }

    private static String snippet(String s) {
        if (s == null) {
            return "";
        }
        if (s.length() <= MAX_SNIPPET_CHARS) {
            return s;
        }
        return s.substring(0, MAX_SNIPPET_CHARS);
    }
}
