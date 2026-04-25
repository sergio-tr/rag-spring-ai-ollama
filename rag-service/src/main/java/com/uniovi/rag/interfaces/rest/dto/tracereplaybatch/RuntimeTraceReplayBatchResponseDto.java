package com.uniovi.rag.interfaces.rest.dto.tracereplaybatch;

import com.uniovi.rag.domain.runtime.tracereplaybatch.RuntimeTraceReplayBatchItemResult;
import com.uniovi.rag.domain.runtime.tracereplaybatch.RuntimeTraceReplayBatchMode;
import com.uniovi.rag.domain.runtime.tracereplaybatch.RuntimeTraceReplayBatchResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Bounded P28 batch HTTP response (no trace blobs, no comparison artifacts).
 */
public record RuntimeTraceReplayBatchResponseDto(
        String batchMode,
        String batchOutcome,
        int requestedCount,
        int selectedCount,
        int processedCount,
        RuntimeTraceReplayBatchSummaryDto summary,
        List<RuntimeTraceReplayBatchItemDto> items) {

    public static final int MAX_ITEMS = 50;

    public static RuntimeTraceReplayBatchResponseDto fromBatchResult(
            RuntimeTraceReplayBatchMode mode, RuntimeTraceReplayBatchResult result) {
        List<RuntimeTraceReplayBatchItemDto> out = new ArrayList<>();
        for (RuntimeTraceReplayBatchItemResult item : result.items()) {
            if (out.size() >= MAX_ITEMS) {
                break;
            }
            out.add(RuntimeTraceReplayBatchItemDto.fromItemResult(item));
        }
        return new RuntimeTraceReplayBatchResponseDto(
                mode.name(),
                result.batchOutcome().name(),
                result.requestedCount(),
                result.selectedCount(),
                result.summary().processedCount(),
                RuntimeTraceReplayBatchSummaryDto.fromSummary(result.summary()),
                List.copyOf(out));
    }
}
