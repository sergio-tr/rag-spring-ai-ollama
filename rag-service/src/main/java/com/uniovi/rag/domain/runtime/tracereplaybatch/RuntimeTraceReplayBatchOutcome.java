package com.uniovi.rag.domain.runtime.tracereplaybatch;

/**
 * Terminal batch outcome for P27 replay batch (never persisted).
 */
public enum RuntimeTraceReplayBatchOutcome {
    NOT_ATTEMPTED,
    EMPTY_SELECTION,
    COMPLETED_ALL_REPLAY_SUCCEEDED,
    COMPLETED_WITH_UNSUPPORTED_ITEMS,
    COMPLETED_WITH_FAILED_SAFE_ITEMS,
    COMPLETED_WITH_NOT_FOUND_ITEMS,
    COMPLETED_MIXED
}
