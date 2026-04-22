package com.uniovi.rag.interfaces.rest;

import com.uniovi.rag.application.service.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteService;
import com.uniovi.rag.application.service.runtime.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionService;
import com.uniovi.rag.application.service.runtime.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunPersistenceService;
import com.uniovi.rag.application.service.runtime.traceregressionsuiterunexport.RuntimeTraceRegressionSuiteRunExportService;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.DeleteMapping;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = RuntimeTraceRegressionSuiteDefinitionController.class)
@ContextConfiguration(classes = RagWebMvcTestApplication.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({RuntimeTraceRegressionSuiteDefinitionController.class, RegressionSuiteDefinitionMutationJacksonConfiguration.class})
@ActiveProfiles("test")
@TestPropertySource(properties = "rag.api.product-base-path=/api/v1")
class RuntimeTraceRegressionSuiteDefinitionControllerRunDeletionWebMvcTest {

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

    private void verifyP52T7T8NeverMutateOrGlobalDelete() {
        verify(runPersistenceService, never()).deleteRunForUser(any(), any());
        verify(suiteService, never()).execute(any());
        verify(runPersistenceService, never()).createRun(any(), any(), any(), any());
    }

    @Test
    void p52_t1_delete_true_returns204_emptyBody_noLocationHeaders_verifyDeleteOnce() throws Exception {
        when(definitionService.loadByIdForUser(definitionId, userId))
                .thenReturn(Optional.of(minimalDefinitionSnapshot(definitionId)));
        when(runPersistenceService.deleteRunForUserAndDefinition(eq(runId), eq(userId), eq(definitionId)))
                .thenReturn(true);

        MvcResult result =
                mockMvc.perform(
                                delete(
                                        productBase()
                                                + "/runtime-trace-regression-suite-definitions/{defId}/runs/{rid}",
                                        definitionId,
                                        runId))
                        .andExpect(status().isNoContent())
                        .andReturn();

        assertThat(result.getResponse().getContentAsByteArray().length).isZero();
        assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION)).isNull();
        assertThat(result.getResponse().getHeader(HttpHeaders.CONTENT_LOCATION)).isNull();
        verify(runPersistenceService, times(1)).deleteRunForUserAndDefinition(eq(runId), eq(userId), eq(definitionId));
        verifyP52T7T8NeverMutateOrGlobalDelete();
    }

    @Test
    void p52_t2_delete_false_returns404_emptyBody_noLocationHeaders() throws Exception {
        when(definitionService.loadByIdForUser(definitionId, userId))
                .thenReturn(Optional.of(minimalDefinitionSnapshot(definitionId)));
        when(runPersistenceService.deleteRunForUserAndDefinition(eq(runId), eq(userId), eq(definitionId)))
                .thenReturn(false);

        MvcResult result =
                mockMvc.perform(
                                delete(
                                        productBase()
                                                + "/runtime-trace-regression-suite-definitions/{defId}/runs/{rid}",
                                        definitionId,
                                        runId))
                        .andExpect(status().isNotFound())
                        .andReturn();

        assertThat(result.getResponse().getContentAsByteArray().length).isZero();
        assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION)).isNull();
        assertThat(result.getResponse().getHeader(HttpHeaders.CONTENT_LOCATION)).isNull();
        verify(runPersistenceService, times(1)).deleteRunForUserAndDefinition(eq(runId), eq(userId), eq(definitionId));
        verifyP52T7T8NeverMutateOrGlobalDelete();
    }

    @Test
    void p52_t3_queryString_400_neverGateOrDelete() throws Exception {
        mockMvc.perform(
                        delete(
                                        productBase()
                                                + "/runtime-trace-regression-suite-definitions/{defId}/runs/{rid}",
                                        definitionId,
                                        runId)
                                .queryParam("x", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(""));

        verify(runPersistenceService, never()).deleteRunForUserAndDefinition(any(), any(), any());
        verify(definitionService, never()).loadByIdForUser(any(), any());
        verifyP52T7T8NeverMutateOrGlobalDelete();
    }

    @Test
    void p52_t4_invalid_definitionId_400_neverGateOrDelete() throws Exception {
        mockMvc.perform(
                        delete(
                                productBase()
                                        + "/runtime-trace-regression-suite-definitions/{defId}/runs/{rid}",
                                "not-a-uuid",
                                runId))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(""));

        verify(runPersistenceService, never()).deleteRunForUserAndDefinition(any(), any(), any());
        verify(definitionService, never()).loadByIdForUser(any(), any());
        verifyP52T7T8NeverMutateOrGlobalDelete();
    }

    @Test
    void p52_t4b_invalid_definitionId_valid_runId_still_400_neverGateOrDelete() throws Exception {
        mockMvc.perform(
                        delete(
                                productBase()
                                        + "/runtime-trace-regression-suite-definitions/{defId}/runs/{rid}",
                                "not-a-uuid",
                                runId))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(""));

        verify(runPersistenceService, never()).deleteRunForUserAndDefinition(any(), any(), any());
        verify(definitionService, never()).loadByIdForUser(any(), any());
        verifyP52T7T8NeverMutateOrGlobalDelete();
    }

    @Test
    void p52_t5_invalid_runId_400_neverGateOrDelete() throws Exception {
        mockMvc.perform(
                        delete(
                                productBase()
                                        + "/runtime-trace-regression-suite-definitions/{defId}/runs/{rid}",
                                definitionId,
                                "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(""));

        verify(runPersistenceService, never()).deleteRunForUserAndDefinition(any(), any(), any());
        verify(definitionService, never()).loadByIdForUser(any(), any());
        verifyP52T7T8NeverMutateOrGlobalDelete();
    }

    @Test
    void p52_t6_gate_empty_404_notFoundException_emptyBody_neverDelete() throws Exception {
        when(definitionService.loadByIdForUser(definitionId, userId)).thenReturn(Optional.empty());

        MvcResult result =
                mockMvc.perform(
                                delete(
                                        productBase()
                                                + "/runtime-trace-regression-suite-definitions/{defId}/runs/{rid}",
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
        verify(runPersistenceService, never()).deleteRunForUserAndDefinition(any(), any(), any());
        verifyP52T7T8NeverMutateOrGlobalDelete();
    }

    @Test
    void p52_t9_location_headers_null_across_statuses() throws Exception {
        when(definitionService.loadByIdForUser(definitionId, userId))
                .thenReturn(Optional.of(minimalDefinitionSnapshot(definitionId)));
        when(runPersistenceService.deleteRunForUserAndDefinition(eq(runId), eq(userId), eq(definitionId)))
                .thenReturn(true);
        MvcResult r204 =
                mockMvc.perform(
                                delete(
                                        productBase()
                                                + "/runtime-trace-regression-suite-definitions/{defId}/runs/{rid}",
                                        definitionId,
                                        runId))
                        .andExpect(status().isNoContent())
                        .andReturn();
        assertThat(r204.getResponse().getHeader(HttpHeaders.LOCATION)).isNull();
        assertThat(r204.getResponse().getHeader(HttpHeaders.CONTENT_LOCATION)).isNull();

        when(runPersistenceService.deleteRunForUserAndDefinition(eq(runId), eq(userId), eq(definitionId)))
                .thenReturn(false);
        MvcResult r404run =
                mockMvc.perform(
                                delete(
                                        productBase()
                                                + "/runtime-trace-regression-suite-definitions/{defId}/runs/{rid}",
                                        definitionId,
                                        runId))
                        .andExpect(status().isNotFound())
                        .andReturn();
        assertThat(r404run.getResponse().getHeader(HttpHeaders.LOCATION)).isNull();
        assertThat(r404run.getResponse().getHeader(HttpHeaders.CONTENT_LOCATION)).isNull();

        MvcResult r400qs =
                mockMvc.perform(
                                delete(
                                                productBase()
                                                        + "/runtime-trace-regression-suite-definitions/{defId}/runs/{rid}",
                                                definitionId,
                                                runId)
                                        .queryParam("x", "1"))
                        .andExpect(status().isBadRequest())
                        .andReturn();
        assertThat(r400qs.getResponse().getHeader(HttpHeaders.LOCATION)).isNull();
        assertThat(r400qs.getResponse().getHeader(HttpHeaders.CONTENT_LOCATION)).isNull();

        when(definitionService.loadByIdForUser(definitionId, userId)).thenReturn(Optional.empty());
        MvcResult r404def =
                mockMvc.perform(
                                delete(
                                        productBase()
                                                + "/runtime-trace-regression-suite-definitions/{defId}/runs/{rid}",
                                        definitionId,
                                        runId))
                        .andExpect(status().isNotFound())
                        .andReturn();
        assertThat(r404def.getResponse().getHeader(HttpHeaders.LOCATION)).isNull();
        assertThat(r404def.getResponse().getHeader(HttpHeaders.CONTENT_LOCATION)).isNull();
    }

    @Test
    void p52_t10_get_list_compat_verify_listSummariesForUserAndDefinitionOnce() throws Exception {
        when(definitionService.loadByIdForUser(definitionId, userId))
                .thenReturn(Optional.of(minimalDefinitionSnapshot(definitionId)));
        when(runPersistenceService.listSummariesForUserAndDefinition(userId, definitionId)).thenReturn(List.of());

        mockMvc.perform(get(productBase() + "/runtime-trace-regression-suite-definitions/{id}/runs", definitionId))
                .andExpect(status().isOk());

        verify(runPersistenceService, times(1)).listSummariesForUserAndDefinition(eq(userId), eq(definitionId));
    }

    @Test
    void p52_t11_get_detail_compat_verify_loadByIdForUserAndDefinitionOnce() throws Exception {
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
                                productBase()
                                        + "/runtime-trace-regression-suite-definitions/{defId}/runs/{rid}",
                                definitionId,
                                runId))
                .andExpect(status().isOk());

        verify(runPersistenceService, times(1)).loadByIdForUserAndDefinition(eq(runId), eq(userId), eq(definitionId));
    }

    @Test
    void p52_t12_deleteRunForDefinition_soleDeleteMapping_forRunsPath_p35_deleteUnchanged() {
        long runsPathDeletes =
                Arrays.stream(RuntimeTraceRegressionSuiteDefinitionController.class.getDeclaredMethods())
                        .filter(
                                m -> {
                                    DeleteMapping dm = m.getAnnotation(DeleteMapping.class);
                                    if (dm == null) {
                                        return false;
                                    }
                                    String[] paths = dm.value().length > 0 ? dm.value() : dm.path();
                                    for (String p : paths) {
                                        if ("/runtime-trace-regression-suite-definitions/{definitionId}/runs/{runId}"
                                                .equals(p)) {
                                            return true;
                                        }
                                    }
                                    return false;
                                })
                        .count();
        assertThat(runsPathDeletes).isEqualTo(1);

        long definitionOnlyDeletes =
                Arrays.stream(RuntimeTraceRegressionSuiteDefinitionController.class.getDeclaredMethods())
                        .filter(
                                m -> {
                                    DeleteMapping dm = m.getAnnotation(DeleteMapping.class);
                                    if (dm == null) {
                                        return false;
                                    }
                                    String[] paths = dm.value().length > 0 ? dm.value() : dm.path();
                                    for (String p : paths) {
                                        if ("/runtime-trace-regression-suite-definitions/{definitionId}".equals(p)) {
                                            return true;
                                        }
                                    }
                                    return false;
                                })
                        .count();
        assertThat(definitionOnlyDeletes).isEqualTo(1);
    }
}
