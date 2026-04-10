package com.uniovi.rag.domain.runtime.judge;

/**
 * Closed terminal outcome set for P14 judge stage.
 */
public enum JudgeOutcome {
    NOT_ATTEMPTED,
    ACCEPTED,
    REJECTED_NO_RETRY,
    RETRY_REQUESTED,
    RETRY_SUCCEEDED,
    RETRY_FAILED,
    FAILED_SAFE
}

