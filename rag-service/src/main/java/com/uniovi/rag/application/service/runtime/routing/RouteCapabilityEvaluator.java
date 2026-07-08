package com.uniovi.rag.application.service.runtime.routing;

import com.uniovi.rag.application.service.runtime.tool.DeterministicToolEvidenceEvaluator;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.domain.runtime.query.AmbiguityStatus;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic evaluator of route-family eligibility facts (no LLM, no downstream execution).
 */
@Service
public class RouteCapabilityEvaluator {

    public RouteCapabilities evaluate(RagConfig rag, QueryPlan plan) {
        List<String> reasons = new ArrayList<>();

        boolean ambiguitySufficient = plan.ambiguityAssessment().status() == AmbiguityStatus.SUFFICIENT;
        if (!ambiguitySufficient) {
            reasons.add("ambiguity_not_sufficient");
        }

        boolean retrievalWorkflowValid = rag.useRetrieval();
        if (!retrievalWorkflowValid) {
            reasons.add("useRetrieval=false");
        }

        boolean directWorkflowValid = true;

        boolean structuredToolRouteApplicable = structuredToolRouteApplicable(plan);
        boolean deterministicToolsEligible =
                rag.toolsEnabled() && ambiguitySufficient && structuredToolRouteApplicable;
        if (!rag.toolsEnabled()) {
            reasons.add("toolsEnabled=false");
        } else if (ambiguitySufficient && !structuredToolRouteApplicable) {
            reasons.add("deterministic_tools_not_applicable");
        }

        boolean functionCallingEligible =
                rag.functionCallingEnabled() && ambiguitySufficient && structuredToolRouteApplicable;
        if (!rag.functionCallingEnabled()) {
            reasons.add("functionCallingEnabled=false");
        } else if (ambiguitySufficient && !structuredToolRouteApplicable) {
            reasons.add("function_calling_not_applicable");
        }

        boolean advisorEligible = rag.useAdvisor() && rag.useRetrieval() && ambiguitySufficient;
        if (!rag.useAdvisor()) {
            reasons.add("useAdvisor=false");
        }
        if (rag.useAdvisor() && !rag.useRetrieval()) {
            reasons.add("useAdvisor_requires_useRetrieval");
        }

        return new RouteCapabilities(
                ambiguitySufficient,
                directWorkflowValid,
                retrievalWorkflowValid,
                deterministicToolsEligible,
                functionCallingEligible,
                advisorEligible,
                List.copyOf(reasons));
    }

    private static boolean structuredToolRouteApplicable(QueryPlan plan) {
        DeterministicToolEvidenceEvaluator.Evaluation evaluation = DeterministicToolEvidenceEvaluator.evaluate(plan);
        return evaluation.toolApplicabilityEligible() && !evaluation.matchedKinds().isEmpty();
    }

    public record RouteCapabilities(
            boolean ambiguitySufficient,
            boolean directWorkflowValid,
            boolean retrievalWorkflowValid,
            boolean deterministicToolsEligible,
            boolean functionCallingEligible,
            boolean advisorEligible,
            List<String> reasons) {
    }
}

