package com.uniovi.rag.domain.runtime.tracecomparisonbatch;

import java.util.List;

/**
 * Transient P24 batch result (never persisted).
 */
public record RuntimeTraceReplayComparisonBatchResult(
        RuntimeTraceReplayComparisonBatchOutcome batchOutcome,
        RuntimeTraceReplayComparisonBatchSummary summary,
        List<RuntimeTraceReplayComparisonBatchItemResult> items,
        int requestedCount,
        int selectedCount) {

    public RuntimeTraceReplayComparisonBatchResult {
        items = List.copyOf(items);
    }
}
