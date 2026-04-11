package com.uniovi.rag.interfaces.rest.dto.tracecomparisonbatch;

import com.uniovi.rag.domain.runtime.tracecomparisonbatch.RuntimeTraceReplayComparisonBatchSummary;

/**
 * Seven category counters only (top-level duplicate counts live on {@link RuntimeTraceReplayComparisonBatchResponseDto}).
 */
public record RuntimeTraceReplayComparisonBatchSummaryDto(
        int exactMatchCount,
        int compatibleMismatchItemCount,
        int structuralMismatchItemCount,
        int comparisonFailedSafeItemCount,
        int replayFailedSafeItemCount,
        int replayUnsupportedItemCount,
        int originalNotFoundOrInaccessibleItemCount) {

    public static RuntimeTraceReplayComparisonBatchSummaryDto fromDomain(RuntimeTraceReplayComparisonBatchSummary s) {
        return new RuntimeTraceReplayComparisonBatchSummaryDto(
                s.exactMatchCount(),
                s.compatibleMismatchItemCount(),
                s.structuralMismatchItemCount(),
                s.comparisonFailedSafeItemCount(),
                s.replayFailedSafeItemCount(),
                s.replayUnsupportedItemCount(),
                s.originalNotFoundOrInaccessibleItemCount());
    }
}
