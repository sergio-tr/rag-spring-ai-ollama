package com.uniovi.rag.application.service.runtime.tracereplaybatch;

import com.uniovi.rag.application.service.runtime.tracequery.RuntimeTraceQueryService;
import com.uniovi.rag.application.service.runtime.tracereplay.RuntimeTraceReplayService;
import com.uniovi.rag.domain.runtime.engine.ExecutionTrace;
import com.uniovi.rag.domain.runtime.tracereplay.RuntimeTraceReplayOutcome;
import com.uniovi.rag.domain.runtime.tracereplay.RuntimeTraceReplayRequest;
import com.uniovi.rag.domain.runtime.tracereplay.RuntimeTraceReplayResult;
import com.uniovi.rag.domain.runtime.tracereplaybatch.RuntimeTraceReplayBatchItemOutcome;
import com.uniovi.rag.domain.runtime.tracereplaybatch.RuntimeTraceReplayBatchOutcome;
import com.uniovi.rag.domain.runtime.tracereplaybatch.RuntimeTraceReplayBatchRequest;
import com.uniovi.rag.domain.runtime.tracereplaybatch.RuntimeTraceReplayBatchSummary;
import com.uniovi.rag.interfaces.rest.NotFoundException;
import com.uniovi.rag.interfaces.rest.dto.RuntimeExecutionTraceSummaryDto;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuntimeTraceReplayBatchServiceTest {

    @Mock
    private RuntimeTraceReplayService replayService;

    @Mock
    private RuntimeTraceQueryService traceQueryService;

    private RuntimeTraceReplayBatchService batchService;

    private UUID userId;
    private UUID t1;
    private UUID t2;

    @BeforeEach
    void setUp() {
        batchService = new RuntimeTraceReplayBatchService(replayService, traceQueryService);
        userId = UUID.randomUUID();
        t1 = UUID.randomUUID();
        t2 = UUID.randomUUID();
    }

    @Test
    void empty_trace_id_list_yields_empty_selection() {
        var req = RuntimeTraceReplayBatchRequest.byTraceIds(userId, List.of());
        var r = batchService.execute(req);
        assertThat(r.batchOutcome()).isEqualTo(RuntimeTraceReplayBatchOutcome.EMPTY_SELECTION);
        assertThat(r.requestedCount()).isZero();
        assertThat(r.selectedCount()).isZero();
    }

    @Test
    void all_replay_succeeded_terminal() {
        when(replayService.replay(any(RuntimeTraceReplayRequest.class)))
                .thenAnswer(
                        inv -> {
                            RuntimeTraceReplayRequest rr = inv.getArgument(0);
                            return successResult(rr.traceId().orElseThrow());
                        });
        var req = RuntimeTraceReplayBatchRequest.byTraceIds(userId, List.of(t1, t2));
        var r = batchService.execute(req);
        assertThat(r.batchOutcome()).isEqualTo(RuntimeTraceReplayBatchOutcome.COMPLETED_ALL_REPLAY_SUCCEEDED);
        assertThat(r.items()).hasSize(2);
        assertThat(r.summary().processedCount()).isEqualTo(2);
        assertSummaryIdentity(r.summary());
        verify(replayService, Mockito.times(2)).replay(any(RuntimeTraceReplayRequest.class));
    }

    @Test
    void success_and_unsupported_only_terminal() {
        when(replayService.replay(any()))
                .thenReturn(
                        RuntimeTraceReplayResult.success("a", ExecutionTrace.placeholder()),
                        RuntimeTraceReplayResult.unsupported(
                                RuntimeTraceReplayOutcome.UNSUPPORTED_ROUTE_FAMILY, Optional.of("x")));
        var r = batchService.execute(RuntimeTraceReplayBatchRequest.byTraceIds(userId, List.of(t1, t2)));
        assertThat(r.batchOutcome()).isEqualTo(RuntimeTraceReplayBatchOutcome.COMPLETED_WITH_UNSUPPORTED_ITEMS);
        assertSummaryIdentity(r.summary());
    }

    @Test
    void failed_safe_only_terminal() {
        when(replayService.replay(any())).thenReturn(RuntimeTraceReplayResult.failedSafe("e"));
        var r = batchService.execute(RuntimeTraceReplayBatchRequest.byTraceIds(userId, List.of(t1)));
        assertThat(r.batchOutcome()).isEqualTo(RuntimeTraceReplayBatchOutcome.COMPLETED_WITH_FAILED_SAFE_ITEMS);
    }

    @Test
    void not_found_item_does_not_abort_batch() {
        when(replayService.replay(any()))
                .thenThrow(new NotFoundException("missing"))
                .thenReturn(RuntimeTraceReplayResult.success("ok", ExecutionTrace.placeholder()));
        var r = batchService.execute(RuntimeTraceReplayBatchRequest.byTraceIds(userId, List.of(t1, t2)));
        assertThat(r.batchOutcome()).isEqualTo(RuntimeTraceReplayBatchOutcome.COMPLETED_WITH_NOT_FOUND_ITEMS);
        assertThat(r.items().getFirst().itemOutcome())
                .isEqualTo(RuntimeTraceReplayBatchItemOutcome.ORIGINAL_TRACE_NOT_FOUND_OR_INACCESSIBLE);
        assertThat(r.items().getFirst().replayOutcome()).isEmpty();
    }

    @Test
    void only_success_and_not_found_pure_terminal() {
        when(replayService.replay(any()))
                .thenReturn(RuntimeTraceReplayResult.success("a", ExecutionTrace.placeholder()))
                .thenThrow(new NotFoundException("missing"));
        var r = batchService.execute(RuntimeTraceReplayBatchRequest.byTraceIds(userId, List.of(t1, t2)));
        assertThat(r.batchOutcome()).isEqualTo(RuntimeTraceReplayBatchOutcome.COMPLETED_WITH_NOT_FOUND_ITEMS);
    }

    @Test
    void not_attempted_item_forces_mixed() {
        when(replayService.replay(any()))
                .thenReturn(RuntimeTraceReplayResult.unsupported(RuntimeTraceReplayOutcome.NOT_ATTEMPTED, Optional.empty()));
        var r = batchService.execute(RuntimeTraceReplayBatchRequest.byTraceIds(userId, List.of(t1)));
        assertThat(r.batchOutcome()).isEqualTo(RuntimeTraceReplayBatchOutcome.COMPLETED_MIXED);
    }

    @Test
    void not_found_and_unsupported_yields_mixed() {
        when(replayService.replay(any()))
                .thenThrow(new NotFoundException("missing"))
                .thenReturn(
                        RuntimeTraceReplayResult.unsupported(
                                RuntimeTraceReplayOutcome.UNSUPPORTED_ROUTE_FAMILY, Optional.of("x")));
        var r = batchService.execute(RuntimeTraceReplayBatchRequest.byTraceIds(userId, List.of(t1, t2)));
        assertThat(r.batchOutcome()).isEqualTo(RuntimeTraceReplayBatchOutcome.COMPLETED_MIXED);
    }

    @Test
    void by_conversation_lists_first_page_and_requested_count_is_one() {
        UUID cid = UUID.randomUUID();
        Instant from = Instant.parse("2024-01-01T00:00:00Z");
        Page<RuntimeExecutionTraceSummaryDto> page =
                new PageImpl<>(
                        List.of(summaryDto(t1), summaryDto(t2)),
                        PageRequest.of(0, RuntimeTraceReplayBatchService.LIST_CONVERSATION_PAGE_SIZE),
                        2);
        when(traceQueryService.listConversationTraceSummaries(
                        eq(userId),
                        eq(cid),
                        eq(Optional.of(from)),
                        eq(Optional.empty()),
                        eq(Optional.of("wf")),
                        eq(0),
                        eq(RuntimeTraceReplayBatchService.LIST_CONVERSATION_PAGE_SIZE)))
                .thenReturn(page);
        when(replayService.replay(any()))
                .thenAnswer(
                        inv ->
                                RuntimeTraceReplayResult.success(
                                        "x", ExecutionTrace.placeholder()));

        var req =
                RuntimeTraceReplayBatchRequest.byConversation(
                        userId, cid, Optional.of(from), Optional.empty(), Optional.of("  wf  "));
        var r = batchService.execute(req);
        assertThat(r.requestedCount()).isEqualTo(1);
        assertThat(r.selectedCount()).isEqualTo(2);
        assertThat(r.batchOutcome()).isEqualTo(RuntimeTraceReplayBatchOutcome.COMPLETED_ALL_REPLAY_SUCCEEDED);

        ArgumentCaptor<Integer> pageCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> sizeCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(traceQueryService)
                .listConversationTraceSummaries(
                        eq(userId),
                        eq(cid),
                        eq(Optional.of(from)),
                        eq(Optional.empty()),
                        eq(Optional.of("wf")),
                        pageCaptor.capture(),
                        sizeCaptor.capture());
        assertThat(pageCaptor.getValue()).isZero();
        assertThat(sizeCaptor.getValue()).isEqualTo(50);
    }

    @Test
    void by_conversation_blank_workflow_passes_empty_optional() {
        UUID cid = UUID.randomUUID();
        Page<RuntimeExecutionTraceSummaryDto> page =
                new PageImpl<>(List.of(), PageRequest.of(0, 50), 0);
        when(traceQueryService.listConversationTraceSummaries(
                        eq(userId), eq(cid), eq(Optional.empty()), eq(Optional.empty()), eq(Optional.empty()), eq(0), eq(50)))
                .thenReturn(page);
        batchService.execute(
                RuntimeTraceReplayBatchRequest.byConversation(userId, cid, Optional.empty(), Optional.empty(), Optional.of("   ")));
        verify(traceQueryService)
                .listConversationTraceSummaries(
                        eq(userId), eq(cid), eq(Optional.empty()), eq(Optional.empty()), eq(Optional.empty()), eq(0), eq(50));
    }

    @Test
    void by_conversation_not_found_propagates() {
        UUID cid = UUID.randomUUID();
        when(traceQueryService.listConversationTraceSummaries(
                        eq(userId),
                        eq(cid),
                        any(),
                        any(),
                        any(),
                        eq(0),
                        eq(RuntimeTraceReplayBatchService.LIST_CONVERSATION_PAGE_SIZE)))
                .thenThrow(new NotFoundException("conversation not found"));
        var req =
                RuntimeTraceReplayBatchRequest.byConversation(
                        userId, cid, Optional.empty(), Optional.empty(), Optional.empty());
        assertThatThrownBy(() -> batchService.execute(req)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void derive_outcome_all_success() {
        var s = new RuntimeTraceReplayBatchSummary(2, 2, 0, 0, 0, 0);
        assertThat(RuntimeTraceReplayBatchService.deriveBatchOutcome(s))
                .isEqualTo(RuntimeTraceReplayBatchOutcome.COMPLETED_ALL_REPLAY_SUCCEEDED);
    }

    @Test
    void map_unsupported_family() {
        assertThat(RuntimeTraceReplayBatchService.mapReplayOutcomeToItemOutcome(RuntimeTraceReplayOutcome.UNSUPPORTED_ROUTE_FAMILY))
                .isEqualTo(RuntimeTraceReplayBatchItemOutcome.REPLAY_UNSUPPORTED);
    }

    private static void assertSummaryIdentity(RuntimeTraceReplayBatchSummary s) {
        int sum =
                s.replaySucceededItemCount()
                        + s.replayUnsupportedItemCount()
                        + s.replayFailedSafeItemCount()
                        + s.originalNotFoundOrInaccessibleItemCount()
                        + s.replayNotAttemptedItemCount();
        assertThat(sum).isEqualTo(s.processedCount());
    }

    private static RuntimeTraceReplayResult successResult(UUID traceId) {
        return RuntimeTraceReplayResult.success("ok-" + traceId, ExecutionTrace.placeholder());
    }

    private static RuntimeExecutionTraceSummaryDto summaryDto(UUID id) {
        return new RuntimeExecutionTraceSummaryDto(
                id,
                Instant.now(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "corr",
                UUID.randomUUID(),
                "hash",
                "wf",
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
                "");
    }
}
