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
        DeterministicToolExecutionResult executed = executor.execute(decision, ctx, plan);
        return withDecisionTelemetry(decision, executed);
    }

    private static DeterministicToolExecutionResult withDecisionTelemetry(
            DeterministicToolDecision decision, DeterministicToolExecutionResult executed) {
        List<String> notes = new ArrayList<>(decision.reasons());
        appendRoutingTelemetry(notes, decision.normalizedInputs());
        notes.addAll(executed.traceNotes());
        return new DeterministicToolExecutionResult(
                executed.toolKind(),
                executed.outcome(),
                executed.success(),
                executed.answerText(),
                executed.normalizedPayload(),
                List.copyOf(notes));
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
        putRoutingNote(notes, inputs, "deterministicEvidenceLevel");
        putRoutingNote(notes, inputs, "routingOracleUsed");
        putRoutingNote(notes, inputs, "toolApplicabilityEligible");
        putRoutingNote(notes, inputs, "toolFallbackReason");
    }

    private static void putRoutingNote(List<String> notes, Map<String, String> inputs, String key) {
        String value = inputs.get(key);
        if (value != null && !value.isBlank()) {
            notes.add(key + "=" + value);
        }
    }
}
