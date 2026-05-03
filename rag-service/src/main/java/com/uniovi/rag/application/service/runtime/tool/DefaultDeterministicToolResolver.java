package com.uniovi.rag.application.service.runtime.tool;

import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.query.AmbiguityStatus;
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

/**
 * Frozen P7 resolution matrix: single match selects; zero → not applicable; multiple → ambiguous (no execution).
 */
@Component
public class DefaultDeterministicToolResolver implements DeterministicToolResolver {

    public static final String FALLBACK_POLICY_INFRA = "tool_fallback_to_workflow";

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

        EnumSet<DeterministicToolKind> matches = EnumSet.noneOf(DeterministicToolKind.class);
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

        if (matches.isEmpty()) {
            return new DeterministicToolDecision(
                    mode,
                    DeterministicToolOutcome.NOT_APPLICABLE,
                    false,
                    Optional.empty(),
                    List.of("tool_not_applicable"),
                    normalizedInputs(ctx, plan),
                    Optional.empty(),
                    Optional.empty());
        }
        if (matches.size() > 1) {
            return new DeterministicToolDecision(
                    mode,
                    DeterministicToolOutcome.NOT_APPLICABLE,
                    false,
                    Optional.empty(),
                    List.of("tool_ambiguous_match", matches.toString()),
                    normalizedInputs(ctx, plan),
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
                normalizedInputs(ctx, plan),
                Optional.empty(),
                Optional.empty());
    }

    /**
     * Backwards-compatible overload for pre-P13 call sites that passed a workflow name.
     * The deterministic resolver is workflow-independent; the value is ignored.
     */
    @Override
    public DeterministicToolDecision resolve(ExecutionContext ctx, QueryPlan plan, String workflowName) {
        return resolve(ctx, plan);
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

    private static Map<String, String> normalizedInputs(ExecutionContext ctx, QueryPlan plan) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("queryText", plan.rewrittenQueryText());
        m.put("correlationId", plan.correlationId());
        m.put("intent", plan.queryIntent().name());
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
