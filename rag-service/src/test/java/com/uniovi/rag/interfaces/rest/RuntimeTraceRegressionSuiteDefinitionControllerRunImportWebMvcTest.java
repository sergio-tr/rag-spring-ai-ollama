package com.uniovi.rag.interfaces.rest;

import com.uniovi.rag.application.service.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteService;
import com.uniovi.rag.application.service.runtime.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionService;
import com.uniovi.rag.application.service.runtime.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunPersistenceService;
import com.uniovi.rag.application.service.runtime.traceregressionsuiterunexport.RuntimeTraceRegressionSuiteRunExportService;
import com.uniovi.rag.application.service.runtime.traceregressionsuiterunimport.RuntimeTraceRegressionSuiteRunImportRejectedException;
import com.uniovi.rag.application.service.runtime.traceregressionsuiterunimport.RuntimeTraceRegressionSuiteRunImportService;
import com.uniovi.rag.application.service.runtime.traceregressionsuiterunimportpreview.RuntimeTraceRegressionSuiteRunImportPreviewService;
import com.uniovi.rag.configuration.RegressionSuiteDefinitionMutationJacksonConfiguration;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteOutcome;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteSummary;
import com.uniovi.rag.domain.runtime.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionEntrySnapshot;
import com.uniovi.rag.domain.runtime.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionSnapshot;
import com.uniovi.rag.domain.runtime.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunId;
import com.uniovi.rag.domain.runtime.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunSnapshot;
import com.uniovi.rag.domain.runtime.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunSourceType;
import com.uniovi.rag.security.RagPrincipal;
import com.uniovi.rag.testsupport.RagApiTestPaths;
import com.uniovi.rag.testsupport.webmvc.RagWebMvcTestApplication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = RuntimeTraceRegressionSuiteDefinitionController.class)
@ContextConfiguration(classes = RagWebMvcTestApplication.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({RuntimeTraceRegressionSuiteDefinitionController.class, RegressionSuiteDefinitionMutationJacksonConfiguration.class})
@ActiveProfiles("test")
@TestPropertySource(properties = "rag.api.product-base-path=/api/v1")
class RuntimeTraceRegressionSuiteDefinitionControllerRunImportWebMvcTest {

    @Autowired
    private Environment environment;

    @Autowired
    private MockMvc mockMvc;

    private String productBase() {
        return RagApiTestPaths.productBasePath(environment);
    }

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

    @MockitoBean
    private RuntimeTraceRegressionSuiteRunImportPreviewService runImportPreviewService;

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

    @Test
    void p54_t1_queryString_400_neverGateOrImport() throws Exception {
        MvcResult result =
                mockMvc.perform(
                                post(
                                                productBase()
                                                        + "/runtime-trace-regression-suite-definitions/{defId}/runs/import",
                                                definitionId)
                                        .queryParam("x", "1")
                                        .contentType("application/zip")
                                        .content(new byte[] {1}))
                        .andExpect(status().isBadRequest())
                        .andReturn();
        assertThat(result.getResponse().getContentAsByteArray().length).isZero();

        verify(definitionService, never()).loadByIdForUser(any(), any());
        verify(runImportService, never()).importRunZipForDefinition(any(), any(), any());
        verify(suiteService, never()).execute(any());
    }

    @Test
    void p54_t2_invalid_definitionId_400_neverGateOrImport() throws Exception {
        MvcResult result =
                mockMvc.perform(
                                post(
                                                productBase()
                                                        + "/runtime-trace-regression-suite-definitions/{defId}/runs/import",
                                                "not-a-uuid")
                                        .contentType("application/zip")
                                        .content(new byte[] {1}))
                        .andExpect(status().isBadRequest())
                        .andReturn();
        assertThat(result.getResponse().getContentAsByteArray().length).isZero();

        verify(definitionService, never()).loadByIdForUser(any(), any());
        verify(runImportService, never()).importRunZipForDefinition(any(), any(), any());
        verify(suiteService, never()).execute(any());
    }

    @Test
    void p54_t3_gate_empty_404_neverImport() throws Exception {
        when(definitionService.loadByIdForUser(definitionId, userId)).thenReturn(Optional.empty());
        MvcResult result =
                mockMvc.perform(
                                post(
                                                productBase()
                                                        + "/runtime-trace-regression-suite-definitions/{defId}/runs/import",
                                                definitionId)
                                        .contentType("application/zip")
                                        .content(new byte[] {1}))
                        .andExpect(status().isNotFound())
                        .andReturn();
        assertThat(result.getResponse().getContentAsByteArray().length).isZero();

        verify(runImportService, never()).importRunZipForDefinition(any(), any(), any());
        verify(suiteService, never()).execute(any());
    }

    @Test
    void p54_t3b_gate_empty_404_even_if_content_type_invalid_neverImport() throws Exception {
        when(definitionService.loadByIdForUser(definitionId, userId)).thenReturn(Optional.empty());
        MvcResult result =
                mockMvc.perform(
                                post(
                                                productBase()
                                                        + "/runtime-trace-regression-suite-definitions/{defId}/runs/import",
                                                definitionId)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{}"))
                        .andExpect(status().isNotFound())
                        .andReturn();
        assertThat(result.getResponse().getContentAsByteArray().length).isZero();

        verify(runImportService, never()).importRunZipForDefinition(any(), any(), any());
        verify(suiteService, never()).execute(any());
    }

    @Test
    void p54_t4_gate_ok_201_location_verifyImportOnce_neverPersistenceFromController() throws Exception {
        when(definitionService.loadByIdForUser(definitionId, userId))
                .thenReturn(Optional.of(minimalDefinitionSnapshot(definitionId)));
        UUID createdId = UUID.randomUUID();
        byte[] zip = new byte[] {9, 9, 9};
        when(runImportService.importRunZipForDefinition(any(byte[].class), eq(userId), eq(definitionId)))
                .thenReturn(createdId);

        MvcResult result =
                mockMvc.perform(
                                post(
                                                productBase()
                                                        + "/runtime-trace-regression-suite-definitions/{defId}/runs/import",
                                                definitionId)
                                        .contentType("application/zip")
                                        .content(zip))
                        .andExpect(status().isCreated())
                        .andReturn();

        assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION))
                .isEqualTo(productBase() + "/runtime-trace-regression-suite-runs/" + createdId);
        verify(runImportService, times(1)).importRunZipForDefinition(any(byte[].class), eq(userId), eq(definitionId));
        verify(runPersistenceService, never()).createRun(any(), any(), any(), any());
        verify(runPersistenceService, never()).loadByIdForUser(any(), any());
        verify(runPersistenceService, never()).listSummariesForUserAndDefinition(any(), any());
        verify(runPersistenceService, never()).loadByIdForUserAndDefinition(any(), any(), any());
        verify(suiteService, never()).execute(any());
    }

    @Test
    void p54_t5a_gate_ok_wrong_content_type_400_neverImport() throws Exception {
        when(definitionService.loadByIdForUser(definitionId, userId))
                .thenReturn(Optional.of(minimalDefinitionSnapshot(definitionId)));
        MvcResult result =
                mockMvc.perform(
                                post(
                                                productBase()
                                                        + "/runtime-trace-regression-suite-definitions/{defId}/runs/import",
                                                definitionId)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{}"))
                        .andExpect(status().isBadRequest())
                        .andReturn();
        assertThat(result.getResponse().getContentAsByteArray().length).isZero();

        verify(runImportService, never()).importRunZipForDefinition(any(), any(), any());
        verify(suiteService, never()).execute(any());
    }

    @Test
    void p54_t5b_gate_ok_empty_body_400_neverImport() throws Exception {
        when(definitionService.loadByIdForUser(definitionId, userId))
                .thenReturn(Optional.of(minimalDefinitionSnapshot(definitionId)));
        MvcResult result =
                mockMvc.perform(
                                post(
                                                productBase()
                                                        + "/runtime-trace-regression-suite-definitions/{defId}/runs/import",
                                                definitionId)
                                        .contentType("application/zip")
                                        .content(new byte[0]))
                        .andExpect(status().isBadRequest())
                        .andReturn();
        assertThat(result.getResponse().getContentAsByteArray().length).isZero();

        verify(runImportService, never()).importRunZipForDefinition(any(), any(), any());
        verify(suiteService, never()).execute(any());
    }

    @Test
    void p54_t5c_gate_ok_oversize_body_400_neverImport() throws Exception {
        when(definitionService.loadByIdForUser(definitionId, userId))
                .thenReturn(Optional.of(minimalDefinitionSnapshot(definitionId)));
        byte[] huge = new byte[(int) RuntimeTraceRegressionSuiteRunImportService.MAX_IMPORT_ZIP_BYTES + 1];
        MvcResult result =
                mockMvc.perform(
                                post(
                                                productBase()
                                                        + "/runtime-trace-regression-suite-definitions/{defId}/runs/import",
                                                definitionId)
                                        .contentType("application/zip")
                                        .content(huge))
                        .andExpect(status().isBadRequest())
                        .andReturn();
        assertThat(result.getResponse().getContentAsByteArray().length).isZero();

        verify(runImportService, never()).importRunZipForDefinition(any(), any(), any());
        verify(suiteService, never()).execute(any());
    }

    @Test
    void p54_t6_gate_ok_import_rejected_400() throws Exception {
        when(definitionService.loadByIdForUser(definitionId, userId))
                .thenReturn(Optional.of(minimalDefinitionSnapshot(definitionId)));
        when(runImportService.importRunZipForDefinition(any(byte[].class), eq(userId), eq(definitionId)))
                .thenThrow(new RuntimeTraceRegressionSuiteRunImportRejectedException("invalid manifest"));

        MvcResult result =
                mockMvc.perform(
                                post(
                                                productBase()
                                                        + "/runtime-trace-regression-suite-definitions/{defId}/runs/import",
                                                definitionId)
                                        .contentType("application/zip")
                                        .content(new byte[] {1, 2}))
                        .andExpect(status().isBadRequest())
                        .andReturn();
        assertThat(result.getResponse().getContentAsByteArray().length).isZero();

        verify(runImportService, times(1)).importRunZipForDefinition(any(byte[].class), eq(userId), eq(definitionId));
        verify(suiteService, never()).execute(any());
    }

    @Test
    void p54_t7_gate_ok_illegal_argument_400() throws Exception {
        when(definitionService.loadByIdForUser(definitionId, userId))
                .thenReturn(Optional.of(minimalDefinitionSnapshot(definitionId)));
        when(runImportService.importRunZipForDefinition(any(byte[].class), eq(userId), eq(definitionId)))
                .thenThrow(new IllegalArgumentException("bad"));

        MvcResult result =
                mockMvc.perform(
                                post(
                                                productBase()
                                                        + "/runtime-trace-regression-suite-definitions/{defId}/runs/import",
                                                definitionId)
                                        .contentType("application/zip")
                                        .content(new byte[] {1, 2}))
                        .andExpect(status().isBadRequest())
                        .andReturn();
        assertThat(result.getResponse().getContentAsByteArray().length).isZero();

        verify(runImportService, times(1)).importRunZipForDefinition(any(byte[].class), eq(userId), eq(definitionId));
        verify(suiteService, never()).execute(any());
    }

    @Test
    void p54_t8_get_list_compat_p50() throws Exception {
        when(definitionService.loadByIdForUser(definitionId, userId))
                .thenReturn(Optional.of(minimalDefinitionSnapshot(definitionId)));
        when(runPersistenceService.listSummariesForUserAndDefinition(userId, definitionId)).thenReturn(List.of());

        mockMvc.perform(get(productBase() + "/runtime-trace-regression-suite-definitions/{id}/runs", definitionId))
                .andExpect(status().isOk());

        verify(runPersistenceService, times(1)).listSummariesForUserAndDefinition(eq(userId), eq(definitionId));
    }

    @Test
    void p54_t9_get_detail_compat_p50() throws Exception {
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
                                productBase() + "/runtime-trace-regression-suite-definitions/{defId}/runs/{rid}",
                                definitionId,
                                runId))
                .andExpect(status().isOk());

        verify(runPersistenceService, times(1)).loadByIdForUserAndDefinition(eq(runId), eq(userId), eq(definitionId));
    }
}
