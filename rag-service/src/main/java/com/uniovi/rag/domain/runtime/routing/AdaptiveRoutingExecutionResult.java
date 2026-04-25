package com.uniovi.rag.domain.runtime.routing;

import com.uniovi.rag.domain.runtime.engine.ExecutionStageTrace;

import java.util.List;
import java.util.Optional;

/**
 * Result of executing the adaptive routing stage (policy + evaluator + gate builder).
 */
public record AdaptiveRoutingExecutionResult(
        AdaptiveRoutingOutcome outcome,
        boolean routingAttempted,
        AdaptiveRouteKind routingRouteKind,
        boolean fallbackApplied,
        Optional<AdaptiveRouteKind> fallbackRouteKind,
        boolean workflowSelectorInvoked,
        RouteExecutionGate gate,
        List<ExecutionStageTrace> stageTraces
) {
    public AdaptiveRoutingExecutionResult {
        if (outcome == null) {
            throw new IllegalArgumentException("outcome must not be null");
        }
        if (routingRouteKind == null) {
            throw new IllegalArgumentException("routingRouteKind must not be null");
        }
        fallbackRouteKind = fallbackRouteKind == null ? Optional.empty() : fallbackRouteKind;
        stageTraces = List.copyOf(stageTraces == null ? List.of() : stageTraces);
        if (fallbackApplied && fallbackRouteKind.isEmpty()) {
            throw new IllegalArgumentException("fallbackRouteKind required when fallbackApplied=true");
        }
        if (!fallbackApplied && fallbackRouteKind.isPresent()) {
            throw new IllegalArgumentException("fallbackRouteKind must be empty when fallbackApplied=false");
        }
        if (gate == null) {
            throw new IllegalArgumentException("gate must not be null");
        }
    }
}

