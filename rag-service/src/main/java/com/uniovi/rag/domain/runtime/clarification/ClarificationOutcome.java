package com.uniovi.rag.domain.runtime.clarification;

/**
 * Terminal clarification outcome for one orchestrated turn (P11).
 */
public enum ClarificationOutcome {
    NOT_NEEDED,
    DISABLED_BY_CONFIG,
    ASKED_CLARIFICATION,
    ASKED_CLARIFICATION_AGAIN,
    RESOLVED_FROM_PENDING,
    INVALID_PENDING_STATE_RECOVERED
}
