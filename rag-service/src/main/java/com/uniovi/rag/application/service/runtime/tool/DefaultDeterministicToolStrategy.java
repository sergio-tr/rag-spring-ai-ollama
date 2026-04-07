package com.uniovi.rag.application.service.runtime.tool;

import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolDecision;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolExecutionResult;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolOutcome;
import org.springframework.stereotype.Component;

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
    public DeterministicToolExecutionResult tryExecute(ExecutionContext ctx, QueryPlan plan, String workflowName) {
        DeterministicToolDecision decision = resolver.resolve(ctx, plan, workflowName);
        if (!decision.selected()) {
            return skippedFromDecision(decision);
        }
        return executor.execute(decision, ctx, plan);
    }

    private static DeterministicToolExecutionResult skippedFromDecision(DeterministicToolDecision d) {
        DeterministicToolOutcome outcome = d.outcome();
        return new DeterministicToolExecutionResult(
                Optional.empty(),
                outcome,
                false,
                "",
                Map.of(),
                List.copyOf(d.reasons()));
    }
}
