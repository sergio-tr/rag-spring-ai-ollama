package com.uniovi.rag.domain.runtime.routing;

import java.util.List;
import java.util.Optional;

/**
 * Pure policy output: selects one primary route family and provides deterministic fallback hints.
 */
public record AdaptiveRoutingDecision(
        AdaptiveRoutingMode mode,
        AdaptiveRouteKind primaryRouteKind,
        Optional<AdaptiveRouteKind> fallbackWorkflowRouteKind,
        List<String> reasons,
        List<String> suppressionNotes
) {
    public AdaptiveRoutingDecision {
        if (mode == null) {
            throw new IllegalArgumentException("mode must not be null");
        }
        if (primaryRouteKind == null) {
            throw new IllegalArgumentException("primaryRouteKind must not be null");
        }
        fallbackWorkflowRouteKind = fallbackWorkflowRouteKind == null ? Optional.empty() : fallbackWorkflowRouteKind;
        reasons = List.copyOf(reasons == null ? List.of() : reasons);
        suppressionNotes = List.copyOf(suppressionNotes == null ? List.of() : suppressionNotes);
        if (fallbackWorkflowRouteKind.isPresent()) {
            AdaptiveRouteKind fk = fallbackWorkflowRouteKind.get();
            if (fk != AdaptiveRouteKind.DIRECT_WORKFLOW_ROUTE && fk != AdaptiveRouteKind.RETRIEVAL_WORKFLOW_ROUTE) {
                throw new IllegalArgumentException("fallbackWorkflowRouteKind must be a workflow route kind");
            }
        }
    }
}

