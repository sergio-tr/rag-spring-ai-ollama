package com.uniovi.rag.application.service.runtime.routing;

import com.uniovi.rag.application.service.runtime.query.ActaFieldAnchorHeuristics;
import com.uniovi.rag.application.service.runtime.query.QueryPlanSlotEnricher;
import com.uniovi.rag.application.service.runtime.tool.DeterministicToolApplicability;
import com.uniovi.rag.application.service.runtime.tool.DeterministicToolEvidenceEvaluator;
import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.domain.runtime.query.AmbiguityStatus;
import com.uniovi.rag.domain.runtime.query.ClassifierStatus;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRouteKind;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRoutingDecision;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRoutingMode;
import com.uniovi.rag.domain.runtime.tool.DeterministicEvidenceLevel;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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

        Optional<AdaptiveRoutingDecision> structuredField = structuredClassifierFieldRoute(rag, plan, workflowFallback);
        if (structuredField.isPresent()) {
            return structuredField.get();
        }

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

    /**
     * Classifier-backed structured field queries (e.g. acta participants on an exact date) must use
     * deterministic tools even when function calling is enabled on the preset (Demo_Best).
     */
    private static Optional<AdaptiveRoutingDecision> structuredClassifierFieldRoute(
            RagConfig rag, QueryPlan plan, AdaptiveRouteKind workflowFallback) {
        if (!rag.toolsEnabled()) {
            return Optional.empty();
        }
        if (!ambiguityAllowsStructuredRoute(plan)) {
            return Optional.empty();
        }
        if (plan.classifierStatus() != ClassifierStatus.OK) {
            return Optional.empty();
        }
        Optional<QueryType> classifierType = plan.classifierQueryType();
        if (classifierType.isEmpty() || !DeterministicToolApplicability.isApplicableQueryType(classifierType.get())) {
            return Optional.empty();
        }
        if (classifierType.get() == QueryType.GET_FIELD) {
            if (!hasGetFieldSlot(plan)) {
                return Optional.empty();
            }
            return Optional.of(structuredFieldDecision(classifierType.get(), workflowFallback));
        }
        if (classifierType.get() == QueryType.SUMMARIZE_MEETING
                || classifierType.get() == QueryType.BOOLEAN_QUERY
                || classifierType.get() == QueryType.COUNT_DOCUMENTS
                || classifierType.get() == QueryType.GET_DURATION
                || classifierType.get() == QueryType.FILTER_AND_LIST
                || classifierType.get() == QueryType.FIND_PARAGRAPH
                || classifierType.get() == QueryType.COUNT_AND_EXPLAIN) {
            return Optional.of(structuredFieldDecision(classifierType.get(), workflowFallback));
        }
        DeterministicToolEvidenceEvaluator.Evaluation evaluation = DeterministicToolEvidenceEvaluator.evaluate(plan);
        if (evaluation.singleKind().isEmpty()) {
            return Optional.empty();
        }
        if (evaluation.evidenceLevel() != DeterministicEvidenceLevel.STRONG
                && evaluation.evidenceLevel() != DeterministicEvidenceLevel.ORACLE) {
            return Optional.empty();
        }
        return Optional.of(structuredFieldDecision(classifierType.get(), workflowFallback));
    }

    private static AdaptiveRoutingDecision structuredFieldDecision(
            QueryType classifierType, AdaptiveRouteKind workflowFallback) {
        List<String> reasons =
                List.of(
                        "structured_classifier_field_route",
                        "classifierQueryType=" + classifierType.name(),
                        "selected=DETERMINISTIC_TOOL_ROUTE");
        return new AdaptiveRoutingDecision(
                AdaptiveRoutingMode.ENABLED,
                AdaptiveRouteKind.DETERMINISTIC_TOOL_ROUTE,
                Optional.of(workflowFallback),
                reasons,
                List.of());
    }

    private static boolean ambiguityAllowsStructuredRoute(QueryPlan plan) {
        if (isCompoundMonthTopicAttendeeFilterPlan(plan)) {
            return true;
        }
        if (ActaFieldAnchorHeuristics.needsActaAnchor(plan)) {
            return false;
        }
        return switch (plan.ambiguityAssessment().status()) {
            case SUFFICIENT -> true;
            case CONFLICTING_CUES ->
                    plan.classifierStatus() == ClassifierStatus.OK
                            && plan.classifierQueryType()
                                    .filter(DeterministicToolApplicability::isApplicableQueryType)
                                    .isPresent();
            default -> false;
        };
    }

    private static boolean isCompoundMonthTopicAttendeeFilterPlan(QueryPlan plan) {
        if (plan == null) {
            return false;
        }
        for (String candidate :
                List.of(plan.normalizedQueryText(), plan.rewrittenQueryText(), plan.rawUserQuery())) {
            if (candidate != null
                    && ActaFieldAnchorHeuristics.isCompoundMonthTopicAttendeeFilter(
                            candidate.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasGetFieldSlot(QueryPlan plan) {
        String field = plan.slots().get("field");
        if (field != null && !field.isBlank()) {
            return true;
        }
        return QueryPlanSlotEnricher.inferFieldSlot(plan.normalizedQueryText()).isPresent();
    }

    private static AdaptiveRouteKind compatibilityWorkflowRoute(RagConfig rag) {
        return rag.useRetrieval() ? AdaptiveRouteKind.RETRIEVAL_WORKFLOW_ROUTE : AdaptiveRouteKind.DIRECT_WORKFLOW_ROUTE;
    }
}
