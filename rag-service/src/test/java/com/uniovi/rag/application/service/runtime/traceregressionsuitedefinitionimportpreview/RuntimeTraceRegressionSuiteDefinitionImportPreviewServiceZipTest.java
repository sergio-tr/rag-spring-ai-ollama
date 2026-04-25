package com.uniovi.rag.application.service.runtime.traceregressionsuitedefinitionimportpreview;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.uniovi.rag.application.service.runtime.traceregressionsuitedefinitionimport.P39ImportZipTestUtil;
import com.uniovi.rag.domain.runtime.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionEntrySnapshot;
import com.uniovi.rag.domain.runtime.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionSnapshot;
import com.uniovi.rag.interfaces.rest.dto.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionDetailDto;
import com.uniovi.rag.interfaces.rest.dto.traceregressionsuitedefinitionimportpreview.RuntimeTraceRegressionSuiteDefinitionImportPreviewResponseDto;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuntimeTraceRegressionSuiteDefinitionImportPreviewServiceZipTest {

    @Test
    void t15_validZip_returnsPreviewDto() throws Exception {
        var fd4 = P39ImportZipTestUtil.fd4ObjectMapper();
        UUID userId = UUID.randomUUID();
        UUID definitionId = UUID.randomUUID();
        RuntimeTraceRegressionSuiteDefinitionSnapshot snap =
                new RuntimeTraceRegressionSuiteDefinitionSnapshot(
                        definitionId,
                        "n",
                        "d",
                        1,
                        Instant.parse("2020-01-01T00:00:00Z"),
                        Instant.parse("2020-01-02T00:00:00Z"),
                        List.of(
                                new RuntimeTraceRegressionSuiteDefinitionEntrySnapshot.ByTraceIds(
                                        List.of(UUID.randomUUID()))));
        byte[] defJson = fd4.writeValueAsBytes(RuntimeTraceRegressionSuiteDefinitionDetailDto.fromSnapshot(snap));
        byte[] zip =
                P39ImportZipTestUtil.buildConvergedP38Zip(
                        fd4, Instant.parse("2024-06-01T00:00:00Z"), userId, definitionId, defJson);

        var svc = new RuntimeTraceRegressionSuiteDefinitionImportPreviewService();
        RuntimeTraceRegressionSuiteDefinitionImportPreviewResponseDto dto = svc.previewImportZip(zip);

        assertThat(dto).isNotNull();
        assertThat(dto.importable()).isTrue();
        assertThat(dto.warnings()).isEmpty();
        assertThat(dto.definition()).isNotNull();
    }

    @Test
    void t16_invalidManifestJson_throwsInvalidManifestMessage() throws Exception {
        byte[] defJson = "{}".getBytes();
        byte[] zip = P39ImportZipTestUtil.writeTwoStored("{".getBytes(), defJson);

        var svc = new RuntimeTraceRegressionSuiteDefinitionImportPreviewService();
        assertThatThrownBy(() -> svc.previewImportZip(zip))
                .isInstanceOf(RuntimeTraceRegressionSuiteDefinitionImportPreviewRejectedException.class)
                .hasMessage("invalid manifest");
    }

    @Test
    void t18_zipSizeBytesOneLessThanBody_throwsInvalidManifestMessage() throws Exception {
        var fd4 = P39ImportZipTestUtil.fd4ObjectMapper();
        UUID userId = UUID.randomUUID();
        UUID definitionId = UUID.randomUUID();
        RuntimeTraceRegressionSuiteDefinitionSnapshot snap =
                new RuntimeTraceRegressionSuiteDefinitionSnapshot(
                        definitionId,
                        "n",
                        null,
                        1,
                        Instant.parse("2020-01-01T00:00:00Z"),
                        Instant.parse("2020-01-02T00:00:00Z"),
                        List.of(
                                new RuntimeTraceRegressionSuiteDefinitionEntrySnapshot.ByTraceIds(
                                        List.of(UUID.randomUUID()))));
        byte[] defJson = fd4.writeValueAsBytes(RuntimeTraceRegressionSuiteDefinitionDetailDto.fromSnapshot(snap));

        ObjectNode man = fd4.createObjectNode();
        man.put("exportKind", "REGRESSION_SUITE_DEFINITION");
        man.put("schemaVersion", 1);
        man.put("truncated", false);
        long declaredZipSizeBytes = 0L;
        byte[] zip = null;
        for (int iter = 0; iter < 128; iter++) {
            man.put("zipSizeBytes", declaredZipSizeBytes);
            byte[] candidate = P39ImportZipTestUtil.writeTwoStored(fd4.writeValueAsBytes(man), defJson);
            long needed = candidate.length - 1L;
            if (declaredZipSizeBytes == needed) {
                zip = candidate;
                break;
            }
            declaredZipSizeBytes = needed;
        }
        assertThat(zip).as("expected fixed point for zipSizeBytes == body.length - 1").isNotNull();
        final byte[] zipBytes = zip;

        var svc = new RuntimeTraceRegressionSuiteDefinitionImportPreviewService();
        assertThatThrownBy(() -> svc.previewImportZip(zipBytes))
                .isInstanceOf(RuntimeTraceRegressionSuiteDefinitionImportPreviewRejectedException.class)
                .hasMessage("invalid manifest");
    }
}
