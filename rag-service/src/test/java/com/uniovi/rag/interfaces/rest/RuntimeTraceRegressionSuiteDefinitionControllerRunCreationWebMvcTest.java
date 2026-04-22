package com.uniovi.rag.interfaces.rest;

import com.uniovi.rag.application.service.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteService;
import com.uniovi.rag.application.service.runtime.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionService;
import com.uniovi.rag.application.service.runtime.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunPersistenceService;
import com.uniovi.rag.application.service.runtime.traceregressionsuiterunexport.RuntimeTraceRegressionSuiteRunExportService;
import com.uniovi.rag.application.service.runtime.traceregressionsuiterunimport.RuntimeTraceRegressionSuiteRunImportService;
import com.uniovi.rag.application.service.runtime.traceregressionsuiterunimportpreview.RuntimeTraceRegressionSuiteRunImportPreviewService;
import com.uniovi.rag.configuration.RegressionSuiteDefinitionMutationJacksonConfiguration;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteEntry;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteOutcome;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteRequest;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteResult;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteSummary;
import com.uniovi.rag.domain.runtime.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunSourceType;
import com.uniovi.rag.security.RagPrincipal;
import com.uniovi.rag.testsupport.RagApiTestPaths;
import com.uniovi.rag.testsupport.webmvc.RagWebMvcTestApplication;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
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
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = RuntimeTraceRegressionSuiteDefinitionController.class)
@ContextConfiguration(classes = RagWebMvcTestApplication.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({RuntimeTraceRegressionSuiteDefinitionController.class, RegressionSuiteDefinitionMutationJacksonConfiguration.class})
@TestPropertySource(properties = "rag.api.product-base-path=/api/v1")
class RuntimeTraceRegressionSuiteDefinitionControllerRunCreationWebMvcTest {

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
    private UUID conversationId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        definitionId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
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

    @Test
    void t1_route1_persist_eligible_201_location_createRun_saved_definition() throws Exception {
        UUID createdRunId = UUID.randomUUID();
        RuntimeTraceRegressionSuiteRequest req =
                new RuntimeTraceRegressionSuiteRequest(
                        userId, List.of(new RuntimeTraceRegressionSuiteEntry.ByTraceIds(List.of(UUID.randomUUID()))));
        when(definitionService.materializeToSuiteRequest(eq(definitionId), eq(userId))).thenReturn(req);
        when(suiteService.execute(same(req)))
                .thenReturn(resultOf(RuntimeTraceRegressionSuiteOutcome.COMPLETED_ALL_BATCH_RETURNS));
        when(runPersistenceService.createRun(
                        eq(userId),
                        eq(RuntimeTraceRegressionSuiteRunSourceType.SAVED_DEFINITION),
                        eq(Optional.of(definitionId)),
                        any()))
                .thenReturn(createdRunId);

        MvcResult mvcResult =
                mockMvc.perform(
                                post(productBase() + "/runtime-trace-regression-suite-definitions/{id}/runs", definitionId)
                                        .contentType(MediaType.APPLICATION_JSON))
                        .andExpect(status().isCreated())
                        .andExpect(header().string(HttpHeaders.LOCATION, expectedLocation(createdRunId)))
                        .andReturn();

        assertThat(mvcResult.getResponse().getContentAsString()).isEmpty();
        verify(definitionService, times(1)).materializeToSuiteRequest(eq(definitionId), eq(userId));
        verify(suiteService, times(1)).execute(same(req));
        verify(runPersistenceService, times(1))
                .createRun(
                        eq(userId),
                        eq(RuntimeTraceRegressionSuiteRunSourceType.SAVED_DEFINITION),
                        eq(Optional.of(definitionId)),
                        any());
    }

    @Test
    void t2_route2_guard_passes_same_req_in_order() throws Exception {
        UUID createdRunId = UUID.randomUUID();
        RuntimeTraceRegressionSuiteRequest stubReq =
                new RuntimeTraceRegressionSuiteRequest(
                        userId,
                        List.of(
                                new RuntimeTraceRegressionSuiteEntry.ByConversation(
                                        conversationId,
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty())));
        when(definitionService.materializeToSuiteRequest(eq(definitionId), eq(userId))).thenReturn(stubReq);
        when(suiteService.execute(same(stubReq)))
                .thenReturn(resultOf(RuntimeTraceRegressionSuiteOutcome.COMPLETED_ALL_BATCH_RETURNS));
        when(runPersistenceService.createRun(
                        eq(userId),
                        eq(RuntimeTraceRegressionSuiteRunSourceType.SAVED_DEFINITION),
                        eq(Optional.of(definitionId)),
                        any()))
                .thenReturn(createdRunId);

        mockMvc.perform(
                        post(
                                productBase()
                                        + "/conversations/{cid}/runtime-trace-regression-suite-definitions/{id}/runs",
                                conversationId,
                                definitionId)
                                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, expectedLocation(createdRunId)));

        ArgumentCaptor<RuntimeTraceRegressionSuiteRequest> captor =
                ArgumentCaptor.forClass(RuntimeTraceRegressionSuiteRequest.class);
        verify(suiteService).execute(captor.capture());
        assertThat(captor.getValue()).isSameAs(stubReq);

        InOrder inOrder = inOrder(definitionService, suiteService);
        inOrder.verify(definitionService).materializeToSuiteRequest(eq(definitionId), eq(userId));
        inOrder.verify(suiteService).execute(same(stubReq));
    }

    @Test
    void t3_query_string_400_no_service_calls() throws Exception {
        mockMvc.perform(
                        post(productBase() + "/runtime-trace-regression-suite-definitions/{id}/runs", definitionId)
                                .queryParam("x", "1")
                                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(""))
                .andExpect(result -> assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION)).isNull());

        verify(definitionService, never()).materializeToSuiteRequest(any(), any());
        verify(suiteService, never()).execute(any());
        verify(runPersistenceService, never()).createRun(any(), any(), any(), any());
    }

    @Test
    void t4_invalid_definition_id_400() throws Exception {
        mockMvc.perform(
                        post(productBase() + "/runtime-trace-regression-suite-definitions/{id}/runs", "not-uuid")
                                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(""))
                .andExpect(result -> assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION)).isNull());

        verify(definitionService, never()).materializeToSuiteRequest(any(), any());
        verify(suiteService, never()).execute(any());
        verify(runPersistenceService, never()).createRun(any(), any(), any(), any());
    }

    @Test
    void t5_route2_invalid_conversation_id_400() throws Exception {
        mockMvc.perform(
                        post(
                                productBase()
                                        + "/conversations/{cid}/runtime-trace-regression-suite-definitions/{id}/runs",
                                "bad",
                                definitionId)
                                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(""))
                .andExpect(result -> assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION)).isNull());

        verify(definitionService, never()).materializeToSuiteRequest(any(), any());
        verify(suiteService, never()).execute(any());
        verify(runPersistenceService, never()).createRun(any(), any(), any(), any());
    }

    @Test
    void t6_non_empty_body_400() throws Exception {
        mockMvc.perform(
                        post(productBase() + "/runtime-trace-regression-suite-definitions/{id}/runs", definitionId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(""))
                .andExpect(result -> assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION)).isNull());

        verify(definitionService, never()).materializeToSuiteRequest(any(), any());
        verify(suiteService, never()).execute(any());
        verify(runPersistenceService, never()).createRun(any(), any(), any(), any());
    }

    @Test
    void t7_materialize_not_found_404() throws Exception {
        when(definitionService.materializeToSuiteRequest(eq(definitionId), eq(userId)))
                .thenThrow(new NotFoundException("missing"));
        mockMvc.perform(
                        post(productBase() + "/runtime-trace-regression-suite-definitions/{id}/runs", definitionId)
                                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(content().string(""))
                .andExpect(result -> assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION)).isNull());

        verify(definitionService, times(1)).materializeToSuiteRequest(eq(definitionId), eq(userId));
        verify(suiteService, never()).execute(any());
        verify(runPersistenceService, never()).createRun(any(), any(), any(), any());
    }

    @Test
    void t8_route2_zero_by_conversation_entries_404() throws Exception {
        RuntimeTraceRegressionSuiteRequest req =
                new RuntimeTraceRegressionSuiteRequest(
                        userId, List.of(new RuntimeTraceRegressionSuiteEntry.ByTraceIds(List.of(UUID.randomUUID()))));
        when(definitionService.materializeToSuiteRequest(eq(definitionId), eq(userId))).thenReturn(req);

        mockMvc.perform(
                        post(
                                productBase()
                                        + "/conversations/{cid}/runtime-trace-regression-suite-definitions/{id}/runs",
                                conversationId,
                                definitionId)
                                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(content().string(""))
                .andExpect(result -> assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION)).isNull());

        verify(definitionService, times(1)).materializeToSuiteRequest(eq(definitionId), eq(userId));
        verify(suiteService, never()).execute(any());
        verify(runPersistenceService, never()).createRun(any(), any(), any(), any());
    }

    @Test
    void t9_route2_conversation_id_mismatch_404() throws Exception {
        UUID other = UUID.randomUUID();
        RuntimeTraceRegressionSuiteRequest req =
                new RuntimeTraceRegressionSuiteRequest(
                        userId,
                        List.of(
                                new RuntimeTraceRegressionSuiteEntry.ByConversation(
                                        other, Optional.empty(), Optional.empty(), Optional.empty())));
        when(definitionService.materializeToSuiteRequest(eq(definitionId), eq(userId))).thenReturn(req);

        mockMvc.perform(
                        post(
                                productBase()
                                        + "/conversations/{cid}/runtime-trace-regression-suite-definitions/{id}/runs",
                                conversationId,
                                definitionId)
                                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(content().string(""))
                .andExpect(result -> assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION)).isNull());

        verify(definitionService, times(1)).materializeToSuiteRequest(eq(definitionId), eq(userId));
        verify(suiteService, never()).execute(any());
        verify(runPersistenceService, never()).createRun(any(), any(), any(), any());
    }

    @Test
    void t10_not_attempted_400_execute_once_never_create_run() throws Exception {
        RuntimeTraceRegressionSuiteRequest req =
                new RuntimeTraceRegressionSuiteRequest(
                        userId, List.of(new RuntimeTraceRegressionSuiteEntry.ByTraceIds(List.of(UUID.randomUUID()))));
        when(definitionService.materializeToSuiteRequest(eq(definitionId), eq(userId))).thenReturn(req);
        when(suiteService.execute(same(req)))
                .thenReturn(resultOf(RuntimeTraceRegressionSuiteOutcome.NOT_ATTEMPTED));

        MvcResult mvcResult =
                mockMvc.perform(
                                post(productBase() + "/runtime-trace-regression-suite-definitions/{id}/runs", definitionId)
                                        .contentType(MediaType.APPLICATION_JSON))
                        .andExpect(status().isBadRequest())
                        .andReturn();

        assertThat(mvcResult.getResponse().getContentAsString()).isEmpty();
        assertThat(mvcResult.getResponse().getHeader(HttpHeaders.LOCATION)).isNull();
        verify(suiteService, times(1)).execute(same(req));
        verify(runPersistenceService, never()).createRun(any(), any(), any(), any());
    }

    @Test
    void t11_empty_suite_201_create_run_once() throws Exception {
        UUID createdRunId = UUID.randomUUID();
        RuntimeTraceRegressionSuiteRequest req =
                new RuntimeTraceRegressionSuiteRequest(
                        userId, List.of(new RuntimeTraceRegressionSuiteEntry.ByTraceIds(List.of(UUID.randomUUID()))));
        when(definitionService.materializeToSuiteRequest(eq(definitionId), eq(userId))).thenReturn(req);
        when(suiteService.execute(same(req))).thenReturn(resultOf(RuntimeTraceRegressionSuiteOutcome.EMPTY_SUITE));
        when(runPersistenceService.createRun(
                        eq(userId),
                        eq(RuntimeTraceRegressionSuiteRunSourceType.SAVED_DEFINITION),
                        eq(Optional.of(definitionId)),
                        any()))
                .thenReturn(createdRunId);

        MvcResult mvcResult =
                mockMvc.perform(
                                post(productBase() + "/runtime-trace-regression-suite-definitions/{id}/runs", definitionId)
                                        .contentType(MediaType.APPLICATION_JSON))
                        .andExpect(status().isCreated())
                        .andReturn();

        assertThat(mvcResult.getResponse().getContentAsString()).isEmpty();
        verify(runPersistenceService, times(1))
                .createRun(
                        eq(userId),
                        eq(RuntimeTraceRegressionSuiteRunSourceType.SAVED_DEFINITION),
                        eq(Optional.of(definitionId)),
                        any());
    }

    @Test
    void t12_t13_happy_path_never_other_definition_or_run_reads() throws Exception {
        UUID createdRunId = UUID.randomUUID();
        RuntimeTraceRegressionSuiteRequest req =
                new RuntimeTraceRegressionSuiteRequest(
                        userId, List.of(new RuntimeTraceRegressionSuiteEntry.ByTraceIds(List.of(UUID.randomUUID()))));
        when(definitionService.materializeToSuiteRequest(eq(definitionId), eq(userId))).thenReturn(req);
        when(suiteService.execute(same(req)))
                .thenReturn(resultOf(RuntimeTraceRegressionSuiteOutcome.COMPLETED_ALL_BATCH_RETURNS));
        when(runPersistenceService.createRun(
                        eq(userId),
                        eq(RuntimeTraceRegressionSuiteRunSourceType.SAVED_DEFINITION),
                        eq(Optional.of(definitionId)),
                        any()))
                .thenReturn(createdRunId);

        mockMvc.perform(
                        post(productBase() + "/runtime-trace-regression-suite-definitions/{id}/runs", definitionId)
                                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated());

        verify(definitionService, never()).create(any(), any());
        verify(definitionService, never()).update(any(), any(), any());
        verify(definitionService, never()).delete(any(), any());
        verify(definitionService, never()).loadByIdForUser(any(), any());
        verify(definitionService, never()).listSummariesForUser(any());
        verify(runPersistenceService, never()).loadByIdForUser(any(), any());
        verify(runPersistenceService, never()).listSummariesForUser(any());
    }

    @Test
    void t14_201_location_suffix_no_query() throws Exception {
        UUID createdRunId = UUID.randomUUID();
        RuntimeTraceRegressionSuiteRequest req =
                new RuntimeTraceRegressionSuiteRequest(
                        userId, List.of(new RuntimeTraceRegressionSuiteEntry.ByTraceIds(List.of(UUID.randomUUID()))));
        when(definitionService.materializeToSuiteRequest(eq(definitionId), eq(userId))).thenReturn(req);
        when(suiteService.execute(same(req)))
                .thenReturn(resultOf(RuntimeTraceRegressionSuiteOutcome.COMPLETED_WITH_ENTRY_EXECUTION_FAILURES));
        when(runPersistenceService.createRun(
                        eq(userId),
                        eq(RuntimeTraceRegressionSuiteRunSourceType.SAVED_DEFINITION),
                        eq(Optional.of(definitionId)),
                        any()))
                .thenReturn(createdRunId);

        MvcResult mvcResult =
                mockMvc.perform(
                                post(productBase() + "/runtime-trace-regression-suite-definitions/{id}/runs", definitionId)
                                        .contentType(MediaType.APPLICATION_JSON))
                        .andExpect(status().isCreated())
                        .andReturn();

        String loc = mvcResult.getResponse().getHeader(HttpHeaders.LOCATION);
        assertThat(loc).endsWith("/" + createdRunId);
        assertThat(loc).doesNotContain("?");
        verify(suiteService, times(1)).execute(same(req));
        verify(runPersistenceService, times(1))
                .createRun(
                        eq(userId),
                        eq(RuntimeTraceRegressionSuiteRunSourceType.SAVED_DEFINITION),
                        eq(Optional.of(definitionId)),
                        any());
    }

    @Test
    void t17_completed_all_batch_returns_201_matches_location_rule() throws Exception {
        UUID createdRunId = UUID.randomUUID();
        RuntimeTraceRegressionSuiteRequest req =
                new RuntimeTraceRegressionSuiteRequest(
                        userId, List.of(new RuntimeTraceRegressionSuiteEntry.ByTraceIds(List.of(UUID.randomUUID()))));
        when(definitionService.materializeToSuiteRequest(eq(definitionId), eq(userId))).thenReturn(req);
        when(suiteService.execute(same(req)))
                .thenReturn(resultOf(RuntimeTraceRegressionSuiteOutcome.COMPLETED_ALL_BATCH_RETURNS));
        when(runPersistenceService.createRun(
                        eq(userId),
                        eq(RuntimeTraceRegressionSuiteRunSourceType.SAVED_DEFINITION),
                        eq(Optional.of(definitionId)),
                        any()))
                .thenReturn(createdRunId);

        MvcResult mvcResult =
                mockMvc.perform(
                                post(productBase() + "/runtime-trace-regression-suite-definitions/{id}/runs", definitionId)
                                        .contentType(MediaType.APPLICATION_JSON))
                        .andExpect(status().isCreated())
                        .andExpect(header().string(HttpHeaders.LOCATION, expectedLocation(createdRunId)))
                        .andReturn();

        assertThat(mvcResult.getResponse().getContentAsString()).isEmpty();
        assertThat(mvcResult.getResponse().getHeader(HttpHeaders.LOCATION)).isNotNull();
        verify(runPersistenceService, times(1))
                .createRun(
                        eq(userId),
                        eq(RuntimeTraceRegressionSuiteRunSourceType.SAVED_DEFINITION),
                        eq(Optional.of(definitionId)),
                        any());
    }

    @Test
    void t16_201_has_non_null_location_header() throws Exception {
        UUID createdRunId = UUID.randomUUID();
        RuntimeTraceRegressionSuiteRequest req =
                new RuntimeTraceRegressionSuiteRequest(
                        userId, List.of(new RuntimeTraceRegressionSuiteEntry.ByTraceIds(List.of(UUID.randomUUID()))));
        when(definitionService.materializeToSuiteRequest(eq(definitionId), eq(userId))).thenReturn(req);
        when(suiteService.execute(same(req)))
                .thenReturn(resultOf(RuntimeTraceRegressionSuiteOutcome.COMPLETED_ALL_BATCH_RETURNS));
        when(runPersistenceService.createRun(
                        eq(userId),
                        eq(RuntimeTraceRegressionSuiteRunSourceType.SAVED_DEFINITION),
                        eq(Optional.of(definitionId)),
                        any()))
                .thenReturn(createdRunId);

        MvcResult mvcResult =
                mockMvc.perform(
                                post(productBase() + "/runtime-trace-regression-suite-definitions/{id}/runs", definitionId)
                                        .contentType(MediaType.APPLICATION_JSON))
                        .andExpect(status().isCreated())
                        .andReturn();

        assertThat(mvcResult.getResponse().getHeader(HttpHeaders.LOCATION)).isNotNull();
    }

    @Test
    void p47_p50_p52_p53_p54_p55_controller_has_fifteen_http_handler_methods() {
        long count =
                Arrays.stream(RuntimeTraceRegressionSuiteDefinitionController.class.getDeclaredMethods())
                        .filter(
                                m ->
                                        m.isAnnotationPresent(GetMapping.class)
                                                || m.isAnnotationPresent(PostMapping.class)
                                                || m.isAnnotationPresent(PutMapping.class)
                                                || m.isAnnotationPresent(DeleteMapping.class))
                        .count();
        assertThat(count).isEqualTo(15);
    }

    private String expectedLocation(UUID createdRunId) {
        return productBase() + "/runtime-trace-regression-suite-runs/" + createdRunId;
    }

    private static RuntimeTraceRegressionSuiteResult resultOf(RuntimeTraceRegressionSuiteOutcome outcome) {
        return new RuntimeTraceRegressionSuiteResult(
                outcome, new RuntimeTraceRegressionSuiteSummary(0, 0, 0, 0, 0), List.of());
    }
}
