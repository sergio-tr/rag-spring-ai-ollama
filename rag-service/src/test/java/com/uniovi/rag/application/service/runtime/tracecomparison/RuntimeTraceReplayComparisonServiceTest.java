package com.uniovi.rag.application.service.runtime.tracecomparison;

import com.uniovi.rag.application.service.runtime.tracequery.RuntimeTraceQueryService;
import com.uniovi.rag.application.service.runtime.tracereplay.RuntimeTraceReplayService;
import com.uniovi.rag.domain.runtime.engine.ExecutionTrace;
import com.uniovi.rag.domain.runtime.tracecomparison.RuntimeTraceReplayAnswerComparisonStatus;
import com.uniovi.rag.domain.runtime.tracecomparison.RuntimeTraceReplayComparisonOutcome;
import com.uniovi.rag.domain.runtime.tracecomparison.RuntimeTraceReplayComparisonRequest;
import com.uniovi.rag.domain.runtime.tracecomparison.RuntimeTraceReplayFieldMismatch;
import com.uniovi.rag.domain.runtime.tracereplay.RuntimeTraceReplayOutcome;
import com.uniovi.rag.domain.runtime.tracereplay.RuntimeTraceReplayResult;
import com.uniovi.rag.infrastructure.persistence.mapper.RuntimeExecutionTraceEntityMapper;
import com.uniovi.rag.interfaces.rest.NotFoundException;
import com.uniovi.rag.interfaces.rest.dto.RuntimeExecutionTraceDetailDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuntimeTraceReplayComparisonServiceTest {

    @Mock
    private RuntimeTraceQueryService traceQueryService;

    @Mock
    private RuntimeTraceReplayService replayService;

    @Mock
    private RuntimeTraceReplayComparator comparator;

    private RuntimeTraceReplayComparisonService service;

    private static final UUID UID = UUID.randomUUID();
    private static final UUID TID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new RuntimeTraceReplayComparisonService(traceQueryService, replayService, comparator);
    }

    @Test
    void original_not_found_maps_outcome() {
        when(traceQueryService.getTraceDetailById(UID, TID)).thenThrow(new NotFoundException("missing"));
        var req = RuntimeTraceReplayComparisonRequest.byTraceId(UID, TID);
        var r = service.compare(req);
        assertThat(r.runtimeTraceReplayComparisonOutcome())
                .isEqualTo(RuntimeTraceReplayComparisonOutcome.ORIGINAL_TRACE_NOT_FOUND_OR_INACCESSIBLE);
        verify(replayService, never()).replay(any());
        verify(comparator, never()).compare(any(), any(), any());
    }

    @Test
    void replay_unsupported_never_calls_comparator() {
        RuntimeExecutionTraceDetailDto dto = minimalDto();
        when(traceQueryService.getTraceDetailById(UID, TID)).thenReturn(dto);
        when(replayService.replay(any()))
                .thenReturn(RuntimeTraceReplayResult.unsupported(RuntimeTraceReplayOutcome.UNSUPPORTED_ROUTE_FAMILY, Optional.empty()));
        var r = service.compare(RuntimeTraceReplayComparisonRequest.byTraceId(UID, TID));
        assertThat(r.runtimeTraceReplayComparisonOutcome()).isEqualTo(RuntimeTraceReplayComparisonOutcome.REPLAY_UNSUPPORTED);
        verify(comparator, never()).compare(any(), any(), any());
    }

    @Test
    void replay_failed_safe_never_calls_comparator() {
        RuntimeExecutionTraceDetailDto dto = minimalDto();
        when(traceQueryService.getTraceDetailById(UID, TID)).thenReturn(dto);
        when(replayService.replay(any())).thenReturn(RuntimeTraceReplayResult.failedSafe("boom"));
        var r = service.compare(RuntimeTraceReplayComparisonRequest.byTraceId(UID, TID));
        assertThat(r.runtimeTraceReplayComparisonOutcome()).isEqualTo(RuntimeTraceReplayComparisonOutcome.REPLAY_FAILED_SAFE);
        verify(comparator, never()).compare(any(), any(), any());
    }

    @Test
    void replay_succeeded_exact_match() {
        RuntimeExecutionTraceDetailDto dto = minimalDto();
        when(traceQueryService.getTraceDetailById(UID, TID)).thenReturn(dto);
        ExecutionTrace trace = ExecutionTrace.placeholder();
        when(replayService.replay(any()))
                .thenReturn(RuntimeTraceReplayResult.success("ok", trace));
        when(comparator.compare(any(), any(), any())).thenReturn(List.of());
        when(comparator.classifyAnswerStatus(any()))
                .thenReturn(RuntimeTraceReplayAnswerComparisonStatus.NOT_COMPARABLE_ORIGINAL_ABSENT);

        var r = service.compare(RuntimeTraceReplayComparisonRequest.byTraceId(UID, TID));
        assertThat(r.runtimeTraceReplayComparisonOutcome())
                .isEqualTo(RuntimeTraceReplayComparisonOutcome.COMPARISON_SUCCEEDED_EXACT_MATCH);
        assertThat(r.exactMatch()).isTrue();
        assertThat(r.mismatches()).isEmpty();
    }

    @Test
    void replay_succeeded_compatible_mismatch() {
        RuntimeExecutionTraceDetailDto dto = minimalDto();
        when(traceQueryService.getTraceDetailById(UID, TID)).thenReturn(dto);
        ExecutionTrace trace = ExecutionTrace.placeholder();
        List<RuntimeTraceReplayFieldMismatch> one =
                List.of(
                        new RuntimeTraceReplayFieldMismatch(
                                "ExecutionTrace.classifierStatus",
                                com.uniovi.rag.domain.runtime.tracecomparison.RuntimeTraceReplayMismatchCategory
                                        .FIELD_VALUE_MISMATCH,
                                "a",
                                "b"));
        when(replayService.replay(any())).thenReturn(RuntimeTraceReplayResult.success("ok", trace));
        when(comparator.compare(any(), any(), any())).thenReturn(one);
        when(comparator.classifyAnswerStatus(any()))
                .thenReturn(RuntimeTraceReplayAnswerComparisonStatus.NOT_COMPARABLE_ORIGINAL_ABSENT);
        when(comparator.isCompatibleMismatchOnly(one)).thenReturn(true);

        var r = service.compare(RuntimeTraceReplayComparisonRequest.byTraceId(UID, TID));
        assertThat(r.runtimeTraceReplayComparisonOutcome())
                .isEqualTo(RuntimeTraceReplayComparisonOutcome.COMPARISON_SUCCEEDED_COMPATIBLE_MISMATCH);
        assertThat(r.exactMatch()).isFalse();
    }

    @Test
    void replay_succeeded_structural_mismatch() {
        RuntimeExecutionTraceDetailDto dto = minimalDto();
        when(traceQueryService.getTraceDetailById(UID, TID)).thenReturn(dto);
        ExecutionTrace trace = ExecutionTrace.placeholder();
        List<RuntimeTraceReplayFieldMismatch> one =
                List.of(
                        new RuntimeTraceReplayFieldMismatch(
                                "workflowName",
                                com.uniovi.rag.domain.runtime.tracecomparison.RuntimeTraceReplayMismatchCategory
                                        .FIELD_VALUE_MISMATCH,
                                "a",
                                "b"));
        when(replayService.replay(any())).thenReturn(RuntimeTraceReplayResult.success("ok", trace));
        when(comparator.compare(any(), any(), any())).thenReturn(one);
        when(comparator.classifyAnswerStatus(any()))
                .thenReturn(RuntimeTraceReplayAnswerComparisonStatus.NOT_COMPARABLE_ORIGINAL_ABSENT);
        when(comparator.isCompatibleMismatchOnly(one)).thenReturn(false);

        var r = service.compare(RuntimeTraceReplayComparisonRequest.byTraceId(UID, TID));
        assertThat(r.runtimeTraceReplayComparisonOutcome())
                .isEqualTo(RuntimeTraceReplayComparisonOutcome.COMPARISON_SUCCEEDED_STRUCTURAL_MISMATCH);
    }

    @Test
    void invalid_request_short_circuits() {
        var r = service.compare(null);
        assertThat(r.runtimeTraceReplayComparisonOutcome()).isEqualTo(RuntimeTraceReplayComparisonOutcome.INVALID_REQUEST);
        verify(traceQueryService, never()).getTraceDetailById(any(), any());
    }

    private static RuntimeExecutionTraceDetailDto minimalDto() {
        return new RuntimeExecutionTraceDetailDto(
                TID,
                Instant.now(),
                UID,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "c",
                UUID.randomUUID(),
                "h",
                "DirectLlmWorkflow",
                false,
                "",
                false,
                "",
                "",
                false,
                false,
                "",
                "",
                "",
                false,
                "",
                "",
                false,
                "",
                RuntimeExecutionTraceEntityMapper.TRACE_SCHEMA_VERSION,
                Map.of(),
                List.of());
    }
}
