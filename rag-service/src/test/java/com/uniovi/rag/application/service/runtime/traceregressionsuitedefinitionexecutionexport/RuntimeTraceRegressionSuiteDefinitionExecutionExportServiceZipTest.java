package com.uniovi.rag.application.service.runtime.traceregressionsuitedefinitionexecutionexport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.uniovi.rag.application.service.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteService;
import com.uniovi.rag.application.service.runtime.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionService;
import com.uniovi.rag.domain.runtime.tracecomparisonbatch.RuntimeTraceReplayComparisonBatchOutcome;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteBatchReturnedEntryResult;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteEntry;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteEntryKind;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteOutcome;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteRequest;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteResult;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteSummary;
import com.uniovi.rag.infrastructure.zip.ZipExpansionBudget;
import com.uniovi.rag.infrastructure.zip.ZipIoGuards;
import com.uniovi.rag.interfaces.rest.dto.traceregressionsuite.RuntimeTraceRegressionSuiteResponseDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuntimeTraceRegressionSuiteDefinitionExecutionExportServiceZipTest {

    @Mock
    private RuntimeTraceRegressionSuiteDefinitionService definitionService;

    @Mock
    private RuntimeTraceRegressionSuiteService suiteService;

    @Test
    void zip_contains_manifest_then_suite_json_matching_fromResult() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID definitionId = UUID.randomUUID();
        RuntimeTraceRegressionSuiteRequest req =
                new RuntimeTraceRegressionSuiteRequest(
                        userId, List.of(new RuntimeTraceRegressionSuiteEntry.ByTraceIds(List.of(UUID.randomUUID()))));
        RuntimeTraceRegressionSuiteResult mockResult = minimalCompleted();
        when(definitionService.materializeToSuiteRequest(eq(definitionId), eq(userId))).thenReturn(req);
        when(suiteService.execute(req)).thenReturn(mockResult);

        var svc = new RuntimeTraceRegressionSuiteDefinitionExecutionExportService(definitionService, suiteService);
        RuntimeTraceRegressionSuiteDefinitionExecutionExportArtifact art = svc.exportByDefinitionId(definitionId, userId);

        ObjectMapper om =
                new ObjectMapper()
                        .registerModule(new JavaTimeModule())
                        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                        .disable(SerializationFeature.INDENT_OUTPUT)
                        .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
        JsonNode expectedSuiteTree =
                om.readTree(om.writeValueAsBytes(RuntimeTraceRegressionSuiteResponseDto.fromResult(mockResult)));

        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(art.content()))) {
            ZipExpansionBudget budget = ZipExpansionBudget.forUploadedZip(2097152L);
            ZipEntry e1 = zin.getNextEntry();
            assertThat(e1.getName()).isEqualTo("manifest.json");
            byte[] manBytes = ZipIoGuards.readStoredEntryBytes(zin, e1, 2097152L, budget);
            JsonNode man = om.readTree(manBytes);
            assertThat(man.get("exportKind").asText())
                    .isEqualTo(RuntimeTraceRegressionSuiteDefinitionExecutionExportService.EXPORT_KIND);
            assertThat(man.get("truncated").asBoolean()).isFalse();
            assertThat(man.get("zipSizeBytes").asLong()).isEqualTo(art.content().length);

            ZipEntry e2 = zin.getNextEntry();
            assertThat(e2.getName()).isEqualTo("suite.json");
            byte[] suiteBytes = ZipIoGuards.readStoredEntryBytes(zin, e2, 2097152L, budget);
            JsonNode suiteTree = om.readTree(suiteBytes);
            assertThat(suiteTree).isEqualTo(expectedSuiteTree);
            assertThat(zin.getNextEntry()).isNull();
        }
    }

    private static RuntimeTraceRegressionSuiteResult minimalCompleted() {
        var row =
                new RuntimeTraceRegressionSuiteBatchReturnedEntryResult(
                        0,
                        RuntimeTraceRegressionSuiteEntryKind.BY_TRACE_IDS,
                        "echo",
                        RuntimeTraceReplayComparisonBatchOutcome.COMPLETED_ALL_EXACT_MATCH,
                        1,
                        1,
                        1);
        return new RuntimeTraceRegressionSuiteResult(
                RuntimeTraceRegressionSuiteOutcome.COMPLETED_ALL_BATCH_RETURNS,
                new RuntimeTraceRegressionSuiteSummary(1, 1, 1, 0, 0),
                List.of(row));
    }
}
