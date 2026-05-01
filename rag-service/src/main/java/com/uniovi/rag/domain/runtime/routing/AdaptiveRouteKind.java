package com.uniovi.rag.domain.runtime.routing;

/**
 * Closed set of route families selected by P13 adaptive routing.
 */
public enum AdaptiveRouteKind {
    DIRECT_WORKFLOW_ROUTE,
    RETRIEVAL_WORKFLOW_ROUTE,
    DETERMINISTIC_TOOL_ROUTE,
    FUNCTION_CALLING_ROUTE,
    ADVISOR_ROUTE
}

