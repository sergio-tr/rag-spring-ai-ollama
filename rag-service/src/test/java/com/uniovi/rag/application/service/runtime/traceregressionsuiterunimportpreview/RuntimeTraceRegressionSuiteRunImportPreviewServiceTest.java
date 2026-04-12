package com.uniovi.rag.application.service.runtime.traceregressionsuiterunimportpreview;

import com.uniovi.rag.application.service.runtime.traceregressionsuiterunimport.RunImportZipTestUtil;
import com.uniovi.rag.interfaces.rest.dto.traceregressionsuiterunimportpreview.RuntimeTraceRegressionSuiteRunImportPreviewResponseDto;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuntimeTraceRegressionSuiteRunImportPreviewServiceTest {

    @Test
    void t14_validFixture_returnsImportablePreview() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        byte[] zip = RunImportZipTestUtil.buildAdHocEmptyRunZip(runId, userId);

        RuntimeTraceRegressionSuiteRunImportPreviewService svc = new RuntimeTraceRegressionSuiteRunImportPreviewService();
        RuntimeTraceRegressionSuiteRunImportPreviewResponseDto dto = svc.previewImportZip(zip);

        assertThat(dto).isNotNull();
        assertThat(dto.importable()).isTrue();
        assertThat(dto.warnings()).isEmpty();
        assertThat(dto.run()).isNotNull();
        assertThat(dto.run().id()).isEqualTo(runId);
    }

    @Test
    void p55_p53_fixture_returns_preview() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        UUID defId = UUID.randomUUID();
        byte[] zip = RunImportZipTestUtil.buildSavedDefinitionScopedEmptyRunZip(runId, userId, defId);

        RuntimeTraceRegressionSuiteRunImportPreviewService svc = new RuntimeTraceRegressionSuiteRunImportPreviewService();
        RuntimeTraceRegressionSuiteRunImportPreviewResponseDto dto = svc.previewImportZipForDefinition(zip, defId);

        assertThat(dto.importable()).isTrue();
        assertThat(dto.warnings()).isEmpty();
        assertThat(dto.run().id()).isEqualTo(runId);
        assertThat(dto.run().definitionId()).isEqualTo(defId);
    }

    @Test
    void p55_global_zip_rejects_invalid_manifest() throws Exception {
        UUID defId = UUID.randomUUID();
        byte[] zip = RunImportZipTestUtil.buildAdHocEmptyRunZip(UUID.randomUUID(), UUID.randomUUID());

        RuntimeTraceRegressionSuiteRunImportPreviewService svc = new RuntimeTraceRegressionSuiteRunImportPreviewService();
        assertThatThrownBy(() -> svc.previewImportZipForDefinition(zip, defId))
                .isInstanceOf(RuntimeTraceRegressionSuiteRunImportPreviewRejectedException.class)
                .hasMessage("invalid manifest")
                .hasNoCause();
    }

    @Test
    void p55_scope_definition_id_mismatch_path_rejects_invalid_manifest() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        UUID pathDefId = UUID.randomUUID();
        UUID scopeDefId = UUID.randomUUID();
        byte[] zip =
                RunImportZipTestUtil.buildSavedDefinitionScopedZipWrongScopeDefinitionId(
                        runId, userId, pathDefId, scopeDefId);

        RuntimeTraceRegressionSuiteRunImportPreviewService svc = new RuntimeTraceRegressionSuiteRunImportPreviewService();
        assertThatThrownBy(() -> svc.previewImportZipForDefinition(zip, pathDefId))
                .isInstanceOf(RuntimeTraceRegressionSuiteRunImportPreviewRejectedException.class)
                .hasMessage("invalid manifest")
                .hasNoCause();
    }
}
