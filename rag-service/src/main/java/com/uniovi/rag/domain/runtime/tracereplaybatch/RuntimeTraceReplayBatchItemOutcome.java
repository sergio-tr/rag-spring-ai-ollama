package com.uniovi.rag.domain.runtime.tracereplaybatch;

/**
 * Per-item bucket for P27 batch aggregation (closed enum).
 */
public enum RuntimeTraceReplayBatchItemOutcome {
    REPLAY_SUCCEEDED,
    REPLAY_UNSUPPORTED,
    REPLAY_FAILED_SAFE,
    ORIGINAL_TRACE_NOT_FOUND_OR_INACCESSIBLE,
    REPLAY_NOT_ATTEMPTED
}
