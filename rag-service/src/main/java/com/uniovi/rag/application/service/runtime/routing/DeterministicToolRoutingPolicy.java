package com.uniovi.rag.application.service.runtime.routing;

import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.domain.runtime.query.AmbiguityStatus;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRouteKind;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRoutingDecision;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRoutingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Narrow routing policy for presets with deterministic tool routing enabled without full adaptive routing.
 */
@Service
public class DeterministicToolRoutingPolicy {

    public AdaptiveRoutingDecision resolve(RagConfig rag, QueryPlan plan) {
        List<String> reasons = new ArrayList<>();
        AdaptiveRouteKind workflowFallback = compatibilityWorkflowRoute(rag);

        if (!rag.deterministicToolRoutingEnabled()) {
            reasons.add("deterministicToolRoutingEnabled=false");
            return new AdaptiveRoutingDecision(
                    AdaptiveRoutingMode.DISABLED,
                    workflowFallback,
                    Optional.empty(),
                    List.copyOf(reasons),
                    List.of());
        }
        if (rag.functionCallingEnabled()) {
            reasons.add("functionCallingEnabled=true");
            return new AdaptiveRoutingDecision(
                    AdaptiveRoutingMode.DISABLED,
                    workflowFallback,
                    Optional.empty(),
                    List.copyOf(reasons),
                    List.of());
        }
        if (!rag.toolsEnabled()) {
            reasons.add("toolsEnabled=false");
            return new AdaptiveRoutingDecision(
                    AdaptiveRoutingMode.DISABLED,
                    workflowFallback,
                    Optional.empty(),
                    List.copyOf(reasons),
                    List.of());
        }

        if (plan.ambiguityAssessment().status() != AmbiguityStatus.SUFFICIENT) {
            reasons.add("ambiguity_not_sufficient");
            return new AdaptiveRoutingDecision(
                    AdaptiveRoutingMode.ENABLED,
                    workflowFallback,
                    Optional.empty(),
                    List.copyOf(reasons),
                    List.of());
        }

        reasons.add("selected=DETERMINISTIC_TOOL_ROUTE");
        return new AdaptiveRoutingDecision(
                AdaptiveRoutingMode.ENABLED,
                AdaptiveRouteKind.DETERMINISTIC_TOOL_ROUTE,
                Optional.of(workflowFallback),
                List.copyOf(reasons),
                List.of());
    }

    private static AdaptiveRouteKind compatibilityWorkflowRoute(RagConfig rag) {
        return rag.useRetrieval() ? AdaptiveRouteKind.RETRIEVAL_WORKFLOW_ROUTE : AdaptiveRouteKind.DIRECT_WORKFLOW_ROUTE;
    }
}
