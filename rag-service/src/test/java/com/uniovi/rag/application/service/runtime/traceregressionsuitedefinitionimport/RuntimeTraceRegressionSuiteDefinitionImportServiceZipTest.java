package com.uniovi.rag.application.service.runtime.traceregressionsuitedefinitionimport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.application.service.runtime.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionService;
import com.uniovi.rag.domain.runtime.traceregressionsuitedefinition.CreateDefinitionCommand;
import com.uniovi.rag.domain.runtime.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionEntrySnapshot;
import com.uniovi.rag.domain.runtime.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionSnapshot;
import com.uniovi.rag.interfaces.rest.dto.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionDetailDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
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
class RuntimeTraceRegressionSuiteDefinitionImportServiceZipTest {

    @Mock
    private RuntimeTraceRegressionSuiteDefinitionService definitionService;

    @Test
    void t16_validZip_callsCreateOnce_returnsId() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID defId = UUID.randomUUID();
        UUID created = UUID.randomUUID();
        ObjectMapper om = P39ImportZipTestUtil.fd4ObjectMapper();
        RuntimeTraceRegressionSuiteDefinitionSnapshot snap =
                new RuntimeTraceRegressionSuiteDefinitionSnapshot(
                        defId,
                        "n",
                        null,
                        1,
                        Instant.parse("2020-01-01T00:00:00Z"),
                        Instant.parse("2020-01-02T00:00:00Z"),
                        List.of(new RuntimeTraceRegressionSuiteDefinitionEntrySnapshot.ByTraceIds(List.of(UUID.randomUUID()))));
        byte[] defJson = om.writeValueAsBytes(RuntimeTraceRegressionSuiteDefinitionDetailDto.fromSnapshot(snap));
        byte[] zip =
                P39ImportZipTestUtil.buildConvergedP38Zip(
                        om, Instant.parse("2024-08-01T00:00:00Z"), userId, defId, defJson);
        when(definitionService.create(eq(userId), any(CreateDefinitionCommand.class))).thenReturn(created);

        var svc = new RuntimeTraceRegressionSuiteDefinitionImportService(definitionService);
        UUID out = svc.importDefinitionZip(zip, userId);
        assertThat(out).isEqualTo(created);
        verify(definitionService, times(1)).create(eq(userId), any(CreateDefinitionCommand.class));
    }

    @Test
    void t17_invalidManifestJson_neverCreate() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID defId = UUID.randomUUID();
        ObjectMapper om = P39ImportZipTestUtil.fd4ObjectMapper();
        byte[] defJson =
                om.writeValueAsBytes(
                        RuntimeTraceRegressionSuiteDefinitionDetailDto.fromSnapshot(
                                new RuntimeTraceRegressionSuiteDefinitionSnapshot(
                                        defId,
                                        "n",
                                        null,
                                        1,
                                        Instant.parse("2020-01-01T00:00:00Z"),
                                        Instant.parse("2020-01-02T00:00:00Z"),
                                        List.of(
                                                new RuntimeTraceRegressionSuiteDefinitionEntrySnapshot.ByTraceIds(
                                                        List.of(UUID.randomUUID()))))));
        byte[] zip = P39ImportZipTestUtil.writeTwoStored("{".getBytes(), defJson);

        var svc = new RuntimeTraceRegressionSuiteDefinitionImportService(definitionService);
        assertThatThrownBy(() -> svc.importDefinitionZip(zip, userId))
                .isInstanceOf(RuntimeTraceRegressionSuiteDefinitionImportRejectedException.class)
                .hasMessageContaining("invalid manifest");
        verify(definitionService, never()).create(any(), any());
    }
}
