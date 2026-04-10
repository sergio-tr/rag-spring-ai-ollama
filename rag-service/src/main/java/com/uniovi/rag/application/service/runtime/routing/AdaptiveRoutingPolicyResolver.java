package com.uniovi.rag.application.service.runtime.routing;

import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRouteKind;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRoutingDecision;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRoutingMode;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Pure policy-only resolver for P13 adaptive routing. Deterministic and rule-based.
 */
@Service
public class AdaptiveRoutingPolicyResolver {

    private final RouteCapabilityEvaluator capabilityEvaluator;

    public AdaptiveRoutingPolicyResolver(RouteCapabilityEvaluator capabilityEvaluator) {
        this.capabilityEvaluator = capabilityEvaluator;
    }

    public AdaptiveRoutingDecision resolve(ExecutionContext ctx, QueryPlan plan) {
        RagConfig rag = ctx.resolved().toRagConfig();
        if (!rag.adaptiveRoutingEnabled()) {
            AdaptiveRouteKind compat = compatibilityDefaultWorkflowRoute(rag);
            return new AdaptiveRoutingDecision(
                    AdaptiveRoutingMode.DISABLED,
                    compat,
                    Optional.empty(),
                    List.of("disabled_by_config"),
                    List.of());
        }

        RouteCapabilityEvaluator.RouteCapabilities caps = capabilityEvaluator.evaluate(rag, plan);
        List<String> reasons = new ArrayList<>(caps.reasons());

        // Frozen selection order from the plan.
        if (!caps.ambiguitySufficient()) {
            AdaptiveRouteKind wf = caps.retrievalWorkflowValid() ? AdaptiveRouteKind.RETRIEVAL_WORKFLOW_ROUTE : AdaptiveRouteKind.DIRECT_WORKFLOW_ROUTE;
            reasons.add("ambiguity_forces_workflow=" + wf);
            return new AdaptiveRoutingDecision(
                    AdaptiveRoutingMode.ENABLED,
                    wf,
                    Optional.empty(),
                    List.copyOf(reasons),
                    List.of());
        }

        if (caps.deterministicToolsEligible()) {
            reasons.add("selected=DETERMINISTIC_TOOL_ROUTE");
            return new AdaptiveRoutingDecision(
                    AdaptiveRoutingMode.ENABLED,
                    AdaptiveRouteKind.DETERMINISTIC_TOOL_ROUTE,
                    Optional.of(compatibilityDefaultWorkflowRoute(rag)),
                    List.copyOf(reasons),
                    List.of());
        }

        if (caps.functionCallingEligible()) {
            reasons.add("selected=FUNCTION_CALLING_ROUTE");
            return new AdaptiveRoutingDecision(
                    AdaptiveRoutingMode.ENABLED,
                    AdaptiveRouteKind.FUNCTION_CALLING_ROUTE,
                    Optional.of(compatibilityDefaultWorkflowRoute(rag)),
                    List.copyOf(reasons),
                    List.of());
        }

        if (caps.advisorEligible()) {
            reasons.add("selected=ADVISOR_ROUTE");
            // Advisor route is retrieval-only; fallback remains retrieval workflow.
            return new AdaptiveRoutingDecision(
                    AdaptiveRoutingMode.ENABLED,
                    AdaptiveRouteKind.ADVISOR_ROUTE,
                    Optional.of(AdaptiveRouteKind.RETRIEVAL_WORKFLOW_ROUTE),
                    List.copyOf(reasons),
                    List.of());
        }

        AdaptiveRouteKind wf = caps.retrievalWorkflowValid() ? AdaptiveRouteKind.RETRIEVAL_WORKFLOW_ROUTE : AdaptiveRouteKind.DIRECT_WORKFLOW_ROUTE;
        reasons.add("selected=" + wf);
        return new AdaptiveRoutingDecision(
                AdaptiveRoutingMode.ENABLED,
                wf,
                Optional.empty(),
                List.copyOf(reasons),
                List.of());
    }

    private static AdaptiveRouteKind compatibilityDefaultWorkflowRoute(RagConfig rag) {
        return rag.useRetrieval() ? AdaptiveRouteKind.RETRIEVAL_WORKFLOW_ROUTE : AdaptiveRouteKind.DIRECT_WORKFLOW_ROUTE;
    }
}

