package com.uniovi.rag.application.service.runtime.tracecomparisonbatch;

import com.uniovi.rag.application.service.runtime.tracecomparison.RuntimeTraceReplayComparisonService;
import com.uniovi.rag.application.service.runtime.tracequery.RuntimeTraceQueryService;
import com.uniovi.rag.domain.runtime.tracecomparison.RuntimeTraceReplayAnswerComparisonStatus;
import com.uniovi.rag.domain.runtime.tracecomparison.RuntimeTraceReplayComparisonOutcome;
import com.uniovi.rag.domain.runtime.tracecomparison.RuntimeTraceReplayComparisonReplayEcho;
import com.uniovi.rag.domain.runtime.tracecomparison.RuntimeTraceReplayComparisonRequest;
import com.uniovi.rag.domain.runtime.tracecomparison.RuntimeTraceReplayComparisonResult;
import com.uniovi.rag.domain.runtime.tracecomparison.RuntimeTraceReplayFieldMismatch;
import com.uniovi.rag.domain.runtime.tracecomparison.RuntimeTraceReplayMismatchCategory;
import com.uniovi.rag.domain.runtime.tracecomparisonbatch.RuntimeTraceReplayComparisonBatchOutcome;
import com.uniovi.rag.domain.runtime.tracecomparisonbatch.RuntimeTraceReplayComparisonBatchRequest;
import com.uniovi.rag.domain.runtime.tracecomparisonbatch.RuntimeTraceReplayComparisonBatchSummary;
import com.uniovi.rag.domain.runtime.tracereplay.RuntimeTraceReplayMode;
import com.uniovi.rag.domain.runtime.tracereplay.RuntimeTraceReplayOutcome;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuntimeTraceReplayComparisonBatchServiceTest {

    @Mock
    private RuntimeTraceReplayComparisonService comparisonService;

    @Mock
    private RuntimeTraceQueryService traceQueryService;

    private RuntimeTraceReplayComparisonBatchService batchService;

    private UUID userId;
    private UUID t1;
    private UUID t2;

    @BeforeEach
    void setUp() {
        batchService = new RuntimeTraceReplayComparisonBatchService(comparisonService, traceQueryService);
        userId = UUID.randomUUID();
        t1 = UUID.randomUUID();
        t2 = UUID.randomUUID();
    }

    @Test
    void empty_trace_id_list_yields_empty_selection() {
        var req = RuntimeTraceReplayComparisonBatchRequest.byTraceIds(userId, List.of());
        var r = batchService.execute(req);
        assertThat(r.batchOutcome()).isEqualTo(RuntimeTraceReplayComparisonBatchOutcome.EMPTY_SELECTION);
        assertThat(r.requestedCount()).isZero();
        assertThat(r.selectedCount()).isZero();
    }

    @Test
    void all_exact_match_terminal() {
        when(comparisonService.compare(any(RuntimeTraceReplayComparisonRequest.class)))
                .thenAnswer(
                        inv -> {
                            RuntimeTraceReplayComparisonRequest cr = inv.getArgument(0);
                            return exactMatchResult(cr.traceId().orElseThrow());
                        });
        var req = RuntimeTraceReplayComparisonBatchRequest.byTraceIds(userId, List.of(t1, t2));
        var r = batchService.execute(req);
        assertThat(r.batchOutcome()).isEqualTo(RuntimeTraceReplayComparisonBatchOutcome.COMPLETED_ALL_EXACT_MATCH);
        assertThat(r.items()).hasSize(2);
        assertThat(r.summary().processedCount()).isEqualTo(2);
        assertSummaryIdentity(r.summary());
        verify(comparisonService, Mockito.times(2)).compare(any(RuntimeTraceReplayComparisonRequest.class));
    }

    @Test
    void compatible_only_mix_terminal() {
        when(comparisonService.compare(any()))
                .thenReturn(
                        exactMatchResult(t1),
                        compatibleResult(t2));
        var r =
                batchService.execute(RuntimeTraceReplayComparisonBatchRequest.byTraceIds(userId, List.of(t1, t2)));
        assertThat(r.batchOutcome())
                .isEqualTo(RuntimeTraceReplayComparisonBatchOutcome.COMPLETED_WITH_COMPATIBLE_MISMATCHES_ONLY);
        assertSummaryIdentity(r.summary());
    }

    @Test
    void structural_only_terminal() {
        when(comparisonService.compare(any())).thenReturn(structuralResult(t1));
        var r = batchService.execute(RuntimeTraceReplayComparisonBatchRequest.byTraceIds(userId, List.of(t1)));
        assertThat(r.batchOutcome()).isEqualTo(RuntimeTraceReplayComparisonBatchOutcome.COMPLETED_WITH_STRUCTURAL_MISMATCHES);
    }

    @Test
    void unsupported_only_terminal() {
        when(comparisonService.compare(any())).thenReturn(replayUnsupportedResult(t1));
        var r = batchService.execute(RuntimeTraceReplayComparisonBatchRequest.byTraceIds(userId, List.of(t1)));
        assertThat(r.batchOutcome()).isEqualTo(RuntimeTraceReplayComparisonBatchOutcome.COMPLETED_WITH_UNSUPPORTED_ITEMS);
    }

    @Test
    void failed_safe_only_terminal_replay() {
        when(comparisonService.compare(any())).thenReturn(replayFailedSafeResult(t1));
        var r = batchService.execute(RuntimeTraceReplayComparisonBatchRequest.byTraceIds(userId, List.of(t1)));
        assertThat(r.batchOutcome()).isEqualTo(RuntimeTraceReplayComparisonBatchOutcome.COMPLETED_WITH_FAILED_SAFE_ITEMS);
    }

    @Test
    void failed_safe_only_terminal_comparison_failed_safe() {
        when(comparisonService.compare(any())).thenReturn(comparisonFailedSafeResult(t1));
        var r = batchService.execute(RuntimeTraceReplayComparisonBatchRequest.byTraceIds(userId, List.of(t1)));
        assertThat(r.batchOutcome()).isEqualTo(RuntimeTraceReplayComparisonBatchOutcome.COMPLETED_WITH_FAILED_SAFE_ITEMS);
    }

    @Test
    void mixed_includes_not_found() {
        when(comparisonService.compare(any()))
                .thenReturn(exactMatchResult(t1), notFoundResult());
        var r = batchService.execute(RuntimeTraceReplayComparisonBatchRequest.byTraceIds(userId, List.of(t1, t2)));
        assertThat(r.batchOutcome()).isEqualTo(RuntimeTraceReplayComparisonBatchOutcome.COMPLETED_MIXED);
    }

    @Test
    void requested_trace_id_is_batch_key_and_resolved_empty_when_not_found() {
        when(comparisonService.compare(any())).thenReturn(notFoundResult());
        var r = batchService.execute(RuntimeTraceReplayComparisonBatchRequest.byTraceIds(userId, List.of(t1)));
        assertThat(r.items().getFirst().requestedTraceId()).isEqualTo(t1);
        assertThat(r.items().getFirst().resolvedOriginalTraceId()).isEmpty();
        assertThat(r.items().getFirst().comparisonOutcome())
                .isEqualTo(RuntimeTraceReplayComparisonOutcome.ORIGINAL_TRACE_NOT_FOUND_OR_INACCESSIBLE.name());
    }

    @Test
    void by_conversation_lists_first_page_only_and_requested_equals_selected_count() {
        UUID cid = UUID.randomUUID();
        Instant from = Instant.parse("2024-01-01T00:00:00Z");
        Page<RuntimeExecutionTraceSummaryDto> page =
                new PageImpl<>(
                        List.of(summaryDto(t1), summaryDto(t2)),
                        PageRequest.of(0, RuntimeTraceReplayComparisonBatchService.LIST_CONVERSATION_PAGE_SIZE),
                        2);
        when(traceQueryService.listConversationTraceSummaries(
                        eq(userId),
                        eq(cid),
                        eq(Optional.of(from)),
                        eq(Optional.empty()),
                        eq(Optional.of("  wf  ")),
                        eq(0),
                        eq(RuntimeTraceReplayComparisonBatchService.LIST_CONVERSATION_PAGE_SIZE)))
                .thenReturn(page);
        when(comparisonService.compare(any())).thenAnswer(inv -> exactMatchResult(((RuntimeTraceReplayComparisonRequest) inv.getArgument(0)).traceId().orElseThrow()));

        var req =
                RuntimeTraceReplayComparisonBatchRequest.byConversation(
                        userId, cid, Optional.of(from), Optional.empty(), Optional.of("  wf  "));
        var r = batchService.execute(req);
        assertThat(r.requestedCount()).isEqualTo(r.selectedCount()).isEqualTo(2);
        assertThat(r.batchOutcome()).isEqualTo(RuntimeTraceReplayComparisonBatchOutcome.COMPLETED_ALL_EXACT_MATCH);

        ArgumentCaptor<Integer> pageCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> sizeCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(traceQueryService)
                .listConversationTraceSummaries(
                        eq(userId),
                        eq(cid),
                        eq(Optional.of(from)),
                        eq(Optional.empty()),
                        eq(Optional.of("  wf  ")),
                        pageCaptor.capture(),
                        sizeCaptor.capture());
        assertThat(pageCaptor.getValue()).isZero();
        assertThat(sizeCaptor.getValue()).isEqualTo(50);
    }

    @Test
    void derive_outcome_all_exact() {
        var s =
                new RuntimeTraceReplayComparisonBatchSummary(2, 2, 2, 2, 0, 0, 0, 0, 0, 0);
        assertThat(RuntimeTraceReplayComparisonBatchService.deriveBatchOutcome(s))
                .isEqualTo(RuntimeTraceReplayComparisonBatchOutcome.COMPLETED_ALL_EXACT_MATCH);
    }

    private static void assertSummaryIdentity(RuntimeTraceReplayComparisonBatchSummary s) {
        int sum =
                s.exactMatchCount()
                        + s.compatibleMismatchItemCount()
                        + s.structuralMismatchItemCount()
                        + s.comparisonFailedSafeItemCount()
                        + s.replayFailedSafeItemCount()
                        + s.replayUnsupportedItemCount()
                        + s.originalNotFoundOrInaccessibleItemCount();
        assertThat(sum).isEqualTo(s.processedCount());
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

    private static RuntimeTraceReplayComparisonResult base(
            UUID traceId,
            RuntimeTraceReplayComparisonOutcome co,
            RuntimeTraceReplayOutcome ro,
            boolean exact,
            List<RuntimeTraceReplayFieldMismatch> mismatches,
            UUID originalId) {
        return new RuntimeTraceReplayComparisonResult(
                UUID.randomUUID(),
                UUID.randomUUID(),
                originalId,
                null,
                null,
                RuntimeTraceReplayMode.BY_TRACE_ID,
                new RuntimeTraceReplayComparisonReplayEcho(Optional.of(traceId), Optional.empty(), Optional.empty()),
                co,
                ro,
                RuntimeTraceReplayAnswerComparisonStatus.NOT_COMPARABLE_ORIGINAL_ABSENT,
                exact,
                "summary",
                mismatches,
                "R1",
                "R2",
                "W1",
                "W2");
    }

    private static RuntimeTraceReplayComparisonResult exactMatchResult(UUID traceId) {
        return base(
                traceId,
                RuntimeTraceReplayComparisonOutcome.COMPARISON_SUCCEEDED_EXACT_MATCH,
                RuntimeTraceReplayOutcome.REPLAY_SUCCEEDED,
                true,
                List.of(),
                traceId);
    }

    private static RuntimeTraceReplayComparisonResult compatibleResult(UUID traceId) {
        return base(
                traceId,
                RuntimeTraceReplayComparisonOutcome.COMPARISON_SUCCEEDED_COMPATIBLE_MISMATCH,
                RuntimeTraceReplayOutcome.REPLAY_SUCCEEDED,
                false,
                List.of(
                        new RuntimeTraceReplayFieldMismatch(
                                "f", RuntimeTraceReplayMismatchCategory.FIELD_VALUE_MISMATCH, "a", "b")),
                traceId);
    }

    private static RuntimeTraceReplayComparisonResult structuralResult(UUID traceId) {
        return base(
                traceId,
                RuntimeTraceReplayComparisonOutcome.COMPARISON_SUCCEEDED_STRUCTURAL_MISMATCH,
                RuntimeTraceReplayOutcome.REPLAY_SUCCEEDED,
                false,
                List.of(
                        new RuntimeTraceReplayFieldMismatch(
                                "f", RuntimeTraceReplayMismatchCategory.FIELD_VALUE_MISMATCH, "a", "b")),
                traceId);
    }

    private static RuntimeTraceReplayComparisonResult replayUnsupportedResult(UUID traceId) {
        return base(
                traceId,
                RuntimeTraceReplayComparisonOutcome.REPLAY_UNSUPPORTED,
                RuntimeTraceReplayOutcome.UNSUPPORTED_ROUTE_FAMILY,
                false,
                List.of(),
                traceId);
    }

    private static RuntimeTraceReplayComparisonResult replayFailedSafeResult(UUID traceId) {
        return base(
                traceId,
                RuntimeTraceReplayComparisonOutcome.REPLAY_FAILED_SAFE,
                RuntimeTraceReplayOutcome.REPLAY_FAILED_SAFE,
                false,
                List.of(),
                traceId);
    }

    private static RuntimeTraceReplayComparisonResult comparisonFailedSafeResult(UUID traceId) {
        return base(
                traceId,
                RuntimeTraceReplayComparisonOutcome.COMPARISON_FAILED_SAFE,
                RuntimeTraceReplayOutcome.REPLAY_SUCCEEDED,
                false,
                List.of(),
                traceId);
    }

    private static RuntimeTraceReplayComparisonResult notFoundResult() {
        return new RuntimeTraceReplayComparisonResult(
                UUID.randomUUID(),
                new UUID(0L, 0L),
                new UUID(0L, 0L),
                null,
                null,
                RuntimeTraceReplayMode.BY_TRACE_ID,
                new RuntimeTraceReplayComparisonReplayEcho(Optional.empty(), Optional.empty(), Optional.empty()),
                RuntimeTraceReplayComparisonOutcome.ORIGINAL_TRACE_NOT_FOUND_OR_INACCESSIBLE,
                RuntimeTraceReplayOutcome.NOT_ATTEMPTED,
                RuntimeTraceReplayAnswerComparisonStatus.NOT_COMPARABLE_ORIGINAL_ABSENT,
                false,
                "nf",
                List.of(),
                "",
                "",
                "",
                "");
    }
}
