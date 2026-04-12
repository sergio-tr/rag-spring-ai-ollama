package com.uniovi.rag.application.service.runtime.traceregressionsuiterunimport;

import com.uniovi.rag.application.service.runtime.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunPersistenceService;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteResult;
import com.uniovi.rag.domain.runtime.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunSourceType;
import com.uniovi.rag.interfaces.rest.dto.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunDetailDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuntimeTraceRegressionSuiteRunImportServiceTest {

    @Mock
    private RuntimeTraceRegressionSuiteRunPersistenceService persistence;

    @InjectMocks
    private RuntimeTraceRegressionSuiteRunImportService importService;

    @Test
    void t10_createRunOnce_resultMatchesDetailDtoConversion() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        UUID createdId = UUID.randomUUID();
        byte[] zip = RunImportZipTestUtil.buildAdHocEmptyRunZip(runId, userId);
        byte[] runJson = RunImportZipTestUtil.extractRunJsonBytes(zip);
        RuntimeTraceRegressionSuiteRunDetailDto detail =
                RunImportZipTestUtil.FD4.readValue(runJson, RuntimeTraceRegressionSuiteRunDetailDto.class);
        RuntimeTraceRegressionSuiteResult expected = detail.toRuntimeTraceRegressionSuiteResultForImport();

        when(persistence.createRun(any(), any(), any(), any())).thenReturn(createdId);

        UUID returned = importService.importRunZip(zip, userId);

        assertThat(returned).isEqualTo(createdId);
        ArgumentCaptor<RuntimeTraceRegressionSuiteResult> resultCaptor =
                ArgumentCaptor.forClass(RuntimeTraceRegressionSuiteResult.class);
        verify(persistence, times(1))
                .createRun(
                        eq(userId),
                        eq(RuntimeTraceRegressionSuiteRunSourceType.AD_HOC),
                        eq(Optional.empty()),
                        resultCaptor.capture());
        assertThat(resultCaptor.getValue()).isEqualTo(expected);
        verify(persistence, never()).loadByIdForUser(any(), any());
        verify(persistence, never()).listSummariesForUser(any());
    }

    @Test
    void t12a_adHocWithDefinitionId_rejected() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        UUID defId = UUID.randomUUID();
        byte[] zip = RunImportZipTestUtil.buildZipAdHocWithDefinitionId(runId, userId, defId);

        assertThatThrownBy(() -> importService.importRunZip(zip, userId))
                .isInstanceOf(RuntimeTraceRegressionSuiteRunImportRejectedException.class)
                .hasMessage("invalid sourceType definitionId pairing")
                .hasNoCause();

        verify(persistence, never()).createRun(any(), any(), any(), any());
    }

    @Test
    void t12b_savedDefinitionWithNullDefinitionId_rejected() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        byte[] zip = RunImportZipTestUtil.buildZipSavedDefinitionWithNullDefinitionId(runId, userId);

        assertThatThrownBy(() -> importService.importRunZip(zip, userId))
                .isInstanceOf(RuntimeTraceRegressionSuiteRunImportRejectedException.class)
                .hasMessage("invalid sourceType definitionId pairing")
                .hasNoCause();

        verify(persistence, never()).createRun(any(), any(), any(), any());
    }

    @Test
    void p54_createRunOnce_savedDefinitionScopedFixture() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        UUID defId = UUID.randomUUID();
        UUID createdId = UUID.randomUUID();
        byte[] zip = RunImportZipTestUtil.buildSavedDefinitionScopedEmptyRunZip(runId, userId, defId);
        byte[] runJson = RunImportZipTestUtil.extractRunJsonBytes(zip);
        RuntimeTraceRegressionSuiteRunDetailDto detail =
                RunImportZipTestUtil.FD4.readValue(runJson, RuntimeTraceRegressionSuiteRunDetailDto.class);
        RuntimeTraceRegressionSuiteResult expected = detail.toRuntimeTraceRegressionSuiteResultForImport();

        when(persistence.createRun(any(), any(), any(), any())).thenReturn(createdId);

        UUID returned = importService.importRunZipForDefinition(zip, userId, defId);

        assertThat(returned).isEqualTo(createdId);
        ArgumentCaptor<RuntimeTraceRegressionSuiteResult> resultCaptor =
                ArgumentCaptor.forClass(RuntimeTraceRegressionSuiteResult.class);
        verify(persistence, times(1))
                .createRun(
                        eq(userId),
                        eq(RuntimeTraceRegressionSuiteRunSourceType.SAVED_DEFINITION),
                        eq(Optional.of(defId)),
                        resultCaptor.capture());
        assertThat(resultCaptor.getValue()).isEqualTo(expected);
    }

    @Test
    void p54_globalZip_rejects_invalid_manifest() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID defId = UUID.randomUUID();
        byte[] zip = RunImportZipTestUtil.buildAdHocEmptyRunZip(UUID.randomUUID(), userId);

        assertThatThrownBy(() -> importService.importRunZipForDefinition(zip, userId, defId))
                .isInstanceOf(RuntimeTraceRegressionSuiteRunImportRejectedException.class)
                .hasMessage("invalid manifest")
                .hasNoCause();

        verify(persistence, never()).createRun(any(), any(), any(), any());
    }

    @Test
    void p54_scopeDefinitionIdMismatchPath_rejects_invalid_manifest() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        UUID pathDefId = UUID.randomUUID();
        UUID scopeDefId = UUID.randomUUID();
        byte[] zip =
                RunImportZipTestUtil.buildSavedDefinitionScopedZipWrongScopeDefinitionId(
                        runId, userId, pathDefId, scopeDefId);

        assertThatThrownBy(() -> importService.importRunZipForDefinition(zip, userId, pathDefId))
                .isInstanceOf(RuntimeTraceRegressionSuiteRunImportRejectedException.class)
                .hasMessage("invalid manifest")
                .hasNoCause();

        verify(persistence, never()).createRun(any(), any(), any(), any());
    }
}
