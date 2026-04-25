package com.uniovi.rag.application.service.runtime.traceregressionsuitedefinitionexport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.uniovi.rag.application.service.runtime.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionService;
import com.uniovi.rag.domain.runtime.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionEntrySnapshot;
import com.uniovi.rag.domain.runtime.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionSnapshot;
import com.uniovi.rag.interfaces.rest.dto.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionDetailDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuntimeTraceRegressionSuiteDefinitionExportServiceZipTest {

    @Mock
    private RuntimeTraceRegressionSuiteDefinitionService definitionService;

    private static ObjectMapper fd4ObjectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(SerializationFeature.INDENT_OUTPUT)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }

    @Test
    void t8_t9_t10_zipOrderStoredAndManifestAndDefinitionMatch() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID definitionId = UUID.randomUUID();
        RuntimeTraceRegressionSuiteDefinitionSnapshot snapshot =
                new RuntimeTraceRegressionSuiteDefinitionSnapshot(
                        definitionId,
                        "suite",
                        null,
                        1,
                        Instant.parse("2024-03-01T12:00:00Z"),
                        Instant.parse("2024-03-02T12:00:00Z"),
                        List.of(new RuntimeTraceRegressionSuiteDefinitionEntrySnapshot.ByTraceIds(List.of(UUID.randomUUID()))));
        when(definitionService.loadByIdForUser(eq(definitionId), eq(userId))).thenReturn(Optional.of(snapshot));

        var svc = new RuntimeTraceRegressionSuiteDefinitionExportService(definitionService);
        RuntimeTraceRegressionSuiteDefinitionExportArtifact art = svc.exportDefinitionZip(definitionId, userId);

        ObjectMapper om = fd4ObjectMapper();

        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(art.content()))) {
            ZipEntry e1 = zin.getNextEntry();
            assertThat(e1.getName()).isEqualTo("manifest.json");
            assertThat(e1.getMethod()).isEqualTo(ZipEntry.STORED);
            byte[] manBytes = zin.readNBytes((int) e1.getSize());
            JsonNode man = om.readTree(manBytes);
            assertThat(man.get("zipSizeBytes").asLong()).isEqualTo(art.content().length);
            assertThat(man.get("truncated").asBoolean()).isFalse();
            assertThat(man.get("exportKind").asText()).isEqualTo("REGRESSION_SUITE_DEFINITION");
            assertThat(man.get("selectorType").asText()).isEqualTo("SAVED_DEFINITION_BY_ID");
            assertThat(man.get("definitionId").asText()).isEqualTo(man.get("scope").get("definitionId").asText());

            ZipEntry e2 = zin.getNextEntry();
            assertThat(e2.getName()).isEqualTo("definition.json");
            assertThat(e2.getMethod()).isEqualTo(ZipEntry.STORED);
            byte[] defBytes = zin.readNBytes((int) e2.getSize());
            assertThat(om.readValue(defBytes, RuntimeTraceRegressionSuiteDefinitionDetailDto.class))
                    .isEqualTo(RuntimeTraceRegressionSuiteDefinitionDetailDto.fromSnapshot(snapshot));
            assertThat(zin.getNextEntry()).isNull();
        }
    }

    @Test
    void t13_buildZipBytes_converges() throws Exception {
        var svc = new RuntimeTraceRegressionSuiteDefinitionExportService(definitionService);
        Instant generatedAt = Instant.parse("2024-05-01T00:00:00Z");
        UUID userId = UUID.randomUUID();
        UUID definitionId = UUID.randomUUID();
        byte[] definitionJsonUtf8 = "{\"x\":1}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] zip = svc.buildZipBytes(generatedAt, userId, definitionId, definitionJsonUtf8);

        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry e1 = zin.getNextEntry();
            assertThat(e1.getName()).isEqualTo("manifest.json");
            byte[] manBytes = zin.readNBytes((int) e1.getSize());
            JsonNode man = fd4ObjectMapper().readTree(manBytes);
            assertThat(man.get("zipSizeBytes").asLong()).isEqualTo(zip.length);
            ZipEntry e2 = zin.getNextEntry();
            assertThat(e2.getName()).isEqualTo("definition.json");
            assertThat(zin.getNextEntry()).isNull();
        }
    }

    @Test
    void exportDefinitionZip_throwsWhenZipExceedsMax() throws Exception {
        List<UUID> manyIds = new ArrayList<>();
        for (int i = 0; i < 80; i++) {
            manyIds.add(UUID.randomUUID());
        }
        UUID definitionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        RuntimeTraceRegressionSuiteDefinitionSnapshot snapshot =
                new RuntimeTraceRegressionSuiteDefinitionSnapshot(
                        definitionId,
                        "n",
                        null,
                        1,
                        Instant.parse("2024-01-01T00:00:00Z"),
                        Instant.parse("2024-01-02T00:00:00Z"),
                        List.of(new RuntimeTraceRegressionSuiteDefinitionEntrySnapshot.ByTraceIds(manyIds)));
        when(definitionService.loadByIdForUser(eq(definitionId), eq(userId))).thenReturn(Optional.of(snapshot));

        var strictMapper =
                new ObjectMapper()
                        .registerModule(new JavaTimeModule())
                        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                        .disable(SerializationFeature.INDENT_OUTPUT)
                        .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
        int defJsonLen =
                strictMapper
                        .writeValueAsBytes(RuntimeTraceRegressionSuiteDefinitionDetailDto.fromSnapshot(snapshot))
                        .length;
        assertThat(defJsonLen).isGreaterThan(900);

        var svc = new RuntimeTraceRegressionSuiteDefinitionExportService(definitionService, 1024L);
        assertThatThrownBy(() -> svc.exportDefinitionZip(definitionId, userId))
                .isInstanceOf(RuntimeTraceRegressionSuiteDefinitionExportSizeExceededException.class)
                .hasMessage("definition export exceeds max ZIP size");
    }
}
