package com.uniovi.rag.domain.runtime.tracecomparisonbatch;

import java.util.Optional;
import java.util.UUID;

/**
 * One P24 batch row: counters and scalars only (no mismatch lists).
 */
public record RuntimeTraceReplayComparisonBatchItemResult(
        int itemOrder,
        UUID requestedTraceId,
        Optional<UUID> resolvedOriginalTraceId,
        String comparisonOutcome,
        String replayOutcome,
        boolean exactMatch,
        String answerComparisonStatus,
        String summary,
        int compatibleMismatchCount,
        int structuralMismatchCount,
        boolean unsupported,
        boolean failedSafe) {

    public RuntimeTraceReplayComparisonBatchItemResult {
        resolvedOriginalTraceId = resolvedOriginalTraceId == null ? Optional.empty() : resolvedOriginalTraceId;
        summary = summary == null ? "" : summary;
    }
}
