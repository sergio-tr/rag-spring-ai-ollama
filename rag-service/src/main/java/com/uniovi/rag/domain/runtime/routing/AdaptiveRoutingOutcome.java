package com.uniovi.rag.domain.runtime.routing;

/**
 * Closed set of terminal routing outcomes for a single orchestrated turn (P13).
 */
public enum AdaptiveRoutingOutcome {
    SUPPRESSED_BY_CLARIFICATION_SHORT_CIRCUIT,
    DISABLED_BY_CONFIG,
    PRIMARY_ROUTE_SELECTED,
    PRIMARY_ROUTE_SELECTED_WITH_WORKFLOW_FALLBACK,
    PRIMARY_ROUTE_EXECUTED_TERMINALLY,
    PRIMARY_ROUTE_CONTINUED_TO_WORKFLOW
}

