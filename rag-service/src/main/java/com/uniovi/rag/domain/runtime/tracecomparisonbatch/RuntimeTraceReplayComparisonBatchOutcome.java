package com.uniovi.rag.domain.runtime.tracecomparisonbatch;

/**
 * Terminal batch outcome for P24 (closed set).
 */
public enum RuntimeTraceReplayComparisonBatchOutcome {
    NOT_ATTEMPTED,
    EMPTY_SELECTION,
    COMPLETED_ALL_EXACT_MATCH,
    COMPLETED_WITH_COMPATIBLE_MISMATCHES_ONLY,
    COMPLETED_WITH_STRUCTURAL_MISMATCHES,
    COMPLETED_WITH_UNSUPPORTED_ITEMS,
    COMPLETED_WITH_FAILED_SAFE_ITEMS,
    COMPLETED_MIXED
}
