package com.uniovi.rag.application.service.runtime.tracereplayexport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.application.service.runtime.tracereplay.RuntimeTraceReplayService;
import com.uniovi.rag.domain.runtime.engine.ExecutionTrace;
import com.uniovi.rag.domain.runtime.tracereplay.RuntimeTraceReplayOutcome;
import com.uniovi.rag.domain.runtime.tracereplay.RuntimeTraceReplayRequest;
import com.uniovi.rag.domain.runtime.tracereplay.RuntimeTraceReplayResult;
import com.uniovi.rag.interfaces.rest.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuntimeTraceReplayExportServiceTest {

    @Mock
    private RuntimeTraceReplayService replayService;

    private UUID userId;
    private UUID traceId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        traceId = UUID.randomUUID();
    }

    @Test
    void not_found_from_replay_propagates() {
        when(replayService.replay(any(RuntimeTraceReplayRequest.class))).thenThrow(new NotFoundException("missing"));
        RuntimeTraceReplayExportService svc = new RuntimeTraceReplayExportService(replayService);
        assertThatThrownBy(() -> svc.exportByTraceId(userId, traceId)).isInstanceOf(NotFoundException.class);
        verify(replayService).replay(any(RuntimeTraceReplayRequest.class));
    }

    @Test
    void replay_invoked_exactly_once_for_trace_export() throws Exception {
        when(replayService.replay(any(RuntimeTraceReplayRequest.class))).thenReturn(successResult());
        RuntimeTraceReplayExportService svc = new RuntimeTraceReplayExportService(replayService);
        svc.exportByTraceId(userId, traceId);
        verify(replayService).replay(any(RuntimeTraceReplayRequest.class));
    }

    @Test
    void zip_has_manifest_then_replay_entries_and_manifest_zipSize_matches_response_length() throws Exception {
        when(replayService.replay(any(RuntimeTraceReplayRequest.class))).thenReturn(successResult());
        RuntimeTraceReplayExportService svc = new RuntimeTraceReplayExportService(replayService);
        RuntimeTraceReplayExportArtifact artifact = svc.exportByTraceId(userId, traceId);
        ObjectMapper om = new ObjectMapper();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(artifact.content()))) {
            ZipEntry e1 = zis.getNextEntry();
            assertThat(e1.getName()).isEqualTo("manifest.json");
            byte[] mbytes = zis.readAllBytes();
            zis.closeEntry();
            JsonNode manifest = om.readTree(new String(mbytes, StandardCharsets.UTF_8));
            assertThat(manifest.get("exportKind").asText()).isEqualTo("REPLAY");
            assertThat(manifest.get("schemaVersion").asInt()).isEqualTo(1);
            assertThat(manifest.get("truncated").asBoolean()).isFalse();
            assertThat(manifest.get("zipSizeBytes").asLong()).isEqualTo(artifact.sizeBytes());

            ZipEntry e2 = zis.getNextEntry();
            assertThat(e2.getName()).isEqualTo("replay.json");
            byte[] rbytes = zis.readAllBytes();
            zis.closeEntry();
            JsonNode replay = om.readTree(new String(rbytes, StandardCharsets.UTF_8));
            assertThat(replay.has("executionTraceJson")).isFalse();
            assertThat(replay.has("stagesJson")).isFalse();
            assertThat(replay.get("replayOutcome").asText()).isEqualTo("REPLAY_SUCCEEDED");

            assertThat(zis.getNextEntry()).isNull();
        }
    }

    @Test
    void unsupported_outcome_still_produces_zip_after_trace_resolved() throws Exception {
        when(replayService.replay(any(RuntimeTraceReplayRequest.class)))
                .thenReturn(RuntimeTraceReplayResult.unsupported(
                        RuntimeTraceReplayOutcome.UNSUPPORTED_ROUTE_FAMILY, Optional.of("x")));
        RuntimeTraceReplayExportService svc = new RuntimeTraceReplayExportService(replayService);
        RuntimeTraceReplayExportArtifact artifact = svc.exportByTraceId(userId, traceId);
        assertThat(artifact.content().length).isGreaterThan(0);
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(artifact.content()))) {
            assertThat(zis.getNextEntry().getName()).isEqualTo("manifest.json");
        }
    }

    @Test
    void replay_failed_safe_produces_zip() throws Exception {
        when(replayService.replay(any(RuntimeTraceReplayRequest.class)))
                .thenReturn(RuntimeTraceReplayResult.failedSafe("oops"));
        RuntimeTraceReplayExportService svc = new RuntimeTraceReplayExportService(replayService);
        RuntimeTraceReplayExportArtifact artifact = svc.exportByTraceId(userId, traceId);
        assertThat(artifact.sizeBytes()).isEqualTo(artifact.content().length);
        ObjectMapper om = new ObjectMapper();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(artifact.content()))) {
            zis.getNextEntry();
            JsonNode manifest = om.readTree(new String(zis.readAllBytes(), StandardCharsets.UTF_8));
            zis.closeEntry();
            assertThat(manifest.get("replayOutcome").asText()).isEqualTo("REPLAY_FAILED_SAFE");
        }
    }

    @Test
    void zip_over_cap_throws() {
        when(replayService.replay(any(RuntimeTraceReplayRequest.class))).thenReturn(successResult());
        RuntimeTraceReplayExportService svc = new RuntimeTraceReplayExportService(replayService, 400L);
        assertThatThrownBy(() -> svc.exportByTraceId(userId, traceId))
                .isInstanceOf(RuntimeTraceReplayExportSizeExceededException.class);
    }

    private static RuntimeTraceReplayResult successResult() {
        return RuntimeTraceReplayResult.success("ok", ExecutionTrace.placeholder());
    }
}
