package com.uniovi.rag.application.service.runtime.routing;

import com.uniovi.rag.domain.runtime.routing.AdaptiveRouteKind;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRoutingDecision;
import com.uniovi.rag.domain.runtime.routing.RouteExecutionGate;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Builds the single routing gate contract consumed by {@code RagExecutionOrchestrator}.
 */
@Service
public class RouteExecutionGateBuilder {

    public RouteExecutionGate fromDecision(AdaptiveRoutingDecision d) {
        AdaptiveRouteKind primary = d.primaryRouteKind();

        boolean workflowAllowed = primary == AdaptiveRouteKind.DIRECT_WORKFLOW_ROUTE
                || primary == AdaptiveRouteKind.RETRIEVAL_WORKFLOW_ROUTE;
        boolean deterministicAllowed = primary == AdaptiveRouteKind.DETERMINISTIC_TOOL_ROUTE;
        boolean fcAllowed = primary == AdaptiveRouteKind.FUNCTION_CALLING_ROUTE;
        boolean advisorAllowed = primary == AdaptiveRouteKind.ADVISOR_ROUTE;

        boolean fallbackRequired = !workflowAllowed;
        Optional<AdaptiveRouteKind> fallbackRouteKind = fallbackRequired ? d.fallbackWorkflowRouteKind() : Optional.empty();
        if (fallbackRequired && fallbackRouteKind.isEmpty()) {
            throw new IllegalArgumentException("fallbackWorkflowRouteKind required for non-workflow primary routes");
        }

        boolean workflowSelectorRequired = workflowAllowed;

        return new RouteExecutionGate(
                primary,
                workflowAllowed,
                deterministicAllowed,
                fcAllowed,
                advisorAllowed,
                fallbackRequired,
                fallbackRouteKind,
                workflowSelectorRequired);
    }
}
