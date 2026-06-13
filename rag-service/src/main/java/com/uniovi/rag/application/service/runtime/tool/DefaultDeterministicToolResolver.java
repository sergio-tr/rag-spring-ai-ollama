package com.uniovi.rag.application.service.runtime.tool;

import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.query.AmbiguityStatus;
import com.uniovi.rag.domain.runtime.query.ClassifierStatus;
import com.uniovi.rag.domain.runtime.query.ExpectedAnswerShape;
import com.uniovi.rag.domain.runtime.query.QueryIntent;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolDecision;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolKind;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolOutcome;
import com.uniovi.rag.domain.runtime.tool.ToolExecutionMode;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Resolves one deterministic tool kind from query-plan signals; ambiguous matches fall back to workflow. */
@Component
public class DefaultDeterministicToolResolver implements DeterministicToolResolver {

    public static final String FALLBACK_POLICY_INFRA = "tool_fallback_to_workflow";
    public static final String REASON_CLASSIFIER_INVALID = "classifier_invalid_output";
    public static final String REASON_CLASSIFIER_LOW_CONFIDENCE = "classifier_low_confidence";
    public static final String REASON_CLASSIFIER_UNAVAILABLE = "classifier_unavailable";
    public static final String REASON_NON_APPLICABLE_TYPE = "non_applicable_query_type";
    public static final String REASON_HEURISTIC_AMBIGUOUS = "heuristic_ambiguous_match";

    @Override
    public DeterministicToolDecision resolve(ExecutionContext ctx, QueryPlan plan) {
        RagConfig rag = ctx.resolved().toRagConfig();
        ToolExecutionMode mode = rag.toolsEnabled() ? ToolExecutionMode.ENABLED : ToolExecutionMode.DISABLED;
        if (mode == ToolExecutionMode.DISABLED) {
            return new DeterministicToolDecision(
                    mode,
                    DeterministicToolOutcome.DISABLED_BY_CONFIG,
                    false,
                    Optional.empty(),
                    List.of("toolsEnabled=false"),
                    normalizedInputs(ctx, plan),
                    Optional.of("tool_disabled_by_config"),
                    Optional.empty());
        }
        if (plan.ambiguityAssessment().status() != AmbiguityStatus.SUFFICIENT) {
            return new DeterministicToolDecision(
                    mode,
                    DeterministicToolOutcome.SUPPRESSED_BY_AMBIGUITY,
                    false,
                    Optional.empty(),
                    List.of("ambiguityStatus=" + plan.ambiguityAssessment().status()),
                    normalizedInputs(ctx, plan),
                    Optional.of("tool_suppressed_by_ambiguity"),
                    Optional.empty());
        }

        Optional<String> hardSuppression = hardClassifierSuppressionReason(plan);
        if (hardSuppression.isPresent()) {
            return suppressedByClassifier(mode, plan, ctx, hardSuppression.get());
        }

        EnumSet<DeterministicToolKind> matches = EnumSet.noneOf(DeterministicToolKind.class);
        boolean heuristicRouteUsed = false;
        addClassifierQueryTypeMatch(plan, matches);
        if (heuristicsAllowed(plan)) {
            heuristicRouteUsed = addHeuristicMatches(plan, matches);
        }

        if (matches.isEmpty()) {
            return new DeterministicToolDecision(
                    mode,
                    DeterministicToolOutcome.NOT_APPLICABLE,
                    false,
                    Optional.empty(),
                    List.of("tool_not_applicable"),
                    withRoutingTelemetry(normalizedInputs(ctx, plan), false, "", heuristicRouteUsed),
                    Optional.empty(),
                    Optional.empty());
        }
        if (matches.size() > 1) {
            String suppressionReason =
                    classifierUnavailable(plan) ? REASON_CLASSIFIER_UNAVAILABLE : REASON_HEURISTIC_AMBIGUOUS;
            return new DeterministicToolDecision(
                    mode,
                    DeterministicToolOutcome.NOT_APPLICABLE,
                    false,
                    Optional.empty(),
                    List.of("tool_ambiguous_match", matches.toString()),
                    withRoutingTelemetry(
                            normalizedInputs(ctx, plan),
                            classifierUnavailable(plan),
                            suppressionReason,
                            heuristicRouteUsed),
                    Optional.of("tool_ambiguous_match"),
                    Optional.empty());
        }

        DeterministicToolKind kind = matches.iterator().next();
        return new DeterministicToolDecision(
                mode,
                DeterministicToolOutcome.SELECTED,
                true,
                Optional.of(kind),
                List.of("selected=" + kind),
                withRoutingTelemetry(normalizedInputs(ctx, plan), false, "", heuristicRouteUsed),
                Optional.empty(),
                Optional.empty());
    }

    @Override
    public DeterministicToolDecision resolve(ExecutionContext ctx, QueryPlan plan, String workflowName) {
        return resolve(ctx, plan);
    }

    private static DeterministicToolDecision suppressedByClassifier(
            ToolExecutionMode mode, QueryPlan plan, ExecutionContext ctx, String reason) {
        return new DeterministicToolDecision(
                mode,
                DeterministicToolOutcome.NOT_APPLICABLE,
                false,
                Optional.empty(),
                List.of("route_suppressed_by_classifier", reason),
                withRoutingTelemetry(normalizedInputs(ctx, plan), true, reason, false),
                Optional.of(reason),
                Optional.empty());
    }

    private static Optional<String> hardClassifierSuppressionReason(QueryPlan plan) {
        return switch (plan.classifierStatus()) {
            case INVALID_OUTPUT -> Optional.of(REASON_CLASSIFIER_INVALID);
            case LOW_CONFIDENCE -> Optional.of(REASON_CLASSIFIER_LOW_CONFIDENCE);
            case OK -> {
                Optional<QueryType> cqt = plan.classifierQueryType();
                if (cqt.isPresent() && !DeterministicToolApplicability.isApplicableQueryType(cqt.get())) {
                    yield Optional.of(REASON_NON_APPLICABLE_TYPE);
                }
                yield Optional.empty();
            }
            default -> Optional.empty();
        };
    }

    private static boolean classifierUnavailable(QueryPlan plan) {
        return switch (plan.classifierStatus()) {
            case UNAVAILABLE, TIMEOUT, INVALID_REQUEST -> true;
            default -> false;
        };
    }

    private static boolean heuristicsAllowed(QueryPlan plan) {
        ClassifierStatus status = plan.classifierStatus();
        if (status == ClassifierStatus.OK) {
            return plan.classifierQueryType()
                    .map(DeterministicToolApplicability::isApplicableQueryType)
                    .orElse(true);
        }
        if (status == ClassifierStatus.UNAVAILABLE
                || status == ClassifierStatus.TIMEOUT
                || status == ClassifierStatus.INVALID_REQUEST) {
            return true;
        }
        return false;
    }

    private static boolean addHeuristicMatches(QueryPlan plan, EnumSet<DeterministicToolKind> matches) {
        int before = matches.size();
        if (matchesCountDocuments(plan)) {
            matches.add(DeterministicToolKind.COUNT_DOCUMENTS_TOOL);
        }
        if (matchesFindParagraph(plan)) {
            matches.add(DeterministicToolKind.FIND_PARAGRAPH_TOOL);
        }
        if (matchesGetField(plan)) {
            matches.add(DeterministicToolKind.GET_FIELD_TOOL);
        }
        if (matchesBoolean(plan)) {
            matches.add(DeterministicToolKind.BOOLEAN_QUERY_TOOL);
        }
        if (matchesCountAndExplain(plan)) {
            matches.add(DeterministicToolKind.COUNT_AND_EXPLAIN_TOOL);
        }
        return matches.size() > before;
    }

    private static void addClassifierQueryTypeMatch(QueryPlan p, EnumSet<DeterministicToolKind> matches) {
        if (p.classifierStatus() != ClassifierStatus.OK) {
            return;
        }
        Optional<QueryType> cqt = p.classifierQueryType();
        if (cqt.isEmpty()) {
            return;
        }
        Optional<DeterministicToolKind> kind = DeterministicToolApplicability.toolKindForQueryType(cqt.get());
        if (kind.isEmpty()) {
            return;
        }
        if (kind.get() == DeterministicToolKind.GET_FIELD_TOOL && !matchesGetField(p)) {
            return;
        }
        matches.add(kind.get());
    }

    private static boolean matchesCountDocuments(QueryPlan p) {
        return p.queryIntent() == QueryIntent.COUNT || p.expectedAnswerShape() == ExpectedAnswerShape.SCALAR_COUNT;
    }

    private static boolean matchesFindParagraph(QueryPlan p) {
        return p.queryIntent() == QueryIntent.FIND && p.expectedAnswerShape() == ExpectedAnswerShape.PARAGRAPH;
    }

    private static boolean matchesGetField(QueryPlan p) {
        boolean shapeOk =
                p.queryIntent() == QueryIntent.EXTRACT_FIELD || p.expectedAnswerShape() == ExpectedAnswerShape.FIELD_VALUE;
        if (!shapeOk) {
            return false;
        }
        String field = p.slots().get("field");
        return field != null && !field.isBlank();
    }

    private static boolean matchesBoolean(QueryPlan p) {
        return p.queryIntent() == QueryIntent.BOOLEAN_CHECK || p.expectedAnswerShape() == ExpectedAnswerShape.SCALAR_BOOLEAN;
    }

    private static boolean matchesCountAndExplain(QueryPlan p) {
        if (p.classifierQueryType().filter(qt -> qt == QueryType.COUNT_AND_EXPLAIN).isPresent()) {
            return true;
        }
        return p.queryIntent() == QueryIntent.COUNT && "true".equalsIgnoreCase(p.slots().getOrDefault("explain", ""));
    }

    private static Map<String, String> withRoutingTelemetry(
            Map<String, String> base, boolean suppressed, String reason, boolean heuristicRouteUsed) {
        Map<String, String> m = new LinkedHashMap<>(base);
        m.put("routeSuppressedByClassifier", Boolean.toString(suppressed));
        if (!reason.isBlank()) {
            m.put("routeSuppressedReason", reason);
        }
        m.put("heuristicRouteUsed", Boolean.toString(heuristicRouteUsed));
        return m;
    }

    private static Map<String, String> normalizedInputs(ExecutionContext ctx, QueryPlan plan) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("queryText", plan.rewrittenQueryText());
        m.put("correlationId", plan.correlationId());
        m.put("intent", plan.queryIntent().name());
        m.put("classifierStatus", plan.classifierStatus().name());
        for (var e : plan.slots().entrySet()) {
            m.put("slots." + e.getKey(), e.getValue());
        }
        var ner = plan.entityExtractionResult();
        if (!ner.dates().isEmpty()) {
            m.put("entities.dates", String.join(",", ner.dates()));
        }
        if (!ner.people().isEmpty()) {
            m.put("entities.people", String.join(",", ner.people()));
        }
        if (!ner.locations().isEmpty()) {
            m.put("entities.locations", String.join(",", ner.locations()));
        }
        if (!ner.topics().isEmpty()) {
            m.put("entities.topics", String.join(",", ner.topics()));
        }
        if (!ner.organizations().isEmpty()) {
            m.put("entities.organizations", String.join(",", ner.organizations()));
        }
        return m;
    }
}
