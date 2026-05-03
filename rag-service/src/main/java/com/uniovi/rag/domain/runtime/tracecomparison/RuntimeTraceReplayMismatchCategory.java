package com.uniovi.rag.domain.runtime.tracecomparison;

/**
 * Closed mismatch categories for P19 dimensional comparison (bounded; no open JSON diff).
 */
public enum RuntimeTraceReplayMismatchCategory {
    FIELD_VALUE_MISMATCH,
    STRUCTURAL_MISSING_ORIGINAL_FIELD,
    STRUCTURAL_MISSING_REPLAY_FIELD,
    STRUCTURAL_UNPARSEABLE_VALUE,
    STAGE_SEQUENCE_MISMATCH,
    STAGE_COUNT_MISMATCH,
    ANSWER_TEXT_MISMATCH,
    ANSWER_TEXT_UNAVAILABLE
}
