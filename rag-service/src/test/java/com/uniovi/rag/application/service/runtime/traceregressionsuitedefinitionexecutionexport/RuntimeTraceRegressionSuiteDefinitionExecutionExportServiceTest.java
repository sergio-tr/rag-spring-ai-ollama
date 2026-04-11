package com.uniovi.rag.application.service.runtime.traceregressionsuitedefinitionexecutionexport;

import com.uniovi.rag.application.service.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteService;
import com.uniovi.rag.application.service.runtime.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionService;
import com.uniovi.rag.domain.runtime.tracecomparisonbatch.RuntimeTraceReplayComparisonBatchOutcome;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteBatchReturnedEntryResult;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteEntry;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteEntryKind;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteOutcome;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteRequest;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteResult;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteSummary;
import com.uniovi.rag.interfaces.rest.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuntimeTraceRegressionSuiteDefinitionExecutionExportServiceTest {

    @Mock
    private RuntimeTraceRegressionSuiteDefinitionService definitionService;

    @Mock
    private RuntimeTraceRegressionSuiteService suiteService;

    @Test
    void t13_x1_happyPath_materializeThenExecuteSameReq() {
        UUID userId = UUID.randomUUID();
        UUID definitionId = UUID.randomUUID();
        RuntimeTraceRegressionSuiteRequest req =
                new RuntimeTraceRegressionSuiteRequest(
                        userId, List.of(new RuntimeTraceRegressionSuiteEntry.ByTraceIds(List.of(UUID.randomUUID()))));
        when(definitionService.materializeToSuiteRequest(definitionId, userId)).thenReturn(req);
        when(suiteService.execute(same(req))).thenReturn(minimalCompleted());
        var svc = new RuntimeTraceRegressionSuiteDefinitionExecutionExportService(definitionService, suiteService);
        svc.exportByDefinitionId(definitionId, userId);
        InOrder inOrder = inOrder(definitionService, suiteService);
        inOrder.verify(definitionService).materializeToSuiteRequest(eq(definitionId), eq(userId));
        inOrder.verify(suiteService).execute(same(req));
        verify(suiteService, times(1)).execute(any());
    }

    @Test
    void t14_x2_happyPath_materializeThenExecuteSameReq() {
        UUID userId = UUID.randomUUID();
        UUID definitionId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        RuntimeTraceRegressionSuiteRequest req =
                new RuntimeTraceRegressionSuiteRequest(
                        userId,
                        List.of(
                                new RuntimeTraceRegressionSuiteEntry.ByConversation(
                                        conversationId, Optional.empty(), Optional.empty(), Optional.empty())));
        when(definitionService.materializeToSuiteRequest(definitionId, userId)).thenReturn(req);
        when(suiteService.execute(same(req))).thenReturn(minimalCompleted());
        var svc = new RuntimeTraceRegressionSuiteDefinitionExecutionExportService(definitionService, suiteService);
        svc.exportByDefinitionIdAndConversation(definitionId, conversationId, userId);
        InOrder inOrder = inOrder(definitionService, suiteService);
        inOrder.verify(definitionService).materializeToSuiteRequest(eq(definitionId), eq(userId));
        inOrder.verify(suiteService).execute(same(req));
        verify(suiteService, times(1)).execute(any());
    }

    @Test
    void t15_zipExceedsMax_executeCalledOnceOnly() {
        UUID userId = UUID.randomUUID();
        UUID definitionId = UUID.randomUUID();
        RuntimeTraceRegressionSuiteRequest req =
                new RuntimeTraceRegressionSuiteRequest(userId, List.of(new RuntimeTraceRegressionSuiteEntry.ByTraceIds(List.of())));
        when(definitionService.materializeToSuiteRequest(definitionId, userId)).thenReturn(req);
        when(suiteService.execute(same(req))).thenReturn(minimalCompleted());
        var svc = new RuntimeTraceRegressionSuiteDefinitionExecutionExportService(definitionService, suiteService, 1L);
        assertThatThrownBy(() -> svc.exportByDefinitionId(definitionId, userId))
                .isInstanceOf(RuntimeTraceRegressionSuiteDefinitionExecutionExportSizeExceededException.class);
        verify(suiteService, times(1)).execute(any());
    }

    @Test
    void t16_materializeNotFound_neverExecute() {
        UUID userId = UUID.randomUUID();
        UUID definitionId = UUID.randomUUID();
        when(definitionService.materializeToSuiteRequest(definitionId, userId)).thenThrow(new NotFoundException("x"));
        var svc = new RuntimeTraceRegressionSuiteDefinitionExecutionExportService(definitionService, suiteService);
        assertThatThrownBy(() -> svc.exportByDefinitionId(definitionId, userId)).isInstanceOf(NotFoundException.class);
        verify(suiteService, never()).execute(any());
    }

    private static RuntimeTraceRegressionSuiteResult minimalCompleted() {
        var row =
                new RuntimeTraceRegressionSuiteBatchReturnedEntryResult(
                        0,
                        RuntimeTraceRegressionSuiteEntryKind.BY_TRACE_IDS,
                        "echo",
                        RuntimeTraceReplayComparisonBatchOutcome.COMPLETED_ALL_EXACT_MATCH,
                        1,
                        1,
                        1);
        return new RuntimeTraceRegressionSuiteResult(
                RuntimeTraceRegressionSuiteOutcome.COMPLETED_ALL_BATCH_RETURNS,
                new RuntimeTraceRegressionSuiteSummary(1, 1, 1, 0, 0),
                List.of(row));
    }
}
