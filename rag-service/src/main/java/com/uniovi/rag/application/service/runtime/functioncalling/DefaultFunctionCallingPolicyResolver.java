package com.uniovi.rag.application.service.runtime.functioncalling;

import com.uniovi.rag.application.service.runtime.tool.DeterministicToolEvidenceEvaluator;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.functioncalling.FunctionCallingDecision;
import com.uniovi.rag.domain.runtime.functioncalling.FunctionCallingMode;
import com.uniovi.rag.domain.runtime.functioncalling.FunctionCallingOutcome;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolKind;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Resolves function-calling applicability and tool exposure. */
@Component
public class DefaultFunctionCallingPolicyResolver implements FunctionCallingPolicyResolver {

    @Override
    public Optional<FunctionCallingDecision> resolve(ExecutionContext ctx, QueryPlan plan) {
        RagConfig rag = ctx.resolved().toRagConfig();
        if (!rag.functionCallingEnabled()) {
            throw new IllegalStateException("FunctionCallingPolicyResolver must run only when functionCallingEnabled is true");
        }
        DeterministicToolEvidenceEvaluator.Evaluation evaluation = DeterministicToolEvidenceEvaluator.evaluate(plan);
        if (!evaluation.toolApplicabilityEligible() || evaluation.matchedKinds().isEmpty()) {
            return Optional.empty();
        }
        List<DeterministicToolKind> kinds = new ArrayList<>(evaluation.matchedKinds());
        kinds.sort(Comparator.naturalOrder());
        Map<String, String> normalized = normalizedInputs(plan);
        FunctionCallingDecision decision =
                new FunctionCallingDecision(
                        FunctionCallingMode.ENABLED,
                        FunctionCallingOutcome.NOT_APPLICABLE,
                        true,
                        kinds,
                        List.of("exposed=" + kinds, "evidence=" + evaluation.evidenceLevel()),
                        Optional.empty(),
                        plan.rewrittenQueryText(),
                        normalized);
        return Optional.of(decision);
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
