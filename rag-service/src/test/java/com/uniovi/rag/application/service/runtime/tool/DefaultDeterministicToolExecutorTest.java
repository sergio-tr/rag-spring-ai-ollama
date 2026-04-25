package com.uniovi.rag.application.service.runtime.tool;

import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolDecision;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolExecutionResult;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolKind;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolOutcome;
import com.uniovi.rag.domain.runtime.tool.MeetingMinutesToolRawResult;
import com.uniovi.rag.domain.runtime.tool.ToolExecutionMode;
import com.uniovi.rag.tool.ToolResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultDeterministicToolExecutorTest {

    @Mock private MeetingMinutesToolExecutionCore meetingMinutesToolExecutionCore;
    @Mock private DeterministicToolResultMapper resultMapper;
    @Mock private ExecutionContext ctx;
    @Mock private QueryPlan plan;

    @Test
    void execute_skipsWhenNotSelected() {
        DefaultDeterministicToolExecutor ex =
                new DefaultDeterministicToolExecutor(meetingMinutesToolExecutionCore, resultMapper);
        DeterministicToolDecision d =
                new DeterministicToolDecision(
                        ToolExecutionMode.ENABLED,
                        DeterministicToolOutcome.NOT_ATTEMPTED,
                        false,
                        Optional.empty(),
                        List.of("n"),
                        Map.of(),
                        Optional.empty(),
                        Optional.empty());
        DeterministicToolExecutionResult r = ex.execute(d, ctx, plan);
        assertThat(r.outcome()).isEqualTo(DeterministicToolOutcome.NOT_ATTEMPTED);
    }

    @Test
    void execute_returnsInfraFailure_onMissingTool() {
        when(meetingMinutesToolExecutionCore.execute(any(), any(), any()))
                .thenReturn(MeetingMinutesToolRawResult.missingTool(DeterministicToolKind.COUNT_DOCUMENTS_TOOL));
        DefaultDeterministicToolExecutor ex =
                new DefaultDeterministicToolExecutor(meetingMinutesToolExecutionCore, resultMapper);
        DeterministicToolDecision d =
                new DeterministicToolDecision(
                        ToolExecutionMode.ENABLED,
                        DeterministicToolOutcome.SELECTED,
                        true,
                        Optional.of(DeterministicToolKind.COUNT_DOCUMENTS_TOOL),
                        List.of(),
                        Map.of(),
                        Optional.empty(),
                        Optional.empty());
        DeterministicToolExecutionResult r = ex.execute(d, ctx, plan);
        assertThat(r.outcome()).isEqualTo(DeterministicToolOutcome.EXECUTED_FAILED_INFRA);
        assertThat(r.normalizedPayload()).containsKey("queryType");
    }

    @Test
    void execute_mapsSuccess_whenMapperReturnsOutput() {
        ToolResult toolResult = org.mockito.Mockito.mock(ToolResult.class);
        when(meetingMinutesToolExecutionCore.execute(any(), any(), any()))
                .thenReturn(
                        MeetingMinutesToolRawResult.ok(DeterministicToolKind.COUNT_DOCUMENTS_TOOL, toolResult));
        when(resultMapper.map(toolResult, DeterministicToolKind.COUNT_DOCUMENTS_TOOL))
                .thenReturn(
                        new MappedToolOutput(
                                DeterministicToolKind.COUNT_DOCUMENTS_TOOL, "answer", Map.of("k", "v")));
        DefaultDeterministicToolExecutor ex =
                new DefaultDeterministicToolExecutor(meetingMinutesToolExecutionCore, resultMapper);
        DeterministicToolDecision d =
                new DeterministicToolDecision(
                        ToolExecutionMode.ENABLED,
                        DeterministicToolOutcome.SELECTED,
                        true,
                        Optional.of(DeterministicToolKind.COUNT_DOCUMENTS_TOOL),
                        List.of(),
                        Map.of(),
                        Optional.empty(),
                        Optional.empty());
        DeterministicToolExecutionResult r = ex.execute(d, ctx, plan);
        assertThat(r.outcome()).isEqualTo(DeterministicToolOutcome.EXECUTED_SUCCESS);
        assertThat(r.answerText()).isEqualTo("answer");
    }

    @Test
    void execute_returnsInfraFailure_onRuntimeFailure() {
        when(meetingMinutesToolExecutionCore.execute(any(), any(), any()))
                .thenReturn(
                        MeetingMinutesToolRawResult.runtimeFailure(
                                DeterministicToolKind.COUNT_DOCUMENTS_TOOL, "exploded"));
        DefaultDeterministicToolExecutor ex =
                new DefaultDeterministicToolExecutor(meetingMinutesToolExecutionCore, resultMapper);
        DeterministicToolDecision d =
                new DeterministicToolDecision(
                        ToolExecutionMode.ENABLED,
                        DeterministicToolOutcome.SELECTED,
                        true,
                        Optional.of(DeterministicToolKind.COUNT_DOCUMENTS_TOOL),
                        List.of(),
                        Map.of(),
                        Optional.empty(),
                        Optional.empty());
        DeterministicToolExecutionResult r = ex.execute(d, ctx, plan);
        assertThat(r.outcome()).isEqualTo(DeterministicToolOutcome.EXECUTED_FAILED_INFRA);
        assertThat(r.normalizedPayload().get("message")).isEqualTo("exploded");
    }

    @Test
    void execute_returnsInfraFailure_whenMapperReturnsNull() {
        ToolResult toolResult = org.mockito.Mockito.mock(ToolResult.class);
        when(meetingMinutesToolExecutionCore.execute(any(), any(), any()))
                .thenReturn(
                        MeetingMinutesToolRawResult.ok(DeterministicToolKind.COUNT_DOCUMENTS_TOOL, toolResult));
        when(resultMapper.map(toolResult, DeterministicToolKind.COUNT_DOCUMENTS_TOOL)).thenReturn(null);
        DefaultDeterministicToolExecutor ex =
                new DefaultDeterministicToolExecutor(meetingMinutesToolExecutionCore, resultMapper);
        DeterministicToolDecision d =
                new DeterministicToolDecision(
                        ToolExecutionMode.ENABLED,
                        DeterministicToolOutcome.SELECTED,
                        true,
                        Optional.of(DeterministicToolKind.COUNT_DOCUMENTS_TOOL),
                        List.of(),
                        Map.of(),
                        Optional.empty(),
                        Optional.empty());
        DeterministicToolExecutionResult r = ex.execute(d, ctx, plan);
        assertThat(r.outcome()).isEqualTo(DeterministicToolOutcome.EXECUTED_FAILED_INFRA);
    }
}
