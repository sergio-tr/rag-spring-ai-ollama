package com.uniovi.rag.application.service.runtime.tracecomparisonexport;

import com.uniovi.rag.application.service.runtime.tracecomparison.RuntimeTraceReplayComparisonService;
import com.uniovi.rag.domain.runtime.tracecomparison.RuntimeTraceReplayAnswerComparisonStatus;
import com.uniovi.rag.domain.runtime.tracecomparison.RuntimeTraceReplayComparisonOutcome;
import com.uniovi.rag.domain.runtime.tracecomparison.RuntimeTraceReplayComparisonReplayEcho;
import com.uniovi.rag.domain.runtime.tracecomparison.RuntimeTraceReplayComparisonRequest;
import com.uniovi.rag.domain.runtime.tracecomparison.RuntimeTraceReplayComparisonResult;
import com.uniovi.rag.domain.runtime.tracereplay.RuntimeTraceReplayMode;
import com.uniovi.rag.domain.runtime.tracereplay.RuntimeTraceReplayOutcome;
import com.uniovi.rag.interfaces.rest.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.util.List;
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
class RuntimeTraceReplayComparisonExportServiceTest {

    @Mock
    private RuntimeTraceReplayComparisonService comparisonService;

    private UUID userId;
    private UUID traceId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        traceId = UUID.randomUUID();
    }

    @Test
    void not_found_outcome_throws_not_found() {
        when(comparisonService.compare(any(RuntimeTraceReplayComparisonRequest.class)))
                .thenReturn(notFoundResult());
        RuntimeTraceReplayComparisonExportService svc =
                new RuntimeTraceReplayComparisonExportService(comparisonService);
        assertThatThrownBy(() -> svc.exportByTraceId(userId, traceId)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void zip_has_exactly_manifest_and_comparison_entries_in_order() throws Exception {
        when(comparisonService.compare(any(RuntimeTraceReplayComparisonRequest.class)))
                .thenReturn(sampleOkResult());
        RuntimeTraceReplayComparisonExportService svc =
                new RuntimeTraceReplayComparisonExportService(comparisonService);
        RuntimeTraceReplayComparisonExportArtifact artifact = svc.exportByTraceId(userId, traceId);
        verify(comparisonService).compare(any(RuntimeTraceReplayComparisonRequest.class));
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(artifact.content()))) {
            ZipEntry e1 = zis.getNextEntry();
            assertThat(e1.getName()).isEqualTo("manifest.json");
            zis.closeEntry();
            ZipEntry e2 = zis.getNextEntry();
            assertThat(e2.getName()).isEqualTo("comparison.json");
            zis.closeEntry();
            assertThat(zis.getNextEntry()).isNull();
        }
    }

    @Test
    void zip_over_cap_throws() {
        when(comparisonService.compare(any(RuntimeTraceReplayComparisonRequest.class)))
                .thenReturn(sampleOkResult());
        RuntimeTraceReplayComparisonExportService svc =
                new RuntimeTraceReplayComparisonExportService(comparisonService, 32L);
        assertThatThrownBy(() -> svc.exportByTraceId(userId, traceId))
                .isInstanceOf(RuntimeTraceReplayComparisonExportSizeExceededException.class);
    }

    private RuntimeTraceReplayComparisonResult sampleOkResult() {
        UUID tid = UUID.randomUUID();
        return new RuntimeTraceReplayComparisonResult(
                userId,
                UUID.randomUUID(),
                tid,
                UUID.randomUUID(),
                UUID.randomUUID(),
                RuntimeTraceReplayMode.BY_TRACE_ID,
                new RuntimeTraceReplayComparisonReplayEcho(Optional.of(tid), Optional.empty(), Optional.empty()),
                RuntimeTraceReplayComparisonOutcome.COMPARISON_SUCCEEDED_EXACT_MATCH,
                RuntimeTraceReplayOutcome.REPLAY_SUCCEEDED,
                RuntimeTraceReplayAnswerComparisonStatus.NOT_COMPARABLE_ORIGINAL_ABSENT,
                true,
                "ok",
                List.of(),
                "DIRECT_WORKFLOW_ROUTE",
                "DIRECT_WORKFLOW_ROUTE",
                "DirectLlmWorkflow",
                "DirectLlmWorkflow");
    }

    private static RuntimeTraceReplayComparisonResult notFoundResult() {
        return new RuntimeTraceReplayComparisonResult(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                null,
                RuntimeTraceReplayMode.BY_TRACE_ID,
                new RuntimeTraceReplayComparisonReplayEcho(Optional.empty(), Optional.empty(), Optional.empty()),
                RuntimeTraceReplayComparisonOutcome.ORIGINAL_TRACE_NOT_FOUND_OR_INACCESSIBLE,
                RuntimeTraceReplayOutcome.NOT_ATTEMPTED,
                RuntimeTraceReplayAnswerComparisonStatus.NOT_COMPARABLE_ORIGINAL_ABSENT,
                false,
                "nf",
                List.of(),
                "",
                "",
                "",
                "");
    }
}
