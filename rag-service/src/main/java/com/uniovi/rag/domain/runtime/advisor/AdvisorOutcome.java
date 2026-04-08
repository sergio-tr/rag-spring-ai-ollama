package com.uniovi.rag.domain.runtime.advisor;

/**
 * Terminal advisor summary for one turn (5.2 closed set).
 */
public enum AdvisorOutcome {
    NOT_REACHED_BECAUSE_DETERMINISTIC_TOOL,
    NOT_REACHED_BECAUSE_FUNCTION_CALLING,
    SUPPRESSED_BY_POLICY,
    EXECUTED_SUCCESS,
    EXECUTED_FAILED_RETRIEVAL,
    EXECUTED_FAILED_PACKING,
    FAILED_RESERVED_KIND
}
