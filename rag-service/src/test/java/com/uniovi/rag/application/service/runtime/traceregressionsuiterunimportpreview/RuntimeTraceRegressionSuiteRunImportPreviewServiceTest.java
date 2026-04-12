package com.uniovi.rag.application.service.runtime.traceregressionsuiterunimportpreview;

import com.uniovi.rag.application.service.runtime.traceregressionsuiterunimport.RunImportZipTestUtil;
import com.uniovi.rag.interfaces.rest.dto.traceregressionsuiterunimportpreview.RuntimeTraceRegressionSuiteRunImportPreviewResponseDto;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

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
}
