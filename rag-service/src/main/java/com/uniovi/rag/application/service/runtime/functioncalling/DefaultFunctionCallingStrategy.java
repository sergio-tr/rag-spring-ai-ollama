package com.uniovi.rag.application.service.runtime.functioncalling;

import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.engine.ExecutionStageOutcome;
import com.uniovi.rag.domain.runtime.engine.ExecutionStageTrace;
import com.uniovi.rag.domain.runtime.functioncalling.FunctionCallingDecision;
import com.uniovi.rag.domain.runtime.functioncalling.FunctionCallingExecutionResult;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class DefaultFunctionCallingStrategy implements FunctionCallingStrategy {

    private final FunctionCallingExecutor executor;

    public DefaultFunctionCallingStrategy(FunctionCallingExecutor executor) {
        this.executor = executor;
    }

    @Override
    public FunctionCallingExecutionResult tryExecute(
            ExecutionContext ctx, QueryPlan plan, String workflowName, FunctionCallingDecision decision) {
        List<ExecutionStageTrace> stages = new ArrayList<>();
        stages.add(
                new ExecutionStageTrace(
                        "function_calling_policy",
                        0L,
                        ExecutionStageOutcome.SUCCESS,
                        "exposed=" + decision.exposedToolKinds()));
        FunctionCallingExecutionResult inner = executor.run(ctx, plan, decision);
        stages.addAll(inner.stageTraces());
        return new FunctionCallingExecutionResult(
                inner.outcome(),
                inner.success(),
                inner.selectedToolKind(),
                inner.answerText(),
                inner.normalizedPayload(),
                inner.traceNotes(),
                inner.shortCircuited(),
                stages);
    }
}
