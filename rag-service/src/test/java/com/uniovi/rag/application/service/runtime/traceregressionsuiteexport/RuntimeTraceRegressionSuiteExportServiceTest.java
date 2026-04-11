package com.uniovi.rag.application.service.runtime.traceregressionsuiteexport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.application.service.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteService;
import com.uniovi.rag.domain.runtime.tracecomparisonbatch.RuntimeTraceReplayComparisonBatchOutcome;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteBatchReturnedEntryResult;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteEntryKind;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteOutcome;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteRequest;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteResult;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteSummary;
import com.uniovi.rag.interfaces.rest.dto.traceregressionsuite.RuntimeTraceRegressionSuiteConversationExecuteRequestDto;
import com.uniovi.rag.interfaces.rest.dto.traceregressionsuite.RuntimeTraceRegressionSuiteExecuteRequestDto;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuntimeTraceRegressionSuiteExportServiceTest {

    @Mock
    private RuntimeTraceRegressionSuiteService suiteService;

    @Test
    void execute_called_exactly_once_per_export() {
        UUID uid = UUID.randomUUID();
        var result = minimalCompleted();
        when(suiteService.execute(any())).thenReturn(result);
        var svc = new RuntimeTraceRegressionSuiteExportService(suiteService);
        var body = new RuntimeTraceRegressionSuiteExecuteRequestDto(List.of());
        var req = new RuntimeTraceRegressionSuiteRequest(uid, List.of());
        svc.exportExplicit(uid, req, body);
        verify(suiteService, times(1)).execute(any());
    }

    @Test
    void zip_order_manifest_then_suite_json_and_zipSizeBytes_matches() throws Exception {
        UUID uid = UUID.randomUUID();
        var result = minimalCompleted();
        when(suiteService.execute(any())).thenReturn(result);
        var svc = new RuntimeTraceRegressionSuiteExportService(suiteService);
        var body = new RuntimeTraceRegressionSuiteExecuteRequestDto(List.of());
        var req = new RuntimeTraceRegressionSuiteRequest(uid, List.of());
        RuntimeTraceRegressionSuiteExportArtifact art = svc.exportExplicit(uid, req, body);
        assertThat(art.sizeBytes()).isEqualTo(art.content().length);

        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(art.content()))) {
            ZipEntry e1 = zin.getNextEntry();
            assertThat(e1.getName()).isEqualTo("manifest.json");
            byte[] manBytes = zin.readNBytes((int) e1.getSize());
            JsonNode man = new ObjectMapper().readTree(manBytes);
            assertThat(man.get("exportKind").asText()).isEqualTo(RuntimeTraceRegressionSuiteExportService.EXPORT_KIND);
            assertThat(man.get("schemaVersion").asInt()).isEqualTo(1);
            assertThat(man.get("truncated").asBoolean()).isFalse();
            assertThat(man.get("zipSizeBytes").asLong()).isEqualTo(art.content().length);

            ZipEntry e2 = zin.getNextEntry();
            assertThat(e2.getName()).isEqualTo("suite.json");
            byte[] suiteBytes = zin.readNBytes((int) e2.getSize());
            String suiteJson = new String(suiteBytes, StandardCharsets.UTF_8);
            assertThat(suiteJson).contains("COMPLETED_ALL_BATCH_RETURNS");
            assertThat(zin.getNextEntry()).isNull();
        }
    }

    @Test
    void oversized_zip_throws_after_single_execute() {
        UUID uid = UUID.randomUUID();
        var result = minimalCompleted();
        when(suiteService.execute(any())).thenReturn(result);
        var svc = new RuntimeTraceRegressionSuiteExportService(suiteService, 100L);
        var body = new RuntimeTraceRegressionSuiteExecuteRequestDto(List.of());
        var req = new RuntimeTraceRegressionSuiteRequest(uid, List.of());
        assertThatThrownBy(() -> svc.exportExplicit(uid, req, body))
                .isInstanceOf(RuntimeTraceRegressionSuiteExportSizeExceededException.class);
        verify(suiteService, times(1)).execute(any());
    }

    @Test
    void not_attempted_throws_after_execute() {
        UUID uid = UUID.randomUUID();
        when(suiteService.execute(any()))
                .thenReturn(
                        new RuntimeTraceRegressionSuiteResult(
                                RuntimeTraceRegressionSuiteOutcome.NOT_ATTEMPTED,
                                new RuntimeTraceRegressionSuiteSummary(0, 0, 0, 0, 0),
                                List.of()));
        var svc = new RuntimeTraceRegressionSuiteExportService(suiteService);
        var body = new RuntimeTraceRegressionSuiteExecuteRequestDto(List.of());
        var req = new RuntimeTraceRegressionSuiteRequest(uid, List.of());
        assertThatThrownBy(() -> svc.exportExplicit(uid, req, body))
                .isInstanceOf(RuntimeTraceRegressionSuiteExportNotAttemptedException.class);
        verify(suiteService, times(1)).execute(any());
    }

    @Test
    void conversation_export_passes_scope_to_manifest() throws Exception {
        UUID uid = UUID.randomUUID();
        UUID cid = UUID.randomUUID();
        var result = minimalCompleted();
        when(suiteService.execute(any())).thenReturn(result);
        var svc = new RuntimeTraceRegressionSuiteExportService(suiteService);
        var convBody = new RuntimeTraceRegressionSuiteConversationExecuteRequestDto(List.of());
        var req = new RuntimeTraceRegressionSuiteRequest(uid, List.of());
        RuntimeTraceRegressionSuiteExportArtifact art = svc.exportConversationScoped(uid, req, cid, convBody);
        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(art.content()))) {
            ZipEntry m = zin.getNextEntry();
            byte[] manBytes = zin.readNBytes((int) m.getSize());
            JsonNode man = new ObjectMapper().readTree(manBytes);
            assertThat(man.get("selectorType").asText())
                    .isEqualTo(RuntimeTraceRegressionSuiteExportService.SELECTOR_CONVERSATION_SCOPED_SUITE);
            assertThat(man.get("scope").get("conversationId").asText()).isEqualTo(cid.toString());
        }
    }

    private static RuntimeTraceRegressionSuiteResult minimalCompleted() {
        var row =
                new RuntimeTraceRegressionSuiteBatchReturnedEntryResult(
                        0,
                        RuntimeTraceRegressionSuiteEntryKind.BY_TRACE_IDS,
                        "e",
                        RuntimeTraceReplayComparisonBatchOutcome.COMPLETED_ALL_EXACT_MATCH,
                        0,
                        0,
                        0);
        var summary = new RuntimeTraceRegressionSuiteSummary(1, 1, 1, 0, 0);
        return new RuntimeTraceRegressionSuiteResult(
                RuntimeTraceRegressionSuiteOutcome.COMPLETED_ALL_BATCH_RETURNS, summary, List.of(row));
    }
}
