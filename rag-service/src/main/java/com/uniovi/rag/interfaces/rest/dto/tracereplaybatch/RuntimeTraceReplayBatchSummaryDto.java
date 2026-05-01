package com.uniovi.rag.interfaces.rest.dto.tracereplaybatch;

import com.uniovi.rag.domain.runtime.tracereplaybatch.RuntimeTraceReplayBatchSummary;

/**
 * Five P27 processing-category counters only ({@code requestedCount} / {@code selectedCount} / {@code processedCount}
 * live on {@link RuntimeTraceReplayBatchResponseDto}).
 */
public record RuntimeTraceReplayBatchSummaryDto(
        int replaySucceededItemCount,
        int replayUnsupportedItemCount,
        int replayFailedSafeItemCount,
        int originalNotFoundOrInaccessibleItemCount,
        int replayNotAttemptedItemCount) {

    public static RuntimeTraceReplayBatchSummaryDto fromSummary(RuntimeTraceReplayBatchSummary s) {
        return new RuntimeTraceReplayBatchSummaryDto(
                s.replaySucceededItemCount(),
                s.replayUnsupportedItemCount(),
                s.replayFailedSafeItemCount(),
                s.originalNotFoundOrInaccessibleItemCount(),
                s.replayNotAttemptedItemCount());
    }
}
