package com.uniovi.rag.domain.runtime.traceregressionsuite;

import com.uniovi.rag.domain.runtime.tracecomparisonbatch.RuntimeTraceReplayComparisonBatchOutcome;

/**
 * One suite slot where P24 {@code execute} returned normally (P30).
 */
public record RuntimeTraceRegressionSuiteBatchReturnedEntryResult(
        int entryOrder,
        RuntimeTraceRegressionSuiteEntryKind entryKind,
        String selectorEcho,
        RuntimeTraceReplayComparisonBatchOutcome batchOutcome,
        int requestedCount,
        int selectedCount,
        int processedCount)
        implements RuntimeTraceRegressionSuiteEntryResult {

    public RuntimeTraceRegressionSuiteBatchReturnedEntryResult {
        selectorEcho = selectorEcho == null ? "" : selectorEcho;
    }
}
