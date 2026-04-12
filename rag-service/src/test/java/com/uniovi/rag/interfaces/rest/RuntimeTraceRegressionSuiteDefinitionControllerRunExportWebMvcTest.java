package com.uniovi.rag.interfaces.rest;

import com.uniovi.rag.application.service.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteService;
import com.uniovi.rag.application.service.runtime.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionService;
import com.uniovi.rag.application.service.runtime.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunPersistenceService;
import com.uniovi.rag.application.service.runtime.traceregressionsuiterunexport.RuntimeTraceRegressionSuiteRunExportArtifact;
import com.uniovi.rag.application.service.runtime.traceregressionsuiterunexport.RuntimeTraceRegressionSuiteRunExportService;
import com.uniovi.rag.application.service.runtime.traceregressionsuiterunimport.RuntimeTraceRegressionSuiteRunImportService;
import com.uniovi.rag.application.service.runtime.traceregressionsuiterunexport.RuntimeTraceRegressionSuiteRunExportSizeExceededException;
import com.uniovi.rag.configuration.RegressionSuiteDefinitionMutationJacksonConfiguration;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteOutcome;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteSummary;
import com.uniovi.rag.domain.runtime.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionEntrySnapshot;
import com.uniovi.rag.domain.runtime.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionSnapshot;
import com.uniovi.rag.domain.runtime.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunId;
import com.uniovi.rag.domain.runtime.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunSnapshot;
import com.uniovi.rag.domain.runtime.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunSourceType;
import com.uniovi.rag.domain.runtime.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunSummary;
import com.uniovi.rag.security.RagPrincipal;
import com.uniovi.rag.testsupport.webmvc.RagWebMvcTestApplication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = RuntimeTraceRegressionSuiteDefinitionController.class)
@ContextConfiguration(classes = RagWebMvcTestApplication.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({RuntimeTraceRegressionSuiteDefinitionController.class, RegressionSuiteDefinitionMutationJacksonConfiguration.class})
@ActiveProfiles("test")
@TestPropertySource(properties = "rag.api.product-base-path=/api/v1")
class RuntimeTraceRegressionSuiteDefinitionControllerRunExportWebMvcTest {

    private static final String PRODUCT_BASE = "/api/v1";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RuntimeTraceRegressionSuiteDefinitionService definitionService;

    @MockitoBean
    private RuntimeTraceRegressionSuiteService suiteService;

    @MockitoBean
    private RuntimeTraceRegressionSuiteRunPersistenceService runPersistenceService;

    @MockitoBean
    private RuntimeTraceRegressionSuiteRunExportService runExportService;

    @MockitoBean
    private RuntimeTraceRegressionSuiteRunImportService runImportService;

    private UUID userId;
    private UUID definitionId;
    private UUID runId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        definitionId = UUID.randomUUID();
        runId = UUID.randomUUID();
        RagPrincipal principal = new RagPrincipal(userId, "u@test", "USER");
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(
                        principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    private static RuntimeTraceRegressionSuiteDefinitionSnapshot minimalDefinitionSnapshot(UUID defId) {
        UUID tid = UUID.randomUUID();
        return new RuntimeTraceRegressionSuiteDefinitionSnapshot(
                defId,
                "suite",
                null,
                1,
                Instant.parse("2024-03-01T12:00:00Z"),
                Instant.parse("2024-03-02T12:00:00Z"),
                List.of(new RuntimeTraceRegressionSuiteDefinitionEntrySnapshot.ByTraceIds(List.of(tid))));
    }

    private void assertNoZipDownloadResponse(MvcResult result) {
        assertThat(result.getResponse().getContentAsByteArray().length).isZero();
        assertThat(result.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION)).isNull();
        String ct = result.getResponse().getContentType();
        assertThat(ct == null || !"application/zip".equals(ct) || ct.startsWith("application/zip;"))
                .isTrue();
    }

    @Test
    void p53_t1_export_200_zipHeaders_verifyExportOnce_neverPersistenceFromController() throws Exception {
        when(definitionService.loadByIdForUser(definitionId, userId))
                .thenReturn(Optional.of(minimalDefinitionSnapshot(definitionId)));
        byte[] body = new byte[] {10, 20, 30};
        String filename = "runtime-trace-regression-suite-definition-run_" + definitionId + "_" + runId + ".zip";
        RuntimeTraceRegressionSuiteRunExportArtifact artifact =
                new RuntimeTraceRegressionSuiteRunExportArtifact(
                        filename, RuntimeTraceRegressionSuiteRunExportArtifact.MEDIA_TYPE_ZIP, body, body.length);
        when(runExportService.exportRunZipForDefinition(eq(runId), eq(userId), eq(definitionId))).thenReturn(artifact);

        MvcResult result =
                mockMvc.perform(
                                get(
                                        PRODUCT_BASE
                                                + "/runtime-trace-regression-suite-definitions/{defId}/runs/{rid}/export",
                                        definitionId,
                                        runId))
                        .andExpect(status().isOk())
                        .andReturn();

        assertThat(result.getResponse().getContentAsByteArray()).isEqualTo(body);
        assertThat(result.getResponse().getHeader(HttpHeaders.CONTENT_TYPE)).isEqualTo("application/zip");
        assertThat(result.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION))
                .isEqualTo("attachment; filename=\"" + filename + "\"");
        assertThat(result.getResponse().getHeader(HttpHeaders.CONTENT_LENGTH)).isEqualTo(Long.toString(body.length));
        verify(runExportService, times(1)).exportRunZipForDefinition(eq(runId), eq(userId), eq(definitionId));
        verify(runPersistenceService, never()).loadByIdForUser(any(), any());
        verify(runPersistenceService, never()).loadByIdForUserAndDefinition(any(), any(), any());
    }

    @Test
    void p53_t2_queryString_400_neverGateOrExport() throws Exception {
        MvcResult result =
                mockMvc.perform(
                                get(
                                                PRODUCT_BASE
                                                        + "/runtime-trace-regression-suite-definitions/{defId}/runs/{rid}/export",
                                                definitionId,
                                                runId)
                                        .queryParam("x", "1"))
                        .andExpect(status().isBadRequest())
                        .andReturn();
        assertNoZipDownloadResponse(result);
        verify(runExportService, never()).exportRunZipForDefinition(any(), any(), any());
        verify(definitionService, never()).loadByIdForUser(any(), any());
    }

    @Test
    void p53_t3_invalid_definitionId_400() throws Exception {
        MvcResult result =
                mockMvc.perform(
                                get(
                                        PRODUCT_BASE
                                                + "/runtime-trace-regression-suite-definitions/{defId}/runs/{rid}/export",
                                        "not-a-uuid",
                                        runId))
                        .andExpect(status().isBadRequest())
                        .andReturn();
        assertNoZipDownloadResponse(result);
        verify(runExportService, never()).exportRunZipForDefinition(any(), any(), any());
        verify(definitionService, never()).loadByIdForUser(any(), any());
    }

    @Test
    void p53_t4_invalid_runId_400() throws Exception {
        MvcResult result =
                mockMvc.perform(
                                get(
                                        PRODUCT_BASE
                                                + "/runtime-trace-regression-suite-definitions/{defId}/runs/{rid}/export",
                                        definitionId,
                                        "not-a-uuid"))
                        .andExpect(status().isBadRequest())
                        .andReturn();
        assertNoZipDownloadResponse(result);
        verify(runExportService, never()).exportRunZipForDefinition(any(), any(), any());
        verify(definitionService, never()).loadByIdForUser(any(), any());
    }

    @Test
    void p53_t5_gate_empty_404_definitionNotFound_neverExport() throws Exception {
        when(definitionService.loadByIdForUser(definitionId, userId)).thenReturn(Optional.empty());
        MvcResult result =
                mockMvc.perform(
                                get(
                                        PRODUCT_BASE
                                                + "/runtime-trace-regression-suite-definitions/{defId}/runs/{rid}/export",
                                        definitionId,
                                        runId))
                        .andExpect(status().isNotFound())
                        .andExpect(
                                r ->
                                        assertThat(r.getResolvedException())
                                                .isInstanceOf(NotFoundException.class)
                                                .hasMessage("definition not found"))
                        .andReturn();
        assertThat(result.getResponse().getContentAsByteArray().length).isZero();
        verify(runExportService, never()).exportRunZipForDefinition(any(), any(), any());
    }

    @Test
    void p53_t6_gate_ok_export_throwsNotFound_run_404() throws Exception {
        when(definitionService.loadByIdForUser(definitionId, userId))
                .thenReturn(Optional.of(minimalDefinitionSnapshot(definitionId)));
        when(runExportService.exportRunZipForDefinition(eq(runId), eq(userId), eq(definitionId)))
                .thenThrow(new NotFoundException("run not found"));

        MvcResult result =
                mockMvc.perform(
                                get(
                                        PRODUCT_BASE
                                                + "/runtime-trace-regression-suite-definitions/{defId}/runs/{rid}/export",
                                        definitionId,
                                        runId))
                        .andExpect(status().isNotFound())
                        .andReturn();

        assertNoZipDownloadResponse(result);
        verify(definitionService, times(1)).loadByIdForUser(eq(definitionId), eq(userId));
        verify(runExportService, times(1)).exportRunZipForDefinition(eq(runId), eq(userId), eq(definitionId));
    }

    @Test
    void p53_t7_export_throwsSizeExceeded_413() throws Exception {
        when(definitionService.loadByIdForUser(definitionId, userId))
                .thenReturn(Optional.of(minimalDefinitionSnapshot(definitionId)));
        when(runExportService.exportRunZipForDefinition(eq(runId), eq(userId), eq(definitionId)))
                .thenThrow(new RuntimeTraceRegressionSuiteRunExportSizeExceededException("run export exceeds max ZIP size"));

        MvcResult result =
                mockMvc.perform(
                                get(
                                        PRODUCT_BASE
                                                + "/runtime-trace-regression-suite-definitions/{defId}/runs/{rid}/export",
                                        definitionId,
                                        runId))
                        .andExpect(status().isPayloadTooLarge())
                        .andReturn();
        assertNoZipDownloadResponse(result);
    }

    @Test
    void p53_t8_t9_never_execute_create_globalLoad_or_deletes() throws Exception {
        when(definitionService.loadByIdForUser(definitionId, userId))
                .thenReturn(Optional.of(minimalDefinitionSnapshot(definitionId)));
        byte[] body = new byte[] {1};
        when(runExportService.exportRunZipForDefinition(eq(runId), eq(userId), eq(definitionId)))
                .thenReturn(
                        new RuntimeTraceRegressionSuiteRunExportArtifact(
                                "runtime-trace-regression-suite-definition-run_" + definitionId + "_" + runId + ".zip",
                                RuntimeTraceRegressionSuiteRunExportArtifact.MEDIA_TYPE_ZIP,
                                body,
                                body.length));

        mockMvc.perform(
                        get(
                                PRODUCT_BASE
                                        + "/runtime-trace-regression-suite-definitions/{defId}/runs/{rid}/export",
                                definitionId,
                                runId))
                .andExpect(status().isOk());

        verify(suiteService, never()).execute(any());
        verify(runPersistenceService, never()).createRun(any(), any(), any(), any());
        verify(runPersistenceService, never()).loadByIdForUser(any(), any());
        verify(runPersistenceService, never()).deleteRunForUser(any(), any());
        verify(runPersistenceService, never()).deleteRunForUserAndDefinition(any(), any(), any());
    }

    @Test
    void p53_t10_list_compat_p50() throws Exception {
        when(definitionService.loadByIdForUser(definitionId, userId))
                .thenReturn(Optional.of(minimalDefinitionSnapshot(definitionId)));
        when(runPersistenceService.listSummariesForUserAndDefinition(userId, definitionId)).thenReturn(List.of());

        mockMvc.perform(get(PRODUCT_BASE + "/runtime-trace-regression-suite-definitions/{id}/runs", definitionId))
                .andExpect(status().isOk());

        verify(runPersistenceService, times(1)).listSummariesForUserAndDefinition(eq(userId), eq(definitionId));
    }

    @Test
    void p53_t11_detail_compat_p50() throws Exception {
        when(definitionService.loadByIdForUser(definitionId, userId))
                .thenReturn(Optional.of(minimalDefinitionSnapshot(definitionId)));
        RuntimeTraceRegressionSuiteRunSnapshot snap =
                new RuntimeTraceRegressionSuiteRunSnapshot(
                        new RuntimeTraceRegressionSuiteRunId(runId),
                        userId,
                        RuntimeTraceRegressionSuiteRunSourceType.SAVED_DEFINITION,
                        Optional.of(definitionId),
                        RuntimeTraceRegressionSuiteOutcome.COMPLETED_ALL_BATCH_RETURNS,
                        new RuntimeTraceRegressionSuiteSummary(0, 0, 0, 0, 0),
                        Instant.parse("2024-03-01T12:00:00Z"),
                        List.of());
        when(runPersistenceService.loadByIdForUserAndDefinition(runId, userId, definitionId))
                .thenReturn(Optional.of(snap));

        mockMvc.perform(
                        get(
                                PRODUCT_BASE
                                        + "/runtime-trace-regression-suite-definitions/{defId}/runs/{rid}",
                                definitionId,
                                runId))
                .andExpect(status().isOk());

        verify(runPersistenceService, times(1)).loadByIdForUserAndDefinition(eq(runId), eq(userId), eq(definitionId));
    }

    @Test
    void p53_t12_export_soleGetMapping_forExportPath() {
        long exportGets =
                Arrays.stream(RuntimeTraceRegressionSuiteDefinitionController.class.getDeclaredMethods())
                        .filter(
                                m -> {
                                    GetMapping gm = m.getAnnotation(GetMapping.class);
                                    if (gm == null) {
                                        return false;
                                    }
                                    String[] paths = gm.value().length > 0 ? gm.value() : gm.path();
                                    for (String p : paths) {
                                        if ("/runtime-trace-regression-suite-definitions/{definitionId}/runs/{runId}/export"
                                                .equals(p)) {
                                            return true;
                                        }
                                    }
                                    return false;
                                })
                        .count();
        assertThat(exportGets).isEqualTo(1);
    }
}
