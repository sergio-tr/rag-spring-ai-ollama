package com.uniovi.rag.application.service.runtime.traceregressionsuiterun;

import com.uniovi.rag.domain.runtime.tracecomparisonbatch.RuntimeTraceReplayComparisonBatchOutcome;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteBatchReturnedEntryResult;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteEntryFailureKind;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteEntryKind;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteExecutionFailedEntryResult;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteOutcome;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteResult;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteSummary;
import com.uniovi.rag.domain.runtime.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunSourceType;
import com.uniovi.rag.infrastructure.persistence.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunEntryRepository;
import com.uniovi.rag.infrastructure.persistence.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunPersistenceMapper;
import com.uniovi.rag.infrastructure.persistence.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuntimeTraceRegressionSuiteRunPersistenceServiceMockTest {

    @Mock
    private RuntimeTraceRegressionSuiteRunRepository runRepository;

    @Mock
    private RuntimeTraceRegressionSuiteRunEntryRepository entryRepository;

    private RuntimeTraceRegressionSuiteRunPersistenceService service;

    @BeforeEach
    void setUp() {
        service =
                new RuntimeTraceRegressionSuiteRunPersistenceService(
                        runRepository,
                        entryRepository,
                        new RuntimeTraceRegressionSuiteRunPersistenceMapper(),
                        Clock.fixed(Instant.parse("2026-01-02T12:00:00Z"), ZoneOffset.UTC));
    }

    private static RuntimeTraceRegressionSuiteResult validOneEntry() {
        var entry =
                new RuntimeTraceRegressionSuiteBatchReturnedEntryResult(
                        0,
                        RuntimeTraceRegressionSuiteEntryKind.BY_TRACE_IDS,
                        "sel",
                        RuntimeTraceReplayComparisonBatchOutcome.COMPLETED_ALL_EXACT_MATCH,
                        2,
                        2,
                        2);
        var summary = new RuntimeTraceRegressionSuiteSummary(1, 1, 1, 0, 0);
        return new RuntimeTraceRegressionSuiteResult(
                RuntimeTraceRegressionSuiteOutcome.COMPLETED_ALL_BATCH_RETURNS, summary, List.of(entry));
    }

    @Test
    void t1_adHoc_validResult_savesRunAndEntriesOnce() {
        UUID userId = UUID.randomUUID();
        when(runRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UUID id = service.createRun(userId, RuntimeTraceRegressionSuiteRunSourceType.AD_HOC, Optional.empty(), validOneEntry());

        assertThat(id).isNotNull();
        verify(runRepository, times(1)).save(any());
        verify(entryRepository, times(1)).saveAll(any());
    }

    @Test
    void t2_savedDefinition_withDefinitionId_saves() {
        UUID userId = UUID.randomUUID();
        UUID defId = UUID.randomUUID();
        when(runRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UUID id =
                service.createRun(
                        userId, RuntimeTraceRegressionSuiteRunSourceType.SAVED_DEFINITION, Optional.of(defId), validOneEntry());

        assertThat(id).isNotNull();
        verify(runRepository, times(1)).save(any());
        verify(entryRepository, times(1)).saveAll(any());
    }

    @Test
    void t3_nullUserId_throws() {
        assertThatThrownBy(
                        () ->
                                service.createRun(
                                        null,
                                        RuntimeTraceRegressionSuiteRunSourceType.AD_HOC,
                                        Optional.empty(),
                                        validOneEntry()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("userId");
    }

    @Test
    void t4_validationFailures() {
        UUID u = UUID.randomUUID();
        assertThatThrownBy(
                        () ->
                                service.createRun(
                                        u,
                                        RuntimeTraceRegressionSuiteRunSourceType.SAVED_DEFINITION,
                                        Optional.empty(),
                                        validOneEntry()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("definitionId required for SAVED_DEFINITION");

        assertThatThrownBy(
                        () ->
                                service.createRun(
                                        u,
                                        RuntimeTraceRegressionSuiteRunSourceType.AD_HOC,
                                        Optional.of(UUID.randomUUID()),
                                        validOneEntry()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("definitionId must be absent for AD_HOC");

        assertThatThrownBy(
                        () ->
                                service.createRun(
                                        u, RuntimeTraceRegressionSuiteRunSourceType.AD_HOC, Optional.empty(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("result");

        var badSummary = new RuntimeTraceRegressionSuiteSummary(2, 2, 1, 0, 0);
        var bad =
                new RuntimeTraceRegressionSuiteResult(
                        RuntimeTraceRegressionSuiteOutcome.COMPLETED_ALL_BATCH_RETURNS,
                        badSummary,
                        List.of(
                                new RuntimeTraceRegressionSuiteBatchReturnedEntryResult(
                                        0,
                                        RuntimeTraceRegressionSuiteEntryKind.BY_TRACE_IDS,
                                        "a",
                                        RuntimeTraceReplayComparisonBatchOutcome.COMPLETED_ALL_EXACT_MATCH,
                                        1,
                                        1,
                                        1)));
        assertThatThrownBy(() -> service.createRun(u, RuntimeTraceRegressionSuiteRunSourceType.AD_HOC, Optional.empty(), bad))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("result entryResults size mismatch");

        var wrongOrder =
                new RuntimeTraceRegressionSuiteResult(
                        RuntimeTraceRegressionSuiteOutcome.COMPLETED_ALL_BATCH_RETURNS,
                        new RuntimeTraceRegressionSuiteSummary(1, 1, 1, 0, 0),
                        List.of(
                                new RuntimeTraceRegressionSuiteBatchReturnedEntryResult(
                                        1,
                                        RuntimeTraceRegressionSuiteEntryKind.BY_TRACE_IDS,
                                        "a",
                                        RuntimeTraceReplayComparisonBatchOutcome.COMPLETED_ALL_EXACT_MATCH,
                                        1,
                                        1,
                                        1)));
        assertThatThrownBy(
                        () -> service.createRun(u, RuntimeTraceRegressionSuiteRunSourceType.AD_HOC, Optional.empty(), wrongOrder))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("entryOrder mismatch");

        var neg = new RuntimeTraceRegressionSuiteSummary(1, 1, 1, 0, -1);
        var negResult =
                new RuntimeTraceRegressionSuiteResult(
                        RuntimeTraceRegressionSuiteOutcome.COMPLETED_ALL_BATCH_RETURNS,
                        neg,
                        List.of(
                                new RuntimeTraceRegressionSuiteBatchReturnedEntryResult(
                                        0,
                                        RuntimeTraceRegressionSuiteEntryKind.BY_TRACE_IDS,
                                        "a",
                                        RuntimeTraceReplayComparisonBatchOutcome.COMPLETED_ALL_EXACT_MATCH,
                                        1,
                                        1,
                                        1)));
        assertThatThrownBy(
                        () -> service.createRun(u, RuntimeTraceRegressionSuiteRunSourceType.AD_HOC, Optional.empty(), negResult))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("result summary invalid");

        assertThatThrownBy(() -> service.loadByIdForUser(null, u)).isInstanceOf(IllegalArgumentException.class).hasMessage("runId");
        assertThatThrownBy(() -> service.loadByIdForUser(UUID.randomUUID(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("userId");
        assertThatThrownBy(() -> service.listSummariesForUser(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("userId");
    }

    @Test
    void t4b_notAttemptedOrEmptySuite_withEntries_throws() {
        UUID u = UUID.randomUUID();
        var entry =
                new RuntimeTraceRegressionSuiteBatchReturnedEntryResult(
                        0,
                        RuntimeTraceRegressionSuiteEntryKind.BY_TRACE_IDS,
                        "x",
                        RuntimeTraceReplayComparisonBatchOutcome.NOT_ATTEMPTED,
                        0,
                        0,
                        0);
        var badNotAttempted =
                new RuntimeTraceRegressionSuiteResult(
                        RuntimeTraceRegressionSuiteOutcome.NOT_ATTEMPTED,
                        new RuntimeTraceRegressionSuiteSummary(1, 1, 1, 0, 1),
                        List.of(entry));
        assertThatThrownBy(
                        () -> service.createRun(u, RuntimeTraceRegressionSuiteRunSourceType.AD_HOC, Optional.empty(), badNotAttempted))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("NOT_ATTEMPTED result must not contain entry results");

        var badEmpty =
                new RuntimeTraceRegressionSuiteResult(
                        RuntimeTraceRegressionSuiteOutcome.EMPTY_SUITE,
                        new RuntimeTraceRegressionSuiteSummary(1, 1, 1, 0, 1),
                        List.of(entry));
        assertThatThrownBy(
                        () -> service.createRun(u, RuntimeTraceRegressionSuiteRunSourceType.AD_HOC, Optional.empty(), badEmpty))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("EMPTY_SUITE result must not contain entry results");
    }

    @Test
    void t10_mocksOnlyRunAndEntryRepositories() {
        // Constructor wiring: no suite or definition service — verified by compile-time absence in this test class.
        assertThat(service).isNotNull();
    }

    @Test
    void p48_t1_delete_returns_true_when_one_row_deleted() {
        UUID existingId = UUID.randomUUID();
        UUID ownerUserId = UUID.randomUUID();
        when(runRepository.deleteByIdAndUserId(existingId, ownerUserId)).thenReturn(1L);

        assertThat(service.deleteRunForUser(existingId, ownerUserId)).isTrue();

        verify(runRepository, times(1)).deleteByIdAndUserId(existingId, ownerUserId);
        verify(runRepository, never()).findByIdAndUserId(any(), any());
        verify(runRepository, never()).save(any());
        verify(runRepository, never()).deleteById(any());
        verifyNoInteractions(entryRepository);
    }

    @Test
    void p48_t2_delete_returns_false_when_zero_rows() {
        UUID missingId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(runRepository.deleteByIdAndUserId(missingId, userId)).thenReturn(0L);

        assertThat(service.deleteRunForUser(missingId, userId)).isFalse();

        verify(runRepository, only()).deleteByIdAndUserId(missingId, userId);
    }

    @Test
    void p48_t3_wrong_owner_returns_false() {
        UUID runId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(runRepository.deleteByIdAndUserId(runId, userId)).thenReturn(0L);

        assertThat(service.deleteRunForUser(runId, userId)).isFalse();

        verify(runRepository, times(1)).deleteByIdAndUserId(runId, userId);
    }

    @Test
    void p48_t4_null_run_id_throws() {
        UUID userId = UUID.randomUUID();
        assertThatThrownBy(() -> service.deleteRunForUser(null, userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("runId");
    }

    @Test
    void p48_t5_null_user_id_throws() {
        UUID runId = UUID.randomUUID();
        assertThatThrownBy(() -> service.deleteRunForUser(runId, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("userId");
    }

    @Test
    void p48_t6_delete_does_not_call_other_public_methods() {
        RuntimeTraceRegressionSuiteRunPersistenceMapper mapper = new RuntimeTraceRegressionSuiteRunPersistenceMapper();
        RuntimeTraceRegressionSuiteRunPersistenceService spy =
                Mockito.spy(
                        new RuntimeTraceRegressionSuiteRunPersistenceService(
                                runRepository,
                                entryRepository,
                                mapper,
                                Clock.fixed(Instant.parse("2026-01-02T12:00:00Z"), ZoneOffset.UTC)));
        UUID runId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(runRepository.deleteByIdAndUserId(runId, userId)).thenReturn(1L);

        assertThat(spy.deleteRunForUser(runId, userId)).isTrue();

        verify(spy, never()).createRun(any(), any(), any(), any());
        verify(spy, never()).loadByIdForUser(any(), any());
        verify(spy, never()).listSummariesForUser(any());
    }
}
