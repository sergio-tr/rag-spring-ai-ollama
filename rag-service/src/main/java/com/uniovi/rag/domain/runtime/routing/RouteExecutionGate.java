package com.uniovi.rag.domain.runtime.routing;

import java.util.Objects;
import java.util.Optional;

/**
 * Single routing contract consumed by {@code RagExecutionOrchestrator}.
 */
public record RouteExecutionGate(
        AdaptiveRouteKind primaryRouteKind,
        boolean workflowAllowed,
        boolean deterministicToolsAllowed,
        boolean functionCallingAllowed,
        boolean advisorAllowed,
        boolean fallbackRequired,
        Optional<AdaptiveRouteKind> fallbackRouteKind,
        boolean workflowSelectorRequired
) {
    public RouteExecutionGate {
        if (primaryRouteKind == null) {
            throw new IllegalArgumentException("primaryRouteKind must not be null");
        }
        fallbackRouteKind = Objects.requireNonNullElseGet(fallbackRouteKind, Optional::empty);
        int allowedCount = 0;
        if (workflowAllowed) allowedCount++;
        if (deterministicToolsAllowed) allowedCount++;
        if (functionCallingAllowed) allowedCount++;
        if (advisorAllowed) allowedCount++;
        if (allowedCount != 1) {
            throw new IllegalArgumentException("exactly one allowed family must be true");
        }
        if (fallbackRequired && fallbackRouteKind.isEmpty()) {
            throw new IllegalArgumentException("fallbackRouteKind required when fallbackRequired=true");
        }
        if (!fallbackRequired && fallbackRouteKind.isPresent()) {
            throw new IllegalArgumentException("fallbackRouteKind must be empty when fallbackRequired=false");
        }
        if (fallbackRouteKind.isPresent()) {
            AdaptiveRouteKind fk = fallbackRouteKind.get();
            if (fk != AdaptiveRouteKind.DIRECT_WORKFLOW_ROUTE && fk != AdaptiveRouteKind.RETRIEVAL_WORKFLOW_ROUTE) {
                throw new IllegalArgumentException("fallbackRouteKind must be a workflow route kind");
            }
        }
        if (workflowSelectorRequired && !workflowAllowed && !fallbackRequired) {
            throw new IllegalArgumentException("workflowSelectorRequired implies workflowAllowed or fallbackRequired");
        }
    }
}

