package com.uniovi.rag.domain.runtime.tool;

/**
 * Outcome of deterministic tool resolution / execution (frozen P7 contract).
 */
public enum DeterministicToolOutcome {
    NOT_ATTEMPTED,
    SUPPRESSED_BY_AMBIGUITY,
    DISABLED_BY_CONFIG,
    NOT_APPLICABLE,
    SELECTED,
    EXECUTED_SUCCESS,
    EXECUTED_FAILED_INFRA,
    FALLBACK_TO_WORKFLOW
}
