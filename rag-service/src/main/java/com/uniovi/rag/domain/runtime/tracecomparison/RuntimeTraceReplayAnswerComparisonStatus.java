package com.uniovi.rag.domain.runtime.tracecomparison;

/**
 * Answer text comparison status for one P19 comparison (original assistant text is absent on P16 detail in v1).
 */
public enum RuntimeTraceReplayAnswerComparisonStatus {
    NOT_COMPARABLE_ORIGINAL_ABSENT,
    EXACT_MATCH,
    NORMALIZED_MATCH,
    MISMATCH,
    REPLAY_ABSENT
}
