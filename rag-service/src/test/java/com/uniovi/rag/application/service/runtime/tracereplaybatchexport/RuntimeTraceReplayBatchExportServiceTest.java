package com.uniovi.rag.application.service.runtime.tracereplaybatchexport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.application.service.runtime.tracereplaybatch.RuntimeTraceReplayBatchService;
import com.uniovi.rag.domain.runtime.tracereplay.RuntimeTraceReplayOutcome;
import com.uniovi.rag.domain.runtime.tracereplaybatch.RuntimeTraceReplayBatchItemOutcome;
import com.uniovi.rag.domain.runtime.tracereplaybatch.RuntimeTraceReplayBatchItemResult;
import com.uniovi.rag.domain.runtime.tracereplaybatch.RuntimeTraceReplayBatchMode;
import com.uniovi.rag.domain.runtime.tracereplaybatch.RuntimeTraceReplayBatchOutcome;
import com.uniovi.rag.domain.runtime.tracereplaybatch.RuntimeTraceReplayBatchRequest;
import com.uniovi.rag.domain.runtime.tracereplaybatch.RuntimeTraceReplayBatchResult;
import com.uniovi.rag.domain.runtime.tracereplaybatch.RuntimeTraceReplayBatchSummary;
import com.uniovi.rag.interfaces.rest.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuntimeTraceReplayBatchExportServiceTest {

    @Mock
    private RuntimeTraceReplayBatchService batchService;

    private RuntimeTraceReplayBatchExportService exportService;

    private final ObjectMapper testMapper = new ObjectMapper();

    private UUID userId;

    @BeforeEach
    void setUp() {
        exportService = new RuntimeTraceReplayBatchExportService(batchService, 2097152L);
        userId = UUID.randomUUID();
    }

    @Test
    void execute_called_exactly_once() {
        UUID t = UUID.randomUUID();
        when(batchService.execute(any(RuntimeTraceReplayBatchRequest.class))).thenReturn(emptySelectionByTraceIds(0, 0));
        exportService.exportByTraceIds(userId, List.of(t));
        verify(batchService, times(1)).execute(any(RuntimeTraceReplayBatchRequest.class));
    }

    @Test
    void not_attempted_throws() {
        var sum = RuntimeTraceReplayBatchSummary.zeros();
        when(batchService.execute(any()))
                .thenReturn(new RuntimeTraceReplayBatchResult(1, 0, RuntimeTraceReplayBatchOutcome.NOT_ATTEMPTED, sum, List.of()));
        assertThatThrownBy(() -> exportService.exportByTraceIds(userId, List.of(UUID.randomUUID())))
                .isInstanceOf(RuntimeTraceReplayBatchExportNotAttemptedException.class);
    }

    @Test
    void not_found_propagates_from_batch() {
        when(batchService.execute(any())).thenThrow(new NotFoundException("conversation not found"));
        UUID cid = UUID.randomUUID();
        assertThatThrownBy(
                        () ->
                                exportService.exportByConversation(
                                        userId, cid, Optional.empty(), Optional.empty(), Optional.empty()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void zip_has_manifest_then_batch_manifest_zipSize_matches_length() throws Exception {
        when(batchService.execute(any())).thenReturn(emptySelectionByTraceIds(0, 0));
        var artifact = exportService.exportByTraceIds(userId, List.of());
        assertThat(artifact.sizeBytes()).isEqualTo(artifact.content().length);

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(artifact.content()))) {
            ZipEntry e1 = zis.getNextEntry();
            assertThat(e1.getName()).isEqualTo("manifest.json");
            byte[] mbytes = zis.readAllBytes();
            JsonNode manifest = testMapper.readTree(mbytes);
            assertThat(manifest.get("schemaVersion").isInt()).isTrue();
            assertThat(manifest.get("schemaVersion").asInt()).isEqualTo(1);
            assertThat(manifest.get("exportKind").asText()).isEqualTo(RuntimeTraceReplayBatchExportService.EXPORT_KIND);
            assertThat(manifest.get("truncated").asBoolean()).isFalse();
            assertThat(manifest.get("zipSizeBytes").asLong()).isEqualTo(artifact.sizeBytes());

            zis.closeEntry();
            ZipEntry e2 = zis.getNextEntry();
            assertThat(e2.getName()).isEqualTo("batch.json");
            JsonNode batch = testMapper.readTree(new String(zis.readAllBytes(), StandardCharsets.UTF_8));
            assertThat(batch.get("batchOutcome").asText()).isEqualTo("EMPTY_SELECTION");
            assertThat(zis.getNextEntry()).isNull();
        }
    }

    @Test
    void oversize_throws() {
        exportService = new RuntimeTraceReplayBatchExportService(batchService, 1L);
        when(batchService.execute(any())).thenReturn(emptySelectionByTraceIds(0, 0));
        assertThatThrownBy(() -> exportService.exportByTraceIds(userId, List.of()))
                .isInstanceOf(RuntimeTraceReplayBatchExportSizeExceededException.class);
    }

    @Test
    void batch_json_item_has_no_mismatches_key() throws Exception {
        UUID tid = UUID.randomUUID();
        var item =
                new RuntimeTraceReplayBatchItemResult(
                        tid,
                        Optional.of(tid),
                        0,
                        RuntimeTraceReplayBatchItemOutcome.REPLAY_SUCCEEDED,
                        Optional.of(RuntimeTraceReplayOutcome.REPLAY_SUCCEEDED.name()),
                        "",
                        "",
                        "",
                        "",
                        0,
                        true,
                        true,
                        false,
                        false);
        var sum = new RuntimeTraceReplayBatchSummary(1, 1, 0, 0, 0, 0);
        when(batchService.execute(any()))
                .thenReturn(
                        new RuntimeTraceReplayBatchResult(
                                1,
                                1,
                                RuntimeTraceReplayBatchOutcome.COMPLETED_ALL_REPLAY_SUCCEEDED,
                                sum,
                                List.of(item)));
        var artifact = exportService.exportByTraceIds(userId, List.of(tid));
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(artifact.content()))) {
            zis.getNextEntry();
            zis.readAllBytes();
            zis.closeEntry();
            zis.getNextEntry();
            JsonNode batch = testMapper.readTree(new String(zis.readAllBytes(), StandardCharsets.UTF_8));
            assertThat(batch.get("batchMode").asText()).isEqualTo(RuntimeTraceReplayBatchMode.BY_TRACE_IDS.name());
            assertThat(batch.get("items").get(0).get("mismatches")).isNull();
        }
    }

    private static RuntimeTraceReplayBatchResult emptySelectionByTraceIds(int requested, int selected) {
        var summary = RuntimeTraceReplayBatchSummary.zeros();
        return new RuntimeTraceReplayBatchResult(
                requested, selected, RuntimeTraceReplayBatchOutcome.EMPTY_SELECTION, summary, List.of());
    }
}
