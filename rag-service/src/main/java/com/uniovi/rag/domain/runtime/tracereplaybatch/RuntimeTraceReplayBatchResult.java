package com.uniovi.rag.domain.runtime.tracereplaybatch;

import java.util.List;

/**
 * Transient P27 batch result (never persisted).
 */
public record RuntimeTraceReplayBatchResult(
        int requestedCount,
        int selectedCount,
        RuntimeTraceReplayBatchOutcome batchOutcome,
        RuntimeTraceReplayBatchSummary summary,
        List<RuntimeTraceReplayBatchItemResult> items) {

    public RuntimeTraceReplayBatchResult {
        items = List.copyOf(items);
    }
}
