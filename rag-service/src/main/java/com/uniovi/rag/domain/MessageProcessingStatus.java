package com.uniovi.rag.domain;

/**
 * Assistant message lifecycle for job-backed chat generation. User messages use {@link #DONE} once stored.
 */
public enum MessageProcessingStatus {
    PENDING,
    PROCESSING,
    DONE,
    ERROR,
    CANCELLED
}
