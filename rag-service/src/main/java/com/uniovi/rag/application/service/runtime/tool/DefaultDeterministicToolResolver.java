package com.uniovi.rag.application.service.runtime.tool;

import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.query.AmbiguityStatus;
import com.uniovi.rag.domain.runtime.query.ClassifierStatus;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.tool.DeterministicEvidenceLevel;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolDecision;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolKind;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolOutcome;
import com.uniovi.rag.domain.runtime.tool.ToolExecutionMode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
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
    public static final String REASON_CLASSIFIER_HEURISTIC_CONFLICT = "classifier_heuristic_conflict";
    public static final String REASON_UNSUPPORTED_QUERY_TYPE = "unsupported_query_type";
    public static final String REASON_MISSING_REQUIRED_ARGUMENTS = "missing_required_tool_arguments";

    @Override
    public DeterministicToolDecision resolve(ExecutionContext ctx, QueryPlan plan) {
        RagConfig rag = ctx.resolved().toRagConfig();
        ToolExecutionMode mode = rag.toolsEnabled() ? ToolExecutionMode.ENABLED : ToolExecutionMode.DISABLED;
        if (mode == ToolExecutionMode.DISABLED) {
            return disabledDecision(mode, ctx, plan, List.of("toolsEnabled=false"), "tool_disabled_by_config");
        }
        if (!ambiguityAllowsDeterministicTool(plan)) {
            return new DeterministicToolDecision(
                    mode,
                    DeterministicToolOutcome.SUPPRESSED_BY_AMBIGUITY,
                    false,
                    Optional.empty(),
                    List.of("ambiguityStatus=" + plan.ambiguityAssessment().status()),
                    normalizedInputs(ctx, plan, DeterministicEvidenceLevel.NONE, false, false, false),
                    Optional.of("tool_suppressed_by_ambiguity"),
                    Optional.empty());
        }

        Optional<DeterministicToolDecision> structuredGetField = structuredGetFieldDecision(mode, ctx, plan);
        if (structuredGetField.isPresent()) {
            return structuredGetField.get();
        }

        DeterministicToolEvidenceEvaluator.Evaluation evaluation = DeterministicToolEvidenceEvaluator.evaluate(plan);
        Optional<String> classifierVeto = DeterministicToolEvidenceEvaluator.classifierVetoReason(plan, evaluation);
        if (classifierVeto.isPresent()) {
            return suppressedByClassifier(mode, plan, ctx, classifierVeto.get(), evaluation);
        }

        EnumSet<DeterministicToolKind> matches = evaluation.matchedKinds();
        if (matches.isEmpty()) {
            return notApplicable(
                    mode,
                    ctx,
                    plan,
                    evaluation,
                    List.of("tool_not_applicable"),
                    REASON_UNSUPPORTED_QUERY_TYPE);
        }
        if (matches.size() > 1) {
            String suppressionReason =
                    evaluation.heuristicRouteUsed() && !evaluation.routingOracleUsed()
                            ? REASON_HEURISTIC_AMBIGUOUS
                            : REASON_HEURISTIC_AMBIGUOUS;
            return notApplicable(
                    mode,
                    ctx,
                    plan,
                    evaluation,
                    List.of("tool_ambiguous_match", matches.toString()),
                    suppressionReason);
        }

        DeterministicToolKind kind = matches.iterator().next();
        if (!DeterministicToolEvidenceEvaluator.requiredArgumentsPresent(kind, plan)) {
            return notApplicable(
                    mode,
                    ctx,
                    plan,
                    evaluation,
                    List.of("missing_required_tool_arguments", "kind=" + kind),
                    REASON_MISSING_REQUIRED_ARGUMENTS);
        }

        if (evaluation.evidenceLevel() != DeterministicEvidenceLevel.STRONG
                && evaluation.evidenceLevel() != DeterministicEvidenceLevel.ORACLE) {
            return notApplicable(
                    mode,
                    ctx,
                    plan,
                    evaluation,
                    List.of("insufficient_deterministic_evidence", "level=" + evaluation.evidenceLevel()),
                    REASON_UNSUPPORTED_QUERY_TYPE);
        }

        return new DeterministicToolDecision(
                mode,
                DeterministicToolOutcome.SELECTED,
                true,
                Optional.of(kind),
                List.of("selected=" + kind),
                routingTelemetry(ctx, plan, evaluation, false, ""),
                Optional.empty(),
                Optional.empty());
    }

    @Override
    public DeterministicToolDecision resolve(ExecutionContext ctx, QueryPlan plan, String workflowName) {
        return resolve(ctx, plan);
    }

    private static boolean ambiguityAllowsDeterministicTool(QueryPlan plan) {
        return switch (plan.ambiguityAssessment().status()) {
            case SUFFICIENT -> true;
            case CONFLICTING_CUES ->
                    plan.classifierStatus() == ClassifierStatus.OK
                            && plan.classifierQueryType().filter(qt -> qt == QueryType.GET_FIELD).isPresent()
                            && DeterministicToolEvidenceEvaluator.requiredArgumentsPresent(
                                    DeterministicToolKind.GET_FIELD_TOOL, plan);
            default -> false;
        };
    }

    private static Optional<DeterministicToolDecision> structuredGetFieldDecision(
            ToolExecutionMode mode, ExecutionContext ctx, QueryPlan plan) {
        if (plan.classifierStatus() != ClassifierStatus.OK) {
            return Optional.empty();
        }
        if (plan.classifierQueryType().filter(qt -> qt == QueryType.GET_FIELD).isEmpty()) {
            return Optional.empty();
        }
        if (!DeterministicToolEvidenceEvaluator.requiredArgumentsPresent(DeterministicToolKind.GET_FIELD_TOOL, plan)) {
            return Optional.empty();
        }
        DeterministicToolEvidenceEvaluator.Evaluation evaluation = DeterministicToolEvidenceEvaluator.evaluate(plan);
        return Optional.of(
                new DeterministicToolDecision(
                        mode,
                        DeterministicToolOutcome.SELECTED,
                        true,
                        Optional.of(DeterministicToolKind.GET_FIELD_TOOL),
                        List.of("structured_get_field_classifier_route", "selected=GET_FIELD_TOOL"),
                        routingTelemetry(
                                ctx,
                                plan,
                                evaluation,
                                false,
                                ""),
                        Optional.empty(),
                        Optional.empty()));
    }

    private static DeterministicToolDecision disabledDecision(
            ToolExecutionMode mode,
            ExecutionContext ctx,
            QueryPlan plan,
            List<String> reasons,
            String suppression) {
        return new DeterministicToolDecision(
                mode,
                DeterministicToolOutcome.DISABLED_BY_CONFIG,
                false,
                Optional.empty(),
                reasons,
                normalizedInputs(ctx, plan, DeterministicEvidenceLevel.NONE, false, false, false),
                Optional.of(suppression),
                Optional.empty());
    }

    private static DeterministicToolDecision suppressedByClassifier(
            ToolExecutionMode mode,
            QueryPlan plan,
            ExecutionContext ctx,
            String reason,
            DeterministicToolEvidenceEvaluator.Evaluation evaluation) {
        return new DeterministicToolDecision(
                mode,
                DeterministicToolOutcome.NOT_APPLICABLE,
                false,
                Optional.empty(),
                List.of("route_suppressed_by_classifier", reason),
                routingTelemetry(ctx, plan, evaluation, true, reason),
                Optional.of(reason),
                Optional.empty());
    }

    private static DeterministicToolDecision notApplicable(
            ToolExecutionMode mode,
            ExecutionContext ctx,
            QueryPlan plan,
            DeterministicToolEvidenceEvaluator.Evaluation evaluation,
            List<String> reasons,
            String fallbackReason) {
        return new DeterministicToolDecision(
                mode,
                DeterministicToolOutcome.NOT_APPLICABLE,
                false,
                Optional.empty(),
                reasons,
                routingTelemetry(ctx, plan, evaluation, false, fallbackReason),
                Optional.of(fallbackReason),
                Optional.empty());
    }

    private static Map<String, String> routingTelemetry(
            ExecutionContext ctx,
            QueryPlan plan,
            DeterministicToolEvidenceEvaluator.Evaluation evaluation,
            boolean suppressedByClassifier,
            String fallbackReason) {
        return normalizedInputs(
                ctx,
                plan,
                evaluation.evidenceLevel(),
                evaluation.routingOracleUsed(),
                evaluation.heuristicRouteUsed(),
                evaluation.toolApplicabilityEligible(),
                suppressedByClassifier,
                fallbackReason);
    }

    private static Map<String, String> normalizedInputs(
            ExecutionContext ctx,
            QueryPlan plan,
            DeterministicEvidenceLevel evidenceLevel,
            boolean routingOracleUsed,
            boolean heuristicRouteUsed,
            boolean toolApplicabilityEligible) {
        return normalizedInputs(
                ctx, plan, evidenceLevel, routingOracleUsed, heuristicRouteUsed, toolApplicabilityEligible, false, "");
    }

    private static Map<String, String> normalizedInputs(
            ExecutionContext ctx,
            QueryPlan plan,
            DeterministicEvidenceLevel evidenceLevel,
            boolean routingOracleUsed,
            boolean heuristicRouteUsed,
            boolean toolApplicabilityEligible,
            boolean suppressedByClassifier,
            String fallbackReason) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("queryText", plan.rewrittenQueryText());
        m.put("correlationId", plan.correlationId());
        m.put("intent", plan.queryIntent().name());
        m.put("classifierStatus", plan.classifierStatus().name());
        m.put("deterministicEvidenceLevel", evidenceLevel.name());
        m.put("routingOracleUsed", Boolean.toString(routingOracleUsed));
        m.put("toolApplicabilityEligible", Boolean.toString(toolApplicabilityEligible));
        m.put("routeSuppressedByClassifier", Boolean.toString(suppressedByClassifier));
        if (suppressedByClassifier && !fallbackReason.isBlank()) {
            m.put("routeSuppressedReason", fallbackReason);
        }
        if (!fallbackReason.isBlank()) {
            m.put("toolFallbackReason", fallbackReason);
        }
        m.put("heuristicRouteUsed", Boolean.toString(heuristicRouteUsed));
        for (var entry : plan.slots().entrySet()) {
            m.put("slots." + entry.getKey(), entry.getValue());
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
