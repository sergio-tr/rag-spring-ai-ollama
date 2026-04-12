package com.uniovi.rag.interfaces.rest;

import com.uniovi.rag.application.service.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteService;
import com.uniovi.rag.application.service.runtime.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionService;
import com.uniovi.rag.application.service.runtime.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunPersistenceService;
import com.uniovi.rag.application.service.runtime.traceregressionsuiterunexport.RuntimeTraceRegressionSuiteRunExportService;
import com.uniovi.rag.application.service.runtime.traceregressionsuiterunimport.RuntimeTraceRegressionSuiteRunImportService;
import com.uniovi.rag.application.service.runtime.traceregressionsuiterunimportpreview.RuntimeTraceRegressionSuiteRunImportPreviewService;
import com.uniovi.rag.domain.runtime.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionEntrySnapshot;
import com.uniovi.rag.domain.runtime.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionSnapshot;
import com.uniovi.rag.domain.runtime.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionUserSummary;
import com.uniovi.rag.security.RagPrincipal;
import com.uniovi.rag.testsupport.webmvc.RagWebMvcTestApplication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import com.uniovi.rag.configuration.RegressionSuiteDefinitionMutationJacksonConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
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
class RuntimeTraceRegressionSuiteDefinitionControllerTest {

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

    @MockitoBean
    private RuntimeTraceRegressionSuiteRunImportPreviewService runImportPreviewService;

    private UUID userId;
    private UUID definitionId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        definitionId = UUID.randomUUID();
        RagPrincipal principal = new RagPrincipal(userId, "u@test", "USER");
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(
                        principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
        verify(runPersistenceService, never()).createRun(any(), any(), any(), any());
    }

    @Test
    void list_noQueryString_emptyService_returns200_emptyDefinitions() throws Exception {
        when(definitionService.listSummariesForUser(userId)).thenReturn(List.of());
        mockMvc.perform(get("/api/v5/runtime-trace-regression-suite-definitions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.definitions").isArray())
                .andExpect(jsonPath("$.definitions", hasSize(0)))
                .andExpect(jsonPath("$..hibernateLazyInitializer").doesNotExist());
        verify(definitionService, times(1)).listSummariesForUser(userId);
        verify(definitionService, never()).loadByIdForUser(any(), any());
        verifyMutatingNever();
    }

    @Test
    void list_noQueryString_twoSummaries_preservesOrder() throws Exception {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        Instant t1 = Instant.parse("2024-01-01T00:00:00Z");
        Instant t2 = Instant.parse("2024-01-02T00:00:00Z");
        List<RuntimeTraceRegressionSuiteDefinitionUserSummary> rows =
                List.of(
                        new RuntimeTraceRegressionSuiteDefinitionUserSummary(id1, "a", null, 2, t1, t2),
                        new RuntimeTraceRegressionSuiteDefinitionUserSummary(id2, "b", "d", 1, t1, t2));
        when(definitionService.listSummariesForUser(userId)).thenReturn(rows);
        mockMvc.perform(get("/api/v5/runtime-trace-regression-suite-definitions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.definitions[0].id").value(id1.toString()))
                .andExpect(jsonPath("$.definitions[1].id").value(id2.toString()));
        verify(definitionService, times(1)).listSummariesForUser(userId);
        verify(definitionService, never()).loadByIdForUser(any(), any());
        verifyMutatingNever();
    }

    @Test
    void list_queryParam_returns400_emptyBody() throws Exception {
        mockMvc.perform(get("/api/v5/runtime-trace-regression-suite-definitions").queryParam("a", "b"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(""));
        verify(definitionService, never()).listSummariesForUser(any());
        verify(definitionService, never()).loadByIdForUser(any(), any());
        verifyMutatingNever();
    }

    @Test
    void list_emptyQueryString_returns400() throws Exception {
        mockMvc.perform(
                        get("/api/v5/runtime-trace-regression-suite-definitions")
                                .with((RequestPostProcessor) request -> {
                                    request.setQueryString("");
                                    return request;
                                }))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(""));
        verify(definitionService, never()).listSummariesForUser(any());
        verifyMutatingNever();
    }

    @Test
    void detail_validUuid_snapshot_returns200_shape() throws Exception {
        UUID tid1 = UUID.randomUUID();
        UUID tid2 = UUID.randomUUID();
        UUID conv = UUID.randomUUID();
        RuntimeTraceRegressionSuiteDefinitionSnapshot snap =
                new RuntimeTraceRegressionSuiteDefinitionSnapshot(
                        definitionId,
                        "suite",
                        null,
                        1,
                        Instant.parse("2024-03-01T12:00:00Z"),
                        Instant.parse("2024-03-02T12:00:00Z"),
                        List.of(
                                new RuntimeTraceRegressionSuiteDefinitionEntrySnapshot.ByTraceIds(List.of(tid1, tid2)),
                                new RuntimeTraceRegressionSuiteDefinitionEntrySnapshot.ByConversation(
                                        conv, null, null, "wf")));
        when(definitionService.loadByIdForUser(definitionId, userId)).thenReturn(Optional.of(snap));
        mockMvc.perform(get("/api/v5/runtime-trace-regression-suite-definitions/{id}", definitionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(definitionId.toString()))
                .andExpect(jsonPath("$.name").value("suite"))
                .andExpect(jsonPath("$.description").value(nullValue()))
                .andExpect(jsonPath("$.schemaVersion").value(1))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists())
                .andExpect(jsonPath("$.entries").isArray())
                .andExpect(jsonPath("$.entries[0].entryKind").value("BY_TRACE_IDS"))
                .andExpect(jsonPath("$.entries[0].traceIds[0]").value(tid1.toString()))
                .andExpect(jsonPath("$.entries[0].traceIds[1]").value(tid2.toString()))
                .andExpect(jsonPath("$.entries[0].conversationId").doesNotExist())
                .andExpect(jsonPath("$.entries[0].createdAtFrom").doesNotExist())
                .andExpect(jsonPath("$.entries[0].createdAtTo").doesNotExist())
                .andExpect(jsonPath("$.entries[0].workflowName").doesNotExist())
                .andExpect(jsonPath("$.entries[1].entryKind").value("BY_CONVERSATION"))
                .andExpect(jsonPath("$.entries[1].conversationId").value(conv.toString()))
                .andExpect(jsonPath("$.entries[1].traceIds").doesNotExist())
                .andExpect(jsonPath("$..hibernateLazyInitializer").doesNotExist());
        verify(definitionService, times(1)).loadByIdForUser(definitionId, userId);
        verify(definitionService, never()).listSummariesForUser(any());
        verifyMutatingNever();
    }

    @Test
    void detail_emptyOptional_wrongOwner_returns404() throws Exception {
        when(definitionService.loadByIdForUser(definitionId, userId)).thenReturn(Optional.empty());
        mockMvc.perform(get("/api/v5/runtime-trace-regression-suite-definitions/{id}", definitionId))
                .andExpect(status().isNotFound())
                .andExpect(
                        result ->
                                assertThat(result.getResolvedException())
                                        .isInstanceOf(NotFoundException.class)
                                        .hasMessage("definition not found"));
        verify(definitionService, times(1)).loadByIdForUser(definitionId, userId);
        verify(definitionService, never()).listSummariesForUser(any());
        verifyMutatingNever();
    }

    @Test
    void detail_emptyOptional_missingId_returns404() throws Exception {
        UUID missing = UUID.randomUUID();
        when(definitionService.loadByIdForUser(missing, userId)).thenReturn(Optional.empty());
        mockMvc.perform(get("/api/v5/runtime-trace-regression-suite-definitions/{id}", missing))
                .andExpect(status().isNotFound());
        verifyMutatingNever();
    }

    @Test
    void detail_malformedUuid_returns400() throws Exception {
        mockMvc.perform(get("/api/v5/runtime-trace-regression-suite-definitions/{id}", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(""));
        verify(definitionService, never()).loadByIdForUser(any(), any());
        verifyMutatingNever();
    }

    @Test
    void detail_queryParam_returns400() throws Exception {
        mockMvc.perform(
                        get("/api/v5/runtime-trace-regression-suite-definitions/{id}", definitionId)
                                .queryParam("x", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(""));
        verify(definitionService, never()).loadByIdForUser(any(), any());
        verifyMutatingNever();
    }

    @Test
    void list_success_rootKeysOnlyDefinitions() throws Exception {
        when(definitionService.listSummariesForUser(userId))
                .thenReturn(
                        List.of(
                                new RuntimeTraceRegressionSuiteDefinitionUserSummary(
                                        definitionId, "n", null, 1, Instant.now(), Instant.now())));
        mockMvc.perform(get("/api/v5/runtime-trace-regression-suite-definitions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.definitions").exists())
                .andExpect(jsonPath("$.id").doesNotExist());
        verifyMutatingNever();
    }

    @Test
    void list_oneSummary_hasExactSummaryKeys() throws Exception {
        Instant c = Instant.parse("2024-01-05T00:00:00Z");
        Instant u = Instant.parse("2024-01-06T00:00:00Z");
        when(definitionService.listSummariesForUser(userId))
                .thenReturn(
                        List.of(
                                new RuntimeTraceRegressionSuiteDefinitionUserSummary(
                                        definitionId, "nm", "desc", 3, c, u)));
        mockMvc.perform(get("/api/v5/runtime-trace-regression-suite-definitions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.definitions[0].id").value(definitionId.toString()))
                .andExpect(jsonPath("$.definitions[0].name").value("nm"))
                .andExpect(jsonPath("$.definitions[0].description").value("desc"))
                .andExpect(jsonPath("$.definitions[0].entryCount").value(3))
                .andExpect(jsonPath("$.definitions[0].createdAt").exists())
                .andExpect(jsonPath("$.definitions[0].updatedAt").exists())
                .andExpect(jsonPath("$.definitions[0].entries").doesNotExist());
        verifyMutatingNever();
    }

    private void verifyMutatingNever() {
        verify(definitionService, never()).create(any(), any());
        verify(definitionService, never()).update(any(), any(), any());
        verify(definitionService, never()).delete(any(), any());
        verify(definitionService, never()).materializeToSuiteRequest(any(), any());
        verify(suiteService, never()).execute(any());
    }
}
