package com.uniovi.rag.application.service.runtime.tool;

import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolDecision;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolExecutionResult;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolOutcome;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class DefaultDeterministicToolStrategy implements DeterministicToolStrategy {

    private final DeterministicToolResolver resolver;
    private final DeterministicToolExecutor executor;

    public DefaultDeterministicToolStrategy(DeterministicToolResolver resolver, DeterministicToolExecutor executor) {
        this.resolver = resolver;
        this.executor = executor;
    }

    @Override
    public DeterministicToolExecutionResult tryExecute(ExecutionContext ctx, QueryPlan plan) {
        DeterministicToolDecision decision = resolver.resolve(ctx, plan);
        if (!decision.selected()) {
            return skippedFromDecision(decision);
        }
        return executor.execute(decision, ctx, plan);
    }

    private static DeterministicToolExecutionResult skippedFromDecision(DeterministicToolDecision d) {
        DeterministicToolOutcome outcome = d.outcome();
        List<String> notes = new ArrayList<>(d.reasons());
        appendRoutingTelemetry(notes, d.normalizedInputs());
        return new DeterministicToolExecutionResult(
                Optional.empty(),
                outcome,
                false,
                "",
                Map.of(),
                List.copyOf(notes));
    }

    private static void appendRoutingTelemetry(List<String> notes, Map<String, String> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            return;
        }
        putRoutingNote(notes, inputs, "routeSuppressedByClassifier");
        putRoutingNote(notes, inputs, "routeSuppressedReason");
        putRoutingNote(notes, inputs, "heuristicRouteUsed");
    }

    private static void putRoutingNote(List<String> notes, Map<String, String> inputs, String key) {
        String value = inputs.get(key);
        if (value != null && !value.isBlank()) {
            notes.add(key + "=" + value);
        }
    }
}
