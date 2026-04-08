package com.uniovi.rag.domain.runtime.advisor;

/**
 * Closed set for 5.2 when advisor execution is not selected.
 */
public enum AdvisorSuppressionReason {
    DISABLED_BY_CONFIG,
    WORKFLOW_NOT_SUPPORTED,
    SUPPRESSED_BY_AMBIGUITY,
    SUPPRESSED_AFTER_DETERMINISTIC_SHORT_CIRCUIT,
    SUPPRESSED_AFTER_FUNCTION_CALLING_SHORT_CIRCUIT,
    POLICY_NO_EXECUTABLE_KINDS,
    RESERVED_KIND_NOT_EXECUTABLE
}
