package com.uniovi.rag.interfaces.rest.dto.tracecomparisonbatch;

import com.uniovi.rag.domain.runtime.tracecomparisonbatch.RuntimeTraceReplayComparisonBatchItemResult;
import com.uniovi.rag.domain.runtime.tracecomparisonbatch.RuntimeTraceReplayComparisonBatchMode;
import com.uniovi.rag.domain.runtime.tracecomparisonbatch.RuntimeTraceReplayComparisonBatchResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Bounded P25 batch HTTP response (no mismatch blobs).
 */
public record RuntimeTraceReplayComparisonBatchResponseDto(
        String batchMode,
        String batchOutcome,
        int requestedCount,
        int selectedCount,
        int processedCount,
        RuntimeTraceReplayComparisonBatchSummaryDto summary,
        List<RuntimeTraceReplayComparisonBatchItemDto> items) {

    public static final int MAX_ITEMS = 50;

    public static RuntimeTraceReplayComparisonBatchResponseDto fromBatchResult(
            RuntimeTraceReplayComparisonBatchMode mode, RuntimeTraceReplayComparisonBatchResult result) {
        List<RuntimeTraceReplayComparisonBatchItemDto> out = new ArrayList<>();
        for (RuntimeTraceReplayComparisonBatchItemResult item : result.items()) {
            if (out.size() >= MAX_ITEMS) {
                break;
            }
            out.add(RuntimeTraceReplayComparisonBatchItemDto.fromDomain(item));
        }
        return new RuntimeTraceReplayComparisonBatchResponseDto(
                mode.name(),
                result.batchOutcome().name(),
                result.requestedCount(),
                result.selectedCount(),
                result.summary().processedCount(),
                RuntimeTraceReplayComparisonBatchSummaryDto.fromDomain(result.summary()),
                List.copyOf(out));
    }
}
