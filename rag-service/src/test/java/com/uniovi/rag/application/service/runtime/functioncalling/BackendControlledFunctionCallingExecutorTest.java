package com.uniovi.rag.application.service.runtime.functioncalling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.uniovi.rag.application.service.runtime.tool.MeetingMinutesToolExecutionCore;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.functioncalling.FunctionCallProposal;
import com.uniovi.rag.domain.runtime.functioncalling.FunctionCallingDecision;
import com.uniovi.rag.domain.runtime.functioncalling.FunctionCallingExecutionResult;
import com.uniovi.rag.domain.runtime.functioncalling.FunctionCallingMode;
import com.uniovi.rag.domain.runtime.functioncalling.FunctionCallingOutcome;
import com.uniovi.rag.domain.runtime.functioncalling.FunctionProposalMode;
import com.uniovi.rag.domain.runtime.functioncalling.FunctionProposalSource;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolKind;
import com.uniovi.rag.domain.runtime.tool.MeetingMinutesToolRawResult;
import com.uniovi.rag.tool.ToolResult;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BackendControlledFunctionCallingExecutorTest {

    @Test
    void executesValidProposalAndShortCircuits() {
        MeetingMinutesToolExecutionCore core = mock(MeetingMinutesToolExecutionCore.class);
        FunctionCallingResultMapper mapper = mock(FunctionCallingResultMapper.class);
        BackendControlledFunctionCallingExecutor executor =
                new BackendControlledFunctionCallingExecutor(core, mapper);

        ExecutionContext ctx = mock(ExecutionContext.class);
        QueryPlan plan = mock(QueryPlan.class);
        FunctionCallingDecision decision =
                new FunctionCallingDecision(
                        FunctionCallingMode.ENABLED,
                        FunctionCallingOutcome.NOT_APPLICABLE,
                        true,
                        List.of(DeterministicToolKind.COUNT_DOCUMENTS_TOOL),
                        List.of(),
                        Optional.empty(),
                        "q",
                        Map.of());
        FunctionCallProposal proposal =
                new FunctionCallProposal(
                        FunctionProposalMode.BACKEND_DETERMINISTIC,
                        "countDocuments",
                        Optional.of(DeterministicToolKind.COUNT_DOCUMENTS_TOOL),
                        "{\"query\":\"q\"}",
                        true,
                        Optional.empty(),
                        false,
                        false,
                        Optional.of(1.0),
                        Optional.empty(),
                        FunctionProposalSource.QUERY_SHAPE);

        ToolResult raw = mock(ToolResult.class);
        when(core.execute(eq(DeterministicToolKind.COUNT_DOCUMENTS_TOOL), eq(ctx), eq(plan)))
                .thenReturn(MeetingMinutesToolRawResult.ok(DeterministicToolKind.COUNT_DOCUMENTS_TOOL, raw));
        when(mapper.stableAnswerText(raw, DeterministicToolKind.COUNT_DOCUMENTS_TOOL)).thenReturn("42");
        when(mapper.normalizedPayload(raw, DeterministicToolKind.COUNT_DOCUMENTS_TOOL)).thenReturn(Map.of("count", 42));

        FunctionCallingExecutionResult result = executor.run(ctx, plan, decision, proposal);

        assertThat(result.outcome()).isEqualTo(FunctionCallingOutcome.EXECUTED_SUCCESS);
        assertThat(result.shortCircuited()).isTrue();
        assertThat(result.answerText()).isEqualTo("42");
        assertThat(result.backendFunctionCallAttempted()).isTrue();
        assertThat(result.nativeProviderFunctionCallAttempted()).isFalse();
    }

    @Test
    void rejectsInvalidProposalWithoutExecutingTool() {
        MeetingMinutesToolExecutionCore core = mock(MeetingMinutesToolExecutionCore.class);
        FunctionCallingResultMapper mapper = mock(FunctionCallingResultMapper.class);
        BackendControlledFunctionCallingExecutor executor =
                new BackendControlledFunctionCallingExecutor(core, mapper);

        FunctionCallProposal proposal =
                new FunctionCallProposal(
                        FunctionProposalMode.BACKEND_DETERMINISTIC,
                        "countDocuments",
                        Optional.of(DeterministicToolKind.COUNT_DOCUMENTS_TOOL),
                        "{}",
                        false,
                        Optional.of("invalid"),
                        false,
                        false,
                        Optional.empty(),
                        Optional.empty(),
                        FunctionProposalSource.QUERY_SHAPE);

        FunctionCallingExecutionResult result =
                executor.run(mock(ExecutionContext.class), mock(QueryPlan.class), mock(FunctionCallingDecision.class), proposal);

        assertThat(result.outcome()).isEqualTo(FunctionCallingOutcome.INVALID_MODEL_OUTPUT);
        assertThat(result.backendFunctionCallAttempted()).isTrue();
    }
}
