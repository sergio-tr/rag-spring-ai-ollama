package com.uniovi.rag.application.service.runtime.routing;

import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRouteKind;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRoutingDecision;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRoutingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Selects advisor-first routing when enabled without adaptive, deterministic, or function-calling routing. */
@Service
public class AdvisorRoutingPolicy {

    public AdaptiveRoutingDecision resolve(RagConfig rag, QueryPlan plan) {
        List<String> reasons = new ArrayList<>();
        AdaptiveRouteKind workflowFallback = compatibilityWorkflowRoute(rag);

        if (!rag.useAdvisor()) {
            reasons.add("useAdvisor=false");
            return disabled(workflowFallback, reasons);
        }
        if (!rag.useRetrieval()) {
            reasons.add("useAdvisor_requires_useRetrieval");
            return disabled(workflowFallback, reasons);
        }
        if (rag.functionCallingEnabled()) {
            reasons.add("functionCallingEnabled=true");
            return disabled(workflowFallback, reasons);
        }
        if (rag.deterministicToolRoutingEnabled()) {
            reasons.add("deterministicToolRoutingEnabled=true");
            return disabled(workflowFallback, reasons);
        }
        if (rag.adaptiveRoutingEnabled()) {
            reasons.add("adaptiveRoutingEnabled=true");
            return disabled(workflowFallback, reasons);
        }

        reasons.add("selected=ADVISOR_ROUTE");
        return new AdaptiveRoutingDecision(
                AdaptiveRoutingMode.ENABLED,
                AdaptiveRouteKind.ADVISOR_ROUTE,
                Optional.of(workflowFallback),
                List.copyOf(reasons),
                List.of());
    }

    private static AdaptiveRoutingDecision disabled(AdaptiveRouteKind workflowFallback, List<String> reasons) {
        return new AdaptiveRoutingDecision(
                AdaptiveRoutingMode.DISABLED,
                workflowFallback,
                Optional.empty(),
                List.copyOf(reasons),
                List.of());
    }

    private static AdaptiveRouteKind compatibilityWorkflowRoute(RagConfig rag) {
        return rag.useRetrieval() ? AdaptiveRouteKind.RETRIEVAL_WORKFLOW_ROUTE : AdaptiveRouteKind.DIRECT_WORKFLOW_ROUTE;
    }
}
