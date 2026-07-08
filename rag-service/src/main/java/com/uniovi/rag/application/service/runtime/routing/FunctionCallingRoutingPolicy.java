package com.uniovi.rag.application.service.runtime.routing;

import com.uniovi.rag.application.service.runtime.query.ActaFieldAnchorHeuristics;
import com.uniovi.rag.application.service.runtime.tool.DeterministicToolEvidenceEvaluator;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRouteKind;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRoutingDecision;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRoutingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Selects function-calling-first routing when enabled without adaptive or deterministic tool routing. */
@Service
public class FunctionCallingRoutingPolicy {

    public AdaptiveRoutingDecision resolve(RagConfig rag, QueryPlan plan) {
        List<String> reasons = new ArrayList<>();
        AdaptiveRouteKind workflowFallback = compatibilityWorkflowRoute(rag);

        if (ActaFieldAnchorHeuristics.isExplicitActaFilenameFieldExtractionQuery(plan)) {
            reasons.add("explicit_acta_filename_field_query");
            return disabled(workflowFallback, reasons);
        }

        if (!rag.functionCallingEnabled()) {
            reasons.add("functionCallingEnabled=false");
            return disabled(workflowFallback, reasons);
        }
        if (rag.adaptiveRoutingEnabled()) {
            reasons.add("adaptiveRoutingEnabled=true");
            return disabled(workflowFallback, reasons);
        }

        DeterministicToolEvidenceEvaluator.Evaluation evaluation = DeterministicToolEvidenceEvaluator.evaluate(plan);
        if (!evaluation.toolApplicabilityEligible() || evaluation.matchedKinds().isEmpty()) {
            reasons.add("function_calling_not_applicable");
            return disabled(workflowFallback, reasons);
        }

        reasons.add("selected=FUNCTION_CALLING_ROUTE");
        return new AdaptiveRoutingDecision(
                AdaptiveRoutingMode.ENABLED,
                AdaptiveRouteKind.FUNCTION_CALLING_ROUTE,
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
