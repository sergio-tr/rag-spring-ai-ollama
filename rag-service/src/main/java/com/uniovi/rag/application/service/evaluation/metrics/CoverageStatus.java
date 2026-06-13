package com.uniovi.rag.application.service.evaluation.metrics;

/** Whether retrieval or source context was present for an executed item. */
public enum CoverageStatus {
    HAS_CONTEXT,
    NO_CONTEXT,
    NOT_APPLICABLE
}
