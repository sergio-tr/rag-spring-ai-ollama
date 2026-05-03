package com.uniovi.rag.application.service.runtime.functioncalling;

import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.engine.ExecutionStageTrace;
import com.uniovi.rag.domain.runtime.functioncalling.FunctionCallingDecision;
import com.uniovi.rag.domain.runtime.functioncalling.FunctionCallingExecutionResult;
import com.uniovi.rag.domain.runtime.functioncalling.FunctionCallingMode;
import com.uniovi.rag.domain.runtime.functioncalling.FunctionCallingOutcome;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolKind;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DefaultFunctionCallingStrategyTest {

    @Test
    void tryExecute_prependsPolicyStageTrace_andKeepsInnerTraces() {
        FunctionCallingExecutor executor = mock(FunctionCallingExecutor.class);
        DefaultFunctionCallingStrategy s = new DefaultFunctionCallingStrategy(executor);

        ExecutionContext ctx = mock(ExecutionContext.class);
        QueryPlan plan = mock(QueryPlan.class);
        FunctionCallingDecision decision =
                new FunctionCallingDecision(
                        FunctionCallingMode.ENABLED,
                        FunctionCallingOutcome.EXECUTED_SUCCESS,
                        true,
                        List.of(DeterministicToolKind.GET_FIELD_TOOL),
                        List.of("r"),
                        Optional.empty(),
                        "q",
                        Map.of());

        FunctionCallingExecutionResult inner =
                new FunctionCallingExecutionResult(
                        FunctionCallingOutcome.EXECUTED_SUCCESS,
                        true,
                        Optional.of(DeterministicToolKind.GET_FIELD_TOOL),
                        "ans",
                        Map.of("k", "v"),
                        List.of("n"),
                        false,
                        List.of(new ExecutionStageTrace("inner", 1, null, "")));

        when(executor.run(ctx, plan, decision)).thenReturn(inner);

        FunctionCallingExecutionResult out = s.tryExecute(ctx, plan, decision);

        assertThat(out.stageTraces()).hasSize(2);
        assertThat(out.stageTraces().getFirst().stageName()).isEqualTo("function_calling_policy");
        assertThat(out.stageTraces().get(1).stageName()).isEqualTo("inner");
        assertThat(out.answerText()).isEqualTo("ans");
        assertThat(out.normalizedPayload()).containsEntry("k", "v");
    }
}

