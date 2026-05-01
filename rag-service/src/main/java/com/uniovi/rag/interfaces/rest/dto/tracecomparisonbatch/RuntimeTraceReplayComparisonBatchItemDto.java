package com.uniovi.rag.interfaces.rest.dto.tracecomparisonbatch;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.uniovi.rag.domain.runtime.tracecomparison.RuntimeTraceReplayComparisonOutcome;
import com.uniovi.rag.domain.runtime.tracecomparisonbatch.RuntimeTraceReplayComparisonBatchItemResult;

import java.util.UUID;

/**
 * P25 item row: counters and scalars only (no mismatch lists).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RuntimeTraceReplayComparisonBatchItemDto(
        UUID requestedTraceId,
        UUID resolvedOriginalTraceId,
        int itemOrder,
        String comparisonOutcome,
        String replayOutcome,
        boolean exactMatch,
        String answerComparisonStatus,
        String summary,
        int compatibleMismatchCount,
        int structuralMismatchCount,
        boolean unsupported,
        boolean failedSafe,
        boolean originalTraceLoaded) {

    private static final int MAX_ITEM_SUMMARY_CHARS = 512;

    public static RuntimeTraceReplayComparisonBatchItemDto fromDomain(RuntimeTraceReplayComparisonBatchItemResult item) {
        UUID resolved = item.resolvedOriginalTraceId().orElse(null);
        boolean originalLoaded =
                !RuntimeTraceReplayComparisonOutcome.ORIGINAL_TRACE_NOT_FOUND_OR_INACCESSIBLE
                        .name()
                        .equals(item.comparisonOutcome());
        return new RuntimeTraceReplayComparisonBatchItemDto(
                item.requestedTraceId(),
                resolved,
                item.itemOrder(),
                item.comparisonOutcome(),
                item.replayOutcome(),
                item.exactMatch(),
                item.answerComparisonStatus(),
                capSummary(item.summary()),
                item.compatibleMismatchCount(),
                item.structuralMismatchCount(),
                item.unsupported(),
                item.failedSafe(),
                originalLoaded);
    }

    private static String capSummary(String s) {
        if (s == null) {
            return "";
        }
        if (s.length() <= MAX_ITEM_SUMMARY_CHARS) {
            return s;
        }
        return s.substring(0, MAX_ITEM_SUMMARY_CHARS);
    }
}
