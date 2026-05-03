package com.uniovi.rag.interfaces.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.uniovi.rag.domain.runtime.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunSummary;
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
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = RuntimeTraceRegressionSuiteDefinitionController.class)
@ContextConfiguration(classes = RagWebMvcTestApplication.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({RuntimeTraceRegressionSuiteDefinitionController.class, RegressionSuiteDefinitionMutationJacksonConfiguration.class})
@ActiveProfiles("test")
@TestPropertySource(properties = "rag.api.product-base-path=/api/v1")
class RuntimeTraceRegressionSuiteDefinitionControllerRunQueryWebMvcTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
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

    @MockitoBean
    private DefinitionRunZipServiceBundle runZipServices;

    private UUID userId;
    private UUID definitionId;
    private UUID runId;

    @BeforeEach
    void setUp() {
        DefinitionRunZipBundleStubbing.linkMockBundleToZipServices(
                runZipServices, runExportService, runImportService, runImportPreviewService);
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

    private void verifyP50NeverMutateOrGlobalRunReads() {
        verify(suiteService, never()).execute(any());
        verify(runPersistenceService, never()).createRun(any(), any(), any(), any());
        verify(runPersistenceService, never()).deleteRunForUser(any(), any());
    }

    @Test
    void p50_t1_list_emptySummaries_200_emptyRuns() throws Exception {
        when(definitionService.loadByIdForUser(definitionId, userId))
                .thenReturn(Optional.of(minimalDefinitionSnapshot(definitionId)));
        when(runPersistenceService.listSummariesForUserAndDefinition(userId, definitionId)).thenReturn(List.of());

        mockMvc.perform(get(productBase() + "/runtime-trace-regression-suite-definitions/{id}/runs", definitionId))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"runs\":[]}", true));

        verify(definitionService, times(1)).loadByIdForUser(eq(definitionId), eq(userId));
        verify(runPersistenceService, times(1)).listSummariesForUserAndDefinition(eq(userId), eq(definitionId));
        verify(runPersistenceService, never()).loadByIdForUserAndDefinition(any(), any(), any());
        verifyP50NeverMutateOrGlobalRunReads();
    }

    @Test
    void p50_t2_list_twoSummaries_orderPreserved() throws Exception {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        Instant t = Instant.parse("2024-01-01T00:00:00Z");
        List<RuntimeTraceRegressionSuiteRunSummary> rows =
                List.of(
                        new RuntimeTraceRegressionSuiteRunSummary(
                                new RuntimeTraceRegressionSuiteRunId(id1),
                                RuntimeTraceRegressionSuiteRunSourceType.SAVED_DEFINITION,
                                Optional.of(definitionId),
                                RuntimeTraceRegressionSuiteOutcome.COMPLETED_ALL_BATCH_RETURNS,
                                t,
                                1,
                                1,
                                1,
                                0,
                                0),
                        new RuntimeTraceRegressionSuiteRunSummary(
                                new RuntimeTraceRegressionSuiteRunId(id2),
                                RuntimeTraceRegressionSuiteRunSourceType.SAVED_DEFINITION,
                                Optional.of(definitionId),
                                RuntimeTraceRegressionSuiteOutcome.COMPLETED_WITH_ENTRY_EXECUTION_FAILURES,
                                t,
                                2,
                                2,
                                0,
                                1,
                                0));
        when(definitionService.loadByIdForUser(definitionId, userId))
                .thenReturn(Optional.of(minimalDefinitionSnapshot(definitionId)));
        when(runPersistenceService.listSummariesForUserAndDefinition(userId, definitionId)).thenReturn(rows);

        mockMvc.perform(get(productBase() + "/runtime-trace-regression-suite-definitions/{id}/runs", definitionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runs.length()").value(2))
                .andExpect(jsonPath("$.runs[0].id").value(id1.toString()))
                .andExpect(jsonPath("$.runs[1].id").value(id2.toString()));

        verifyP50NeverMutateOrGlobalRunReads();
    }

    @Test
    void p50_t3_list_queryString_400_noDownstream() throws Exception {
        mockMvc.perform(
                        get(productBase() + "/runtime-trace-regression-suite-definitions/{id}/runs", definitionId)
                                .queryParam("x", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(""));
        verify(definitionService, never()).loadByIdForUser(any(), any());
        verify(runPersistenceService, never()).listSummariesForUserAndDefinition(any(), any());
        verify(runPersistenceService, never()).loadByIdForUserAndDefinition(any(), any(), any());
    }

    @Test
    void p50_t3_detail_queryString_400_noDownstream() throws Exception {
        mockMvc.perform(
                        get(
                                productBase()
                                        + "/runtime-trace-regression-suite-definitions/{defId}/runs/{runId}",
                                definitionId,
                                runId)
                                .queryParam("x", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(""));
        verify(definitionService, never()).loadByIdForUser(any(), any());
        verify(runPersistenceService, never()).listSummariesForUserAndDefinition(any(), any());
        verify(runPersistenceService, never()).loadByIdForUserAndDefinition(any(), any(), any());
    }

    @Test
    void p50_t4_list_invalid_definitionId_400() throws Exception {
        mockMvc.perform(get(productBase() + "/runtime-trace-regression-suite-definitions/{id}/runs", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(""));
        verify(definitionService, never()).loadByIdForUser(any(), any());
        verify(runPersistenceService, never()).listSummariesForUserAndDefinition(any(), any());
        verify(runPersistenceService, never()).loadByIdForUserAndDefinition(any(), any(), any());
    }

    @Test
    void p50_t4_detail_invalid_definitionId_400() throws Exception {
        mockMvc.perform(
                        get(
                                productBase()
                                        + "/runtime-trace-regression-suite-definitions/{defId}/runs/{runId}",
                                "not-a-uuid",
                                runId))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(""));
        verify(definitionService, never()).loadByIdForUser(any(), any());
        verify(runPersistenceService, never()).loadByIdForUserAndDefinition(any(), any(), any());
    }

    @Test
    void p50_t5_list_gate_empty_404_neverRunQueries() throws Exception {
        when(definitionService.loadByIdForUser(definitionId, userId)).thenReturn(Optional.empty());
        mockMvc.perform(get(productBase() + "/runtime-trace-regression-suite-definitions/{id}/runs", definitionId))
                .andExpect(status().isNotFound())
                .andExpect(
                        r ->
                                assertThat(r.getResolvedException())
                                        .isInstanceOf(NotFoundException.class)
                                        .hasMessage("definition not found"));
        verify(runPersistenceService, never()).listSummariesForUserAndDefinition(any(), any());
        verify(runPersistenceService, never()).loadByIdForUserAndDefinition(any(), any(), any());
    }

    @Test
    void p50_t5b_detail_gate_empty_404_neverLoadByIdForUserAndDefinition() throws Exception {
        when(definitionService.loadByIdForUser(definitionId, userId)).thenReturn(Optional.empty());
        mockMvc.perform(
                        get(
                                productBase()
                                        + "/runtime-trace-regression-suite-definitions/{defId}/runs/{runId}",
                                definitionId,
                                runId))
                .andExpect(status().isNotFound())
                .andExpect(
                        r ->
                                assertThat(r.getResolvedException())
                                        .isInstanceOf(NotFoundException.class)
                                        .hasMessage("definition not found"));
        verify(runPersistenceService, never()).loadByIdForUserAndDefinition(any(), any(), any());
    }

    @Test
    void p50_t6_detail_200_elevenTopLevelKeys() throws Exception {
        when(definitionService.loadByIdForUser(definitionId, userId))
                .thenReturn(Optional.of(minimalDefinitionSnapshot(definitionId)));
        RuntimeTraceRegressionSuiteRunSnapshot snap =
                new RuntimeTraceRegressionSuiteRunSnapshot(
                        new RuntimeTraceRegressionSuiteRunId(runId),
                        userId,
                        RuntimeTraceRegressionSuiteRunSourceType.SAVED_DEFINITION,
                        Optional.of(definitionId),
                        RuntimeTraceRegressionSuiteOutcome.COMPLETED_ALL_BATCH_RETURNS,
                        new RuntimeTraceRegressionSuiteSummary(1, 1, 1, 0, 0),
                        Instant.parse("2024-03-01T12:00:00Z"),
                        List.of());
        when(runPersistenceService.loadByIdForUserAndDefinition(runId, userId, definitionId))
                .thenReturn(Optional.of(snap));

        MvcResult result =
                mockMvc.perform(
                                get(
                                        productBase()
                                                + "/runtime-trace-regression-suite-definitions/{defId}/runs/{rid}",
                                        definitionId,
                                        runId))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.id").value(runId.toString()))
                        .andExpect(jsonPath("$.definitionId").value(definitionId.toString()))
                        .andExpect(jsonPath("$.entries").isArray())
                        .andExpect(jsonPath("$.entries", hasSize(0)))
                        .andReturn();

        JsonNode root = OBJECT_MAPPER.readTree(result.getResponse().getContentAsString());
        Set<String> names = new HashSet<>();
        root.fieldNames().forEachRemaining(names::add);
        assertThat(names)
                .hasSize(11)
                .isEqualTo(
                        Set.of(
                                "id",
                                "sourceType",
                                "definitionId",
                                "suiteOutcome",
                                "createdAt",
                                "requestedEntryCount",
                                "processedEntryCount",
                                "batchReturnedCount",
                                "executionFailedCount",
                                "batchNotAttemptedSubcount",
                                "entries"));
        verifyP50NeverMutateOrGlobalRunReads();
    }

    @Test
    void p50_t7_detail_runMissing_404() throws Exception {
        when(definitionService.loadByIdForUser(definitionId, userId))
                .thenReturn(Optional.of(minimalDefinitionSnapshot(definitionId)));
        when(runPersistenceService.loadByIdForUserAndDefinition(runId, userId, definitionId))
                .thenReturn(Optional.empty());

        mockMvc.perform(
                        get(
                                productBase()
                                        + "/runtime-trace-regression-suite-definitions/{defId}/runs/{rid}",
                                definitionId,
                                runId))
                .andExpect(status().isNotFound())
                .andExpect(
                        r ->
                                assertThat(r.getResolvedException())
                                        .isInstanceOf(NotFoundException.class)
                                        .hasMessage("run not found"));
        verifyP50NeverMutateOrGlobalRunReads();
    }

    @Test
    void p50_t8_detail_invalid_runId_400_neverLoadScoped() throws Exception {
        mockMvc.perform(
                        get(
                                productBase()
                                        + "/runtime-trace-regression-suite-definitions/{defId}/runs/{rid}",
                                definitionId,
                                "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(""));
        verify(definitionService, never()).loadByIdForUser(any(), any());
        verify(runPersistenceService, never()).loadByIdForUserAndDefinition(any(), any(), any());
    }

    @Test
    void p50_t9_list_and_detail_never_execute_create_delete() throws Exception {
        when(definitionService.loadByIdForUser(definitionId, userId))
                .thenReturn(Optional.of(minimalDefinitionSnapshot(definitionId)));
        when(runPersistenceService.listSummariesForUserAndDefinition(userId, definitionId)).thenReturn(List.of());
        mockMvc.perform(get(productBase() + "/runtime-trace-regression-suite-definitions/{id}/runs", definitionId))
                .andExpect(status().isOk());
        verifyP50NeverMutateOrGlobalRunReads();

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
        verifyP50NeverMutateOrGlobalRunReads();
    }

    @Test
    void p50_t10_detail_never_global_runReads() throws Exception {
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

        verify(runPersistenceService, never()).loadByIdForUser(any(), any());
        verify(runPersistenceService, never()).listSummariesForUser(any());
    }

    @Test
    void p50_t11_list_verify_gate_and_listScopedOnce() throws Exception {
        when(definitionService.loadByIdForUser(definitionId, userId))
                .thenReturn(Optional.of(minimalDefinitionSnapshot(definitionId)));
        when(runPersistenceService.listSummariesForUserAndDefinition(userId, definitionId)).thenReturn(List.of());

        mockMvc.perform(get(productBase() + "/runtime-trace-regression-suite-definitions/{id}/runs", definitionId))
                .andExpect(status().isOk());

        verify(runPersistenceService, times(1)).listSummariesForUserAndDefinition(eq(userId), eq(definitionId));
        verify(definitionService, times(1)).loadByIdForUser(eq(definitionId), eq(userId));
    }

    @Test
    void p50_t12_detail_verify_loadScopedOnce() throws Exception {
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
}
