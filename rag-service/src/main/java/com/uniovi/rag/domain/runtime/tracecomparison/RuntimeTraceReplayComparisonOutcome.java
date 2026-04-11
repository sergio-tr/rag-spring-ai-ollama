package com.uniovi.rag.domain.runtime.tracecomparison;

/**
 * Terminal outcome for one P19 comparison attempt (internal-only; transient).
 */
public enum RuntimeTraceReplayComparisonOutcome {
    NOT_ATTEMPTED,
    INVALID_REQUEST,
    ORIGINAL_TRACE_NOT_FOUND_OR_INACCESSIBLE,
    REPLAY_UNSUPPORTED,
    REPLAY_FAILED_SAFE,
    COMPARISON_SUCCEEDED_EXACT_MATCH,
    COMPARISON_SUCCEEDED_COMPATIBLE_MISMATCH,
    COMPARISON_SUCCEEDED_STRUCTURAL_MISMATCH,
    COMPARISON_FAILED_SAFE
}
