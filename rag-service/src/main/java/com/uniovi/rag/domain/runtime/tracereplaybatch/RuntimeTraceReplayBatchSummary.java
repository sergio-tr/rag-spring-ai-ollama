package com.uniovi.rag.domain.runtime.tracereplaybatch;

/**
 * Aggregated processing counters only for P27 batch ({@code requestedCount} / {@code selectedCount} live on
 * {@link RuntimeTraceReplayBatchResult}).
 */
public record RuntimeTraceReplayBatchSummary(
        int processedCount,
        int replaySucceededItemCount,
        int replayUnsupportedItemCount,
        int replayFailedSafeItemCount,
        int originalNotFoundOrInaccessibleItemCount,
        int replayNotAttemptedItemCount) {

    public static RuntimeTraceReplayBatchSummary zeros() {
        return new RuntimeTraceReplayBatchSummary(0, 0, 0, 0, 0, 0);
    }
}
