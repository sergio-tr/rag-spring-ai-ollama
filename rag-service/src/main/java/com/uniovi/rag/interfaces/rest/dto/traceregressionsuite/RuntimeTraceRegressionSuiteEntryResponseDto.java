package com.uniovi.rag.interfaces.rest.dto.traceregressionsuite;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteBatchReturnedEntryResult;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteEntryResult;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteExecutionFailedEntryResult;

/**
 * Bounded per-entry row — discriminated by {@code entryStatus} (P31).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RuntimeTraceRegressionSuiteEntryResponseDto(
        String entryStatus,
        int entryOrder,
        String entryKind,
        String selectorEcho,
        String batchOutcome,
        Integer requestedCount,
        Integer selectedCount,
        Integer processedCount,
        String failureKind,
        String failureDetail) {

    private static final int SELECTOR_ECHO_MAX_CODE_POINTS = 256;
    private static final int FAILURE_DETAIL_MAX_CODE_POINTS = 1024;

    public static RuntimeTraceRegressionSuiteEntryResponseDto fromEntry(RuntimeTraceRegressionSuiteEntryResult row) {
        if (row instanceof RuntimeTraceRegressionSuiteBatchReturnedEntryResult br) {
            return new RuntimeTraceRegressionSuiteEntryResponseDto(
                    "BATCH_RETURNED",
                    br.entryOrder(),
                    br.entryKind().name(),
                    capCodePoints(br.selectorEcho(), SELECTOR_ECHO_MAX_CODE_POINTS),
                    br.batchOutcome().name(),
                    br.requestedCount(),
                    br.selectedCount(),
                    br.processedCount(),
                    null,
                    null);
        }
        if (row instanceof RuntimeTraceRegressionSuiteExecutionFailedEntryResult ef) {
            return new RuntimeTraceRegressionSuiteEntryResponseDto(
                    "EXECUTION_FAILED",
                    ef.entryOrder(),
                    ef.entryKind().name(),
                    capCodePoints(ef.selectorEcho(), SELECTOR_ECHO_MAX_CODE_POINTS),
                    null,
                    null,
                    null,
                    null,
                    ef.failureKind().name(),
                    capCodePoints(ef.failureDetail(), FAILURE_DETAIL_MAX_CODE_POINTS));
        }
        throw new IllegalArgumentException("Unexpected entry result type: " + row.getClass());
    }

    static String capCodePoints(String s, int max) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        return s.codePoints()
                .limit(max)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    }
}
