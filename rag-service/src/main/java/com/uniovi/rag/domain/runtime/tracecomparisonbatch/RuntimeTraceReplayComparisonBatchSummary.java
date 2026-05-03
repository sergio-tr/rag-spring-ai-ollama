package com.uniovi.rag.domain.runtime.tracecomparisonbatch;

/**
 * Aggregated counters for one P24 batch (no invalid-request item counter).
 */
public record RuntimeTraceReplayComparisonBatchSummary(
        int requestedCount,
        int selectedCount,
        int processedCount,
        int exactMatchCount,
        int compatibleMismatchItemCount,
        int structuralMismatchItemCount,
        int comparisonFailedSafeItemCount,
        int replayFailedSafeItemCount,
        int replayUnsupportedItemCount,
        int originalNotFoundOrInaccessibleItemCount) {}
