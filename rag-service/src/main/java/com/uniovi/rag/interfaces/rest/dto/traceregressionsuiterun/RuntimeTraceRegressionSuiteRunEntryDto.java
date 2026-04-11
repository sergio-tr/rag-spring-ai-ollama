package com.uniovi.rag.interfaces.rest.dto.traceregressionsuiterun;

import com.uniovi.rag.domain.runtime.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunEntrySnapshot;

/** P42 persisted run entry JSON shape; truncation enforced here only (FD-truncate). */
public record RuntimeTraceRegressionSuiteRunEntryDto(
        int entryOrder,
        String entryKind,
        String selectorEcho,
        String executionStatus,
        String batchOutcome,
        Integer requestedCount,
        Integer selectedCount,
        Integer processedCount,
        String failureKind,
        String failureDetail) {

    public static RuntimeTraceRegressionSuiteRunEntryDto fromSnapshot(RuntimeTraceRegressionSuiteRunEntrySnapshot e) {
        return new RuntimeTraceRegressionSuiteRunEntryDto(
                e.entryOrder(),
                e.entryKind().name(),
                capCodePoints(e.selectorEcho(), 256),
                e.executionStatus().name(),
                e.batchOutcome().map(Enum::name).orElse(null),
                e.requestedCount().orElse(null),
                e.selectedCount().orElse(null),
                e.processedCount().orElse(null),
                e.failureKind().map(Enum::name).orElse(null),
                e.failureDetail().map(d -> capCodePoints(d, 1024)).orElse(null));
    }

    private static String capCodePoints(String s, int max) {
        if (s == null) {
            return null;
        }
        int count = s.codePointCount(0, s.length());
        if (count <= max) {
            return s;
        }
        int end = s.offsetByCodePoints(0, max);
        return s.substring(0, end);
    }
}
