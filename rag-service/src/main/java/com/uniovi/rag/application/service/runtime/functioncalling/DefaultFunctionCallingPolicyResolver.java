package com.uniovi.rag.application.service.runtime.functioncalling;

import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.functioncalling.FunctionCallingDecision;
import com.uniovi.rag.domain.runtime.functioncalling.FunctionCallingMode;
import com.uniovi.rag.domain.runtime.functioncalling.FunctionCallingOutcome;
import com.uniovi.rag.domain.runtime.query.ExpectedAnswerShape;
import com.uniovi.rag.domain.runtime.query.QueryIntent;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolKind;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * §10.4 applicability matrix for FC (independent of P7 deterministic resolver matching rules).
 */
@Component
public class DefaultFunctionCallingPolicyResolver implements FunctionCallingPolicyResolver {

    @Override
    public Optional<FunctionCallingDecision> resolve(ExecutionContext ctx, QueryPlan plan, String workflowName) {
        RagConfig rag = ctx.resolved().toRagConfig();
        if (!rag.functionCallingEnabled()) {
            throw new IllegalStateException("FunctionCallingPolicyResolver must run only when functionCallingEnabled is true");
        }
        FunctionCallingMode mode = FunctionCallingMode.ENABLED;
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
            return Optional.empty();
        }
        List<DeterministicToolKind> kinds = new ArrayList<>(matches);
        kinds.sort(Enum::compareTo);
        Map<String, String> normalized = normalizedInputs(plan);
        FunctionCallingDecision decision =
                new FunctionCallingDecision(
                        mode,
                        FunctionCallingOutcome.NOT_APPLICABLE,
                        true,
                        kinds,
                        List.of("exposed=" + kinds),
                        Optional.empty(),
                        plan.rewrittenQueryText(),
                        normalized);
        return Optional.of(decision);
    }

    private static boolean matchesCountDocuments(QueryPlan p) {
        return p.queryIntent() == QueryIntent.COUNT || p.expectedAnswerShape() == ExpectedAnswerShape.SCALAR_COUNT;
    }

    private static boolean matchesFindParagraph(QueryPlan p) {
        return p.queryIntent() == QueryIntent.FIND || p.expectedAnswerShape() == ExpectedAnswerShape.PARAGRAPH;
    }

    private static boolean matchesGetField(QueryPlan p) {
        boolean shapeOk =
                p.queryIntent() == QueryIntent.EXTRACT_FIELD || p.expectedAnswerShape() == ExpectedAnswerShape.FIELD_VALUE;
        return shapeOk && firstNormalizedTargetAttribute(p).isPresent();
    }

    private static Optional<String> firstNormalizedTargetAttribute(QueryPlan p) {
        return p.targetAttributes().stream()
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .findFirst();
    }

    private static boolean matchesBoolean(QueryPlan p) {
        return p.queryIntent() == QueryIntent.BOOLEAN_CHECK || p.expectedAnswerShape() == ExpectedAnswerShape.SCALAR_BOOLEAN;
    }

    private static boolean matchesCountAndExplain(QueryPlan p) {
        if (p.classifierQueryType().filter(qt -> qt == QueryType.COUNT_AND_EXPLAIN).isPresent()) {
            return true;
        }
        if (p.queryIntent() != QueryIntent.COUNT) {
            return false;
        }
        if ("true".equalsIgnoreCase(p.slots().getOrDefault("explain", ""))) {
            return true;
        }
        return p.targetAttributes().stream()
                .map(String::trim)
                .anyMatch(a -> a.toLowerCase().contains("explain"));
    }

    private static Map<String, String> normalizedInputs(QueryPlan plan) {
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
