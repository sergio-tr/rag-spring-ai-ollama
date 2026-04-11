package com.uniovi.rag.application.service.runtime.traceregressionsuite;

import com.uniovi.rag.application.service.runtime.tracecomparisonbatch.RuntimeTraceReplayComparisonBatchService;
import com.uniovi.rag.domain.runtime.tracecomparisonbatch.RuntimeTraceReplayComparisonBatchOutcome;
import com.uniovi.rag.domain.runtime.tracecomparisonbatch.RuntimeTraceReplayComparisonBatchRequest;
import com.uniovi.rag.domain.runtime.tracecomparisonbatch.RuntimeTraceReplayComparisonBatchResult;
import com.uniovi.rag.domain.runtime.tracecomparisonbatch.RuntimeTraceReplayComparisonBatchSummary;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteBatchReturnedEntryResult;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteEntry;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteEntryFailureKind;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteExecutionFailedEntryResult;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteOutcome;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteRequest;
import com.uniovi.rag.interfaces.rest.NotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuntimeTraceRegressionSuiteServiceTest {

    @Mock
    private RuntimeTraceReplayComparisonBatchService batchService;

    private RuntimeTraceRegressionSuiteService suite;

    private UUID userId;

    @BeforeEach
    void setUp() {
        suite = new RuntimeTraceRegressionSuiteService(batchService);
        userId = UUID.randomUUID();
    }

    @Test
    void null_user_id_is_not_attempted_and_never_executes() {
        var req = new RuntimeTraceRegressionSuiteRequest(null, List.of(entryIds(UUID.randomUUID())));
        var r = suite.run(req);
        assertThat(r.suiteOutcome()).isEqualTo(RuntimeTraceRegressionSuiteOutcome.NOT_ATTEMPTED);
        assertThat(r.entryResults()).isEmpty();
        verify(batchService, never()).execute(any());
    }

    @Test
    void null_entries_reference_is_not_attempted() {
        var req = new RuntimeTraceRegressionSuiteRequest(userId, null);
        var r = suite.run(req);
        assertThat(r.suiteOutcome()).isEqualTo(RuntimeTraceRegressionSuiteOutcome.NOT_ATTEMPTED);
        assertThat(r.entryResults()).isEmpty();
        verify(batchService, never()).execute(any());
    }

    @Test
    void more_than_max_entries_is_not_attempted() {
        List<RuntimeTraceRegressionSuiteEntry> entries = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            entries.add(entryIds(UUID.randomUUID()));
        }
        var req = new RuntimeTraceRegressionSuiteRequest(userId, entries);
        var r = suite.run(req);
        assertThat(r.suiteOutcome()).isEqualTo(RuntimeTraceRegressionSuiteOutcome.NOT_ATTEMPTED);
        assertThat(r.entryResults()).isEmpty();
        verify(batchService, never()).execute(any());
    }

    @Test
    void empty_suite_has_empty_results_and_zero_execute() {
        var req = new RuntimeTraceRegressionSuiteRequest(userId, List.of());
        var r = suite.run(req);
        assertThat(r.suiteOutcome()).isEqualTo(RuntimeTraceRegressionSuiteOutcome.EMPTY_SUITE);
        assertThat(r.entryResults()).isEmpty();
        assertThat(r.summary().requestedEntryCount()).isZero();
        assertThat(r.summary().processedEntryCount()).isZero();
        assertThat(r.summary().batchReturnedCount()).isZero();
        assertThat(r.summary().executionFailedCount()).isZero();
        assertThat(r.summary().batchNotAttemptedSubcount()).isZero();
        verify(batchService, never()).execute(any());
    }

    @Test
    void executes_in_entry_order() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        when(batchService.execute(any())).thenReturn(batchResult(RuntimeTraceReplayComparisonBatchOutcome.COMPLETED_ALL_EXACT_MATCH, 1, 1, 1));

        var req =
                new RuntimeTraceRegressionSuiteRequest(
                        userId,
                        List.of(entryIds(a), entryIds(b), entryIds(c)));
        suite.run(req);

        InOrder order = inOrder(batchService);
        order.verify(batchService).execute(RuntimeTraceReplayComparisonBatchRequest.byTraceIds(userId, List.of(a)));
        order.verify(batchService).execute(RuntimeTraceReplayComparisonBatchRequest.byTraceIds(userId, List.of(b)));
        order.verify(batchService).execute(RuntimeTraceReplayComparisonBatchRequest.byTraceIds(userId, List.of(c)));
    }

    @Test
    void one_execute_per_entry() {
        when(batchService.execute(any())).thenReturn(batchResult(RuntimeTraceReplayComparisonBatchOutcome.COMPLETED_ALL_EXACT_MATCH, 1, 1, 1));
        var t1 = UUID.randomUUID();
        var t2 = UUID.randomUUID();
        suite.run(
                new RuntimeTraceRegressionSuiteRequest(
                        userId, List.of(entryIds(t1), entryIds(t2))));
        verify(batchService, times(2)).execute(any());
    }

    @Test
    void mixed_batch_outcomes_terminal_all_batch_returns() {
        when(batchService.execute(any()))
                .thenReturn(batchResult(RuntimeTraceReplayComparisonBatchOutcome.NOT_ATTEMPTED, 5, 0, 0))
                .thenReturn(batchResult(RuntimeTraceReplayComparisonBatchOutcome.EMPTY_SELECTION, 0, 0, 0));

        var r =
                suite.run(
                        new RuntimeTraceRegressionSuiteRequest(
                                userId,
                                List.of(entryIds(UUID.randomUUID()), entryIds(UUID.randomUUID()))));

        assertThat(r.suiteOutcome()).isEqualTo(RuntimeTraceRegressionSuiteOutcome.COMPLETED_ALL_BATCH_RETURNS);
        assertThat(r.summary().batchNotAttemptedSubcount()).isEqualTo(1);
        assertThat(r.summary().executionFailedCount()).isZero();
        assertThat(r.summary().requestedEntryCount()).isEqualTo(2);
        assertThat(r.summary().processedEntryCount()).isEqualTo(2);
        assertThat(r.entryResults()).hasSize(2);
        assertThat(r.entryResults().getFirst()).isInstanceOf(RuntimeTraceRegressionSuiteBatchReturnedEntryResult.class);
    }

    @Test
    void not_found_on_one_entry_continues_and_terminal_failures() {
        var ok = batchResult(RuntimeTraceReplayComparisonBatchOutcome.COMPLETED_ALL_EXACT_MATCH, 1, 1, 1);
        when(batchService.execute(any()))
                .thenThrow(new NotFoundException("missing"))
                .thenReturn(ok)
                .thenReturn(ok);

        var r =
                suite.run(
                        new RuntimeTraceRegressionSuiteRequest(
                                userId,
                                List.of(
                                        entryIds(UUID.randomUUID()),
                                        entryIds(UUID.randomUUID()),
                                        entryIds(UUID.randomUUID()))));

        assertThat(r.suiteOutcome())
                .isEqualTo(RuntimeTraceRegressionSuiteOutcome.COMPLETED_WITH_ENTRY_EXECUTION_FAILURES);
        assertThat(r.summary().executionFailedCount()).isEqualTo(1);
        assertThat(r.summary().batchReturnedCount()).isEqualTo(2);
        assertThat(r.entryResults()).hasSize(3);
        assertThat(r.entryResults().getFirst())
                .isInstanceOfSatisfying(
                        RuntimeTraceRegressionSuiteExecutionFailedEntryResult.class,
                        row -> assertThat(row.failureKind()).isEqualTo(RuntimeTraceRegressionSuiteEntryFailureKind.NOT_FOUND));
        verify(batchService, times(3)).execute(any());
    }

    @Test
    void illegal_argument_from_execute_maps_and_continues() {
        when(batchService.execute(any()))
                .thenThrow(new IllegalArgumentException("bad"))
                .thenReturn(batchResult(RuntimeTraceReplayComparisonBatchOutcome.COMPLETED_ALL_EXACT_MATCH, 1, 1, 1));

        var r =
                suite.run(
                        new RuntimeTraceRegressionSuiteRequest(
                                userId, List.of(entryIds(UUID.randomUUID()), entryIds(UUID.randomUUID()))));

        assertThat(r.suiteOutcome())
                .isEqualTo(RuntimeTraceRegressionSuiteOutcome.COMPLETED_WITH_ENTRY_EXECUTION_FAILURES);
        assertThat(r.entryResults().getFirst())
                .isInstanceOfSatisfying(
                        RuntimeTraceRegressionSuiteExecutionFailedEntryResult.class,
                        row -> {
                            assertThat(row.failureKind())
                                    .isEqualTo(RuntimeTraceRegressionSuiteEntryFailureKind.ILLEGAL_ARGUMENT);
                            assertThat(row.failureDetail()).isEqualTo("bad");
                        });
    }

    @Test
    void generic_runtime_exception_maps_unexpected() {
        when(batchService.execute(any()))
                .thenThrow(new IllegalStateException("boom"))
                .thenReturn(batchResult(RuntimeTraceReplayComparisonBatchOutcome.COMPLETED_ALL_EXACT_MATCH, 1, 1, 1));

        var r =
                suite.run(
                        new RuntimeTraceRegressionSuiteRequest(
                                userId, List.of(entryIds(UUID.randomUUID()), entryIds(UUID.randomUUID()))));

        assertThat(r.entryResults().getFirst())
                .isInstanceOfSatisfying(
                        RuntimeTraceRegressionSuiteExecutionFailedEntryResult.class,
                        row -> assertThat(row.failureKind()).isEqualTo(RuntimeTraceRegressionSuiteEntryFailureKind.UNEXPECTED));
        assertThat(r.suiteOutcome())
                .isEqualTo(RuntimeTraceRegressionSuiteOutcome.COMPLETED_WITH_ENTRY_EXECUTION_FAILURES);
    }

    @Test
    void by_conversation_passes_normalized_workflow_to_batch_factory() {
        UUID conv = UUID.randomUUID();
        when(batchService.execute(any()))
                .thenReturn(batchResult(RuntimeTraceReplayComparisonBatchOutcome.EMPTY_SELECTION, 0, 0, 0));

        suite.run(
                new RuntimeTraceRegressionSuiteRequest(
                        userId,
                        List.of(
                                new RuntimeTraceRegressionSuiteEntry.ByConversation(
                                        conv, Optional.empty(), Optional.empty(), Optional.of("  ")))));

        verify(batchService)
                .execute(
                        RuntimeTraceReplayComparisonBatchRequest.byConversation(
                                userId, conv, Optional.empty(), Optional.empty(), Optional.empty()));
    }

    @Test
    void selector_echo_capped_to_256_code_points() {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 80; i++) {
            ids.add(UUID.randomUUID());
        }
        String echo = RuntimeTraceRegressionSuiteService.selectorEcho(entryIdsList(ids));
        assertThat(echo.codePoints().count()).isLessThanOrEqualTo(256L);
    }

    private static RuntimeTraceRegressionSuiteEntry.ByTraceIds entryIds(UUID id) {
        return new RuntimeTraceRegressionSuiteEntry.ByTraceIds(List.of(id));
    }

    private static RuntimeTraceRegressionSuiteEntry.ByTraceIds entryIdsList(List<UUID> ids) {
        return new RuntimeTraceRegressionSuiteEntry.ByTraceIds(ids);
    }

    private static RuntimeTraceReplayComparisonBatchResult batchResult(
            RuntimeTraceReplayComparisonBatchOutcome outcome, int requested, int selected, int processed) {
        var summary =
                new RuntimeTraceReplayComparisonBatchSummary(
                        requested, selected, processed, 0, 0, 0, 0, 0, 0, 0);
        return new RuntimeTraceReplayComparisonBatchResult(outcome, summary, List.of(), requested, selected);
    }
}
