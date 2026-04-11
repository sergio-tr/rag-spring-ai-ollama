package com.uniovi.rag.interfaces.rest.dto.traceregressionsuite;

import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteSummary;

/** Suite-level counters only (P31). */
public record RuntimeTraceRegressionSuiteSummaryDto(
        int requestedEntryCount,
        int processedEntryCount,
        int batchReturnedCount,
        int executionFailedCount,
        int batchNotAttemptedSubcount) {

    public static RuntimeTraceRegressionSuiteSummaryDto fromSummary(RuntimeTraceRegressionSuiteSummary s) {
        return new RuntimeTraceRegressionSuiteSummaryDto(
                s.requestedEntryCount(),
                s.processedEntryCount(),
                s.batchReturnedCount(),
                s.executionFailedCount(),
                s.batchNotAttemptedSubcount());
    }
}
