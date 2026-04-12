package com.uniovi.rag.application.service.runtime.traceregressionsuiterunexport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.uniovi.rag.application.service.runtime.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunPersistenceService;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteEntryKind;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteOutcome;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteSummary;
import com.uniovi.rag.domain.runtime.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunEntrySnapshot;
import com.uniovi.rag.domain.runtime.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunId;
import com.uniovi.rag.domain.runtime.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunPersistedExecutionStatus;
import com.uniovi.rag.domain.runtime.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunSnapshot;
import com.uniovi.rag.domain.runtime.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunSourceType;
import com.uniovi.rag.interfaces.rest.dto.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunDetailDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuntimeTraceRegressionSuiteRunExportServiceZipTest {

    @Mock
    private RuntimeTraceRegressionSuiteRunPersistenceService runPersistenceService;

    private static ObjectMapper fd4ObjectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(SerializationFeature.INDENT_OUTPUT)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }

    private static List<RuntimeTraceRegressionSuiteRunEntrySnapshot> repeatedEntries(int count) {
        String echo = "a".repeat(256);
        List<RuntimeTraceRegressionSuiteRunEntrySnapshot> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(
                    new RuntimeTraceRegressionSuiteRunEntrySnapshot(
                            (short) i,
                            RuntimeTraceRegressionSuiteEntryKind.BY_TRACE_IDS,
                            echo,
                            RuntimeTraceRegressionSuiteRunPersistedExecutionStatus.BATCH_RETURNED,
                            Optional.empty(),
                            Optional.of(1),
                            Optional.of(1),
                            Optional.of(1),
                            Optional.empty(),
                            Optional.empty()));
        }
        return list;
    }

    private static RuntimeTraceRegressionSuiteRunSnapshot snapshot(UUID runId, UUID userId, int entryCount) {
        return new RuntimeTraceRegressionSuiteRunSnapshot(
                new RuntimeTraceRegressionSuiteRunId(runId),
                userId,
                RuntimeTraceRegressionSuiteRunSourceType.AD_HOC,
                Optional.empty(),
                RuntimeTraceRegressionSuiteOutcome.COMPLETED_ALL_BATCH_RETURNS,
                new RuntimeTraceRegressionSuiteSummary(entryCount, entryCount, entryCount, 0, 0),
                Instant.parse("2024-03-01T12:00:00Z"),
                repeatedEntries(entryCount));
    }

    @Test
    void t7_t8_t9_zipOrderStoredManifestAndRunJson() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        RuntimeTraceRegressionSuiteRunSnapshot snap = snapshot(runId, userId, 1);
        when(runPersistenceService.loadByIdForUser(eq(runId), eq(userId))).thenReturn(Optional.of(snap));

        var svc = new RuntimeTraceRegressionSuiteRunExportService(runPersistenceService);
        RuntimeTraceRegressionSuiteRunExportArtifact art = svc.exportRunZip(runId, userId);

        ObjectMapper om = fd4ObjectMapper();

        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(art.content()))) {
            ZipEntry e1 = zin.getNextEntry();
            assertThat(e1.getName()).isEqualTo("manifest.json");
            assertThat(e1.getMethod()).isEqualTo(ZipEntry.STORED);
            byte[] manBytes = zin.readNBytes((int) e1.getSize());
            JsonNode man = om.readTree(manBytes);
            assertThat(man.get("zipSizeBytes").asLong()).isEqualTo(art.content().length);
            assertThat(man.get("truncated").asBoolean()).isFalse();
            assertThat(man.get("exportKind").asText()).isEqualTo("REGRESSION_SUITE_RUN");
            assertThat(man.get("selectorType").asText()).isEqualTo("SAVED_RUN_BY_ID");
            assertThat(man.get("runId").asText()).isEqualTo(man.get("scope").get("runId").asText());

            ZipEntry e2 = zin.getNextEntry();
            assertThat(e2.getName()).isEqualTo("run.json");
            assertThat(e2.getMethod()).isEqualTo(ZipEntry.STORED);
            byte[] runBytes = zin.readNBytes((int) e2.getSize());
            assertThat(om.readValue(runBytes, RuntimeTraceRegressionSuiteRunDetailDto.class))
                    .isEqualTo(RuntimeTraceRegressionSuiteRunDetailDto.fromSnapshot(snap));
            assertThat(zin.getNextEntry()).isNull();
        }
    }

    @Test
    void t10_buildZipBytes_converges() throws Exception {
        var svc = new RuntimeTraceRegressionSuiteRunExportService(runPersistenceService);
        Instant generatedAt = Instant.parse("2024-05-01T00:00:00Z");
        UUID userId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        RuntimeTraceRegressionSuiteRunSnapshot snap = snapshot(runId, userId, 0);
        byte[] runJsonUtf8 = "{\"x\":1}".getBytes(StandardCharsets.UTF_8);
        byte[] zip = svc.buildZipBytes(generatedAt, userId, runId, snap, runJsonUtf8);

        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry e1 = zin.getNextEntry();
            assertThat(e1.getName()).isEqualTo("manifest.json");
            byte[] manBytes = zin.readNBytes((int) e1.getSize());
            JsonNode man = fd4ObjectMapper().readTree(manBytes);
            assertThat(man.get("zipSizeBytes").asLong()).isEqualTo(zip.length);
            ZipEntry e2 = zin.getNextEntry();
            assertThat(e2.getName()).isEqualTo("run.json");
            assertThat(zin.getNextEntry()).isNull();
        }
    }

    @Test
    void t12_storedEntries_crcMatchesPayload() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        RuntimeTraceRegressionSuiteRunSnapshot snap = snapshot(runId, userId, 1);
        when(runPersistenceService.loadByIdForUser(eq(runId), eq(userId))).thenReturn(Optional.of(snap));

        var svc = new RuntimeTraceRegressionSuiteRunExportService(runPersistenceService);
        byte[] zip = svc.exportRunZip(runId, userId).content();

        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (int i = 0; i < 2; i++) {
                ZipEntry entry = zin.getNextEntry();
                assertThat(entry.getMethod()).isEqualTo(ZipEntry.STORED);
                byte[] payload = zin.readNBytes((int) entry.getSize());
                CRC32 crc = new CRC32();
                crc.update(payload);
                assertThat(entry.getCrc()).isEqualTo(crc.getValue());
                assertThat(entry.getSize()).isEqualTo(payload.length);
            }
            assertThat(zin.getNextEntry()).isNull();
        }
    }

    @Test
    void fd17_exportRunZip_throwsWhenZipExceedsMax() {
        UUID runId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        RuntimeTraceRegressionSuiteRunSnapshot snap = snapshot(runId, userId, 12);
        when(runPersistenceService.loadByIdForUser(eq(runId), eq(userId))).thenReturn(Optional.of(snap));

        var svc = new RuntimeTraceRegressionSuiteRunExportService(runPersistenceService, 1024L);
        assertThatThrownBy(() -> svc.exportRunZip(runId, userId))
                .isInstanceOf(RuntimeTraceRegressionSuiteRunExportSizeExceededException.class)
                .hasMessage("run export exceeds max ZIP size");
        verify(runPersistenceService, times(1)).loadByIdForUser(runId, userId);
        verify(runPersistenceService, never()).createRun(any(), any(), any(), any());
        verify(runPersistenceService, never()).listSummariesForUser(any());
    }
}
