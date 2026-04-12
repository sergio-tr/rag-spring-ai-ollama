package com.uniovi.rag.interfaces.rest;

import com.uniovi.rag.application.service.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteService;
import com.uniovi.rag.application.service.runtime.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionService;
import com.uniovi.rag.application.service.runtime.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunPersistenceService;
import com.uniovi.rag.application.service.runtime.traceregressionsuiterunexport.RuntimeTraceRegressionSuiteRunExportService;
import com.uniovi.rag.configuration.RegressionSuiteDefinitionMutationJacksonConfiguration;
import com.uniovi.rag.domain.runtime.tracecomparisonbatch.RuntimeTraceReplayComparisonBatchOutcome;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteBatchReturnedEntryResult;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteEntry;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteEntryKind;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteOutcome;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteRequest;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteResult;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteSummary;
import com.uniovi.rag.security.RagPrincipal;
import com.uniovi.rag.testsupport.webmvc.RagWebMvcTestApplication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = RuntimeTraceRegressionSuiteDefinitionController.class)
@ContextConfiguration(classes = RagWebMvcTestApplication.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({RuntimeTraceRegressionSuiteDefinitionController.class, RegressionSuiteDefinitionMutationJacksonConfiguration.class})
@TestPropertySource(properties = "rag.api.product-base-path=/api/test")
class RuntimeTraceRegressionSuiteDefinitionControllerExecutionWebMvcTest {

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
        verify(runPersistenceService, never()).createRun(any(), any(), any(), any());
    }

    @Test
    void t1_e1_happyPath_completedAllBatchReturns_inOrderSameReq() throws Exception {
        RuntimeTraceRegressionSuiteRequest req =
                new RuntimeTraceRegressionSuiteRequest(
                        userId,
                        List.of(new RuntimeTraceRegressionSuiteEntry.ByTraceIds(List.of(UUID.randomUUID()))));
        var row =
                new RuntimeTraceRegressionSuiteBatchReturnedEntryResult(
                        0,
                        RuntimeTraceRegressionSuiteEntryKind.BY_TRACE_IDS,
                        "echo",
                        RuntimeTraceReplayComparisonBatchOutcome.COMPLETED_ALL_EXACT_MATCH,
                        1,
                        1,
                        1);
        var summary = new RuntimeTraceRegressionSuiteSummary(1, 1, 1, 0, 0);
        when(definitionService.materializeToSuiteRequest(eq(definitionId), eq(userId))).thenReturn(req);
        when(suiteService.execute(same(req)))
                .thenReturn(
                        new RuntimeTraceRegressionSuiteResult(
                                RuntimeTraceRegressionSuiteOutcome.COMPLETED_ALL_BATCH_RETURNS, summary, List.of(row)));
        mockMvc.perform(
                        post("/api/test/runtime-trace-regression-suite-definitions/{id}/execute", definitionId)
                                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suiteOutcome").value("COMPLETED_ALL_BATCH_RETURNS"));
        InOrder inOrder = inOrder(definitionService, suiteService);
        inOrder.verify(definitionService).materializeToSuiteRequest(eq(definitionId), eq(userId));
        inOrder.verify(suiteService).execute(same(req));
    }

    @Test
    void t2_e1_materializeNotFound_returns404_neverExecute() throws Exception {
        when(definitionService.materializeToSuiteRequest(eq(definitionId), eq(userId)))
                .thenThrow(new NotFoundException("missing"));
        mockMvc.perform(
                        post("/api/test/runtime-trace-regression-suite-definitions/{id}/execute", definitionId)
                                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(content().string(""));
        verify(definitionService, times(1)).materializeToSuiteRequest(eq(definitionId), eq(userId));
        verify(suiteService, never()).execute(any());
    }

    @Test
    void t3_e1_malformedDefinitionId_returns400_neverMaterializeNeverExecute() throws Exception {
        mockMvc.perform(
                        post("/api/test/runtime-trace-regression-suite-definitions/{id}/execute", "not-a-uuid")
                                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(""));
        verify(definitionService, never()).materializeToSuiteRequest(any(), any());
        verify(suiteService, never()).execute(any());
    }

    @Test
    void t4_e1_queryString_returns400_neverMaterializeNeverExecute() throws Exception {
        mockMvc.perform(
                        post("/api/test/runtime-trace-regression-suite-definitions/{id}/execute", definitionId)
                                .queryParam("a", "1")
                                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(""));
        verify(definitionService, never()).materializeToSuiteRequest(any(), any());
        verify(suiteService, never()).execute(any());
    }

    @Test
    void t5_e1_nonEmptyBody_returns400_neverMaterializeNeverExecute() throws Exception {
        mockMvc.perform(
                        post("/api/test/runtime-trace-regression-suite-definitions/{id}/execute", definitionId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(""));
        verify(definitionService, never()).materializeToSuiteRequest(any(), any());
        verify(suiteService, never()).execute(any());
    }

    @Test
    void t6_e1_executeReturnsNotAttempted_returns400_materializeAndExecuteOnce() throws Exception {
        RuntimeTraceRegressionSuiteRequest req =
                new RuntimeTraceRegressionSuiteRequest(userId, List.of(new RuntimeTraceRegressionSuiteEntry.ByTraceIds(List.of())));
        when(definitionService.materializeToSuiteRequest(eq(definitionId), eq(userId))).thenReturn(req);
        when(suiteService.execute(same(req)))
                .thenReturn(
                        new RuntimeTraceRegressionSuiteResult(
                                RuntimeTraceRegressionSuiteOutcome.NOT_ATTEMPTED,
                                new RuntimeTraceRegressionSuiteSummary(0, 0, 0, 0, 0),
                                List.of()));
        mockMvc.perform(
                        post("/api/test/runtime-trace-regression-suite-definitions/{id}/execute", definitionId)
                                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(""));
        verify(definitionService, times(1)).materializeToSuiteRequest(eq(definitionId), eq(userId));
        verify(suiteService, times(1)).execute(same(req));
    }

    @Test
    void t7_e1_executeReturnsEmptySuite_returns200() throws Exception {
        RuntimeTraceRegressionSuiteRequest req =
                new RuntimeTraceRegressionSuiteRequest(userId, List.of(new RuntimeTraceRegressionSuiteEntry.ByTraceIds(List.of())));
        when(definitionService.materializeToSuiteRequest(eq(definitionId), eq(userId))).thenReturn(req);
        when(suiteService.execute(same(req)))
                .thenReturn(
                        new RuntimeTraceRegressionSuiteResult(
                                RuntimeTraceRegressionSuiteOutcome.EMPTY_SUITE,
                                new RuntimeTraceRegressionSuiteSummary(0, 0, 0, 0, 0),
                                List.of()));
        mockMvc.perform(
                        post("/api/test/runtime-trace-regression-suite-definitions/{id}/execute", definitionId)
                                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suiteOutcome").value("EMPTY_SUITE"));
    }

    @Test
    void t8_e1_executeReturnsCompletedWithEntryExecutionFailures_returns200() throws Exception {
        RuntimeTraceRegressionSuiteRequest req =
                new RuntimeTraceRegressionSuiteRequest(userId, List.of(new RuntimeTraceRegressionSuiteEntry.ByTraceIds(List.of())));
        when(definitionService.materializeToSuiteRequest(eq(definitionId), eq(userId))).thenReturn(req);
        when(suiteService.execute(same(req)))
                .thenReturn(
                        new RuntimeTraceRegressionSuiteResult(
                                RuntimeTraceRegressionSuiteOutcome.COMPLETED_WITH_ENTRY_EXECUTION_FAILURES,
                                new RuntimeTraceRegressionSuiteSummary(1, 1, 0, 1, 0),
                                List.of()));
        mockMvc.perform(
                        post("/api/test/runtime-trace-regression-suite-definitions/{id}/execute", definitionId)
                                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suiteOutcome").value("COMPLETED_WITH_ENTRY_EXECUTION_FAILURES"));
    }

    @Test
    void t9_e2_happyPath_inOrderSameReq() throws Exception {
        RuntimeTraceRegressionSuiteRequest req =
                new RuntimeTraceRegressionSuiteRequest(
                        userId,
                        List.of(
                                new RuntimeTraceRegressionSuiteEntry.ByConversation(
                                        conversationId, Optional.empty(), Optional.empty(), Optional.empty())));
        var row =
                new RuntimeTraceRegressionSuiteBatchReturnedEntryResult(
                        0,
                        RuntimeTraceRegressionSuiteEntryKind.BY_CONVERSATION,
                        "echo",
                        RuntimeTraceReplayComparisonBatchOutcome.COMPLETED_ALL_EXACT_MATCH,
                        1,
                        1,
                        1);
        var summary = new RuntimeTraceRegressionSuiteSummary(1, 1, 1, 0, 0);
        when(definitionService.materializeToSuiteRequest(eq(definitionId), eq(userId))).thenReturn(req);
        when(suiteService.execute(same(req)))
                .thenReturn(
                        new RuntimeTraceRegressionSuiteResult(
                                RuntimeTraceRegressionSuiteOutcome.COMPLETED_ALL_BATCH_RETURNS, summary, List.of(row)));
        mockMvc.perform(
                        post(
                                        "/api/test/conversations/{cid}/runtime-trace-regression-suite-definitions/{id}/execute",
                                        conversationId,
                                        definitionId)
                                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suiteOutcome").value("COMPLETED_ALL_BATCH_RETURNS"));
        InOrder inOrder = inOrder(definitionService, suiteService);
        inOrder.verify(definitionService).materializeToSuiteRequest(eq(definitionId), eq(userId));
        inOrder.verify(suiteService).execute(same(req));
    }

    @Test
    void t10_e2_onlyByTraceIds_entries_returns404_materializeOnceNeverExecute() throws Exception {
        RuntimeTraceRegressionSuiteRequest req =
                new RuntimeTraceRegressionSuiteRequest(
                        userId,
                        List.of(new RuntimeTraceRegressionSuiteEntry.ByTraceIds(List.of(UUID.randomUUID()))));
        when(definitionService.materializeToSuiteRequest(eq(definitionId), eq(userId))).thenReturn(req);
        mockMvc.perform(
                        post(
                                        "/api/test/conversations/{cid}/runtime-trace-regression-suite-definitions/{id}/execute",
                                        conversationId,
                                        definitionId)
                                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(content().string(""));
        verify(definitionService, times(1)).materializeToSuiteRequest(eq(definitionId), eq(userId));
        verify(suiteService, never()).execute(any());
    }

    @Test
    void t11_e2_twoByConversation_oneMismatchesPath_returns404_materializeOnceNeverExecute() throws Exception {
        UUID other = UUID.randomUUID();
        RuntimeTraceRegressionSuiteRequest req =
                new RuntimeTraceRegressionSuiteRequest(
                        userId,
                        List.of(
                                new RuntimeTraceRegressionSuiteEntry.ByConversation(
                                        conversationId, Optional.empty(), Optional.empty(), Optional.empty()),
                                new RuntimeTraceRegressionSuiteEntry.ByConversation(
                                        other, Optional.empty(), Optional.empty(), Optional.empty())));
        when(definitionService.materializeToSuiteRequest(eq(definitionId), eq(userId))).thenReturn(req);
        mockMvc.perform(
                        post(
                                        "/api/test/conversations/{cid}/runtime-trace-regression-suite-definitions/{id}/execute",
                                        conversationId,
                                        definitionId)
                                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(content().string(""));
        verify(definitionService, times(1)).materializeToSuiteRequest(eq(definitionId), eq(userId));
        verify(suiteService, never()).execute(any());
    }

    @Test
    void t12_e2_malformedConversationId_returns400_neverMaterializeNeverExecute() throws Exception {
        mockMvc.perform(
                        post(
                                        "/api/test/conversations/{cid}/runtime-trace-regression-suite-definitions/{id}/execute",
                                        "bad-uuid",
                                        definitionId)
                                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(""));
        verify(definitionService, never()).materializeToSuiteRequest(any(), any());
        verify(suiteService, never()).execute(any());
    }

    @Test
    void t13_e1_success_neverCallsMutatingDefinitionMethods() throws Exception {
        RuntimeTraceRegressionSuiteRequest req =
                new RuntimeTraceRegressionSuiteRequest(userId, List.of(new RuntimeTraceRegressionSuiteEntry.ByTraceIds(List.of())));
        when(definitionService.materializeToSuiteRequest(eq(definitionId), eq(userId))).thenReturn(req);
        when(suiteService.execute(same(req)))
                .thenReturn(
                        new RuntimeTraceRegressionSuiteResult(
                                RuntimeTraceRegressionSuiteOutcome.COMPLETED_ALL_BATCH_RETURNS,
                                new RuntimeTraceRegressionSuiteSummary(1, 1, 1, 0, 0),
                                List.of()));
        mockMvc.perform(
                        post("/api/test/runtime-trace-regression-suite-definitions/{id}/execute", definitionId)
                                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        verify(definitionService, never()).create(any(), any());
        verify(definitionService, never()).update(any(), any(), any());
        verify(definitionService, never()).delete(any(), any());
        verify(definitionService, never()).listSummariesForUser(any());
        verify(definitionService, never()).loadByIdForUser(any(), any());
    }

    @Test
    void t14_e2_queryString_returns400_neverMaterializeNeverExecute() throws Exception {
        mockMvc.perform(
                        post(
                                        "/api/test/conversations/{cid}/runtime-trace-regression-suite-definitions/{id}/execute",
                                        conversationId,
                                        definitionId)
                                .queryParam("a", "1")
                                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(""));
        verify(definitionService, never()).materializeToSuiteRequest(any(), any());
        verify(suiteService, never()).execute(any());
    }

    @Test
    void t15_e2_success_neverCallsMutatingDefinitionMethods() throws Exception {
        RuntimeTraceRegressionSuiteRequest req =
                new RuntimeTraceRegressionSuiteRequest(
                        userId,
                        List.of(
                                new RuntimeTraceRegressionSuiteEntry.ByConversation(
                                        conversationId, Optional.empty(), Optional.empty(), Optional.empty())));
        when(definitionService.materializeToSuiteRequest(eq(definitionId), eq(userId))).thenReturn(req);
        when(suiteService.execute(same(req)))
                .thenReturn(
                        new RuntimeTraceRegressionSuiteResult(
                                RuntimeTraceRegressionSuiteOutcome.COMPLETED_ALL_BATCH_RETURNS,
                                new RuntimeTraceRegressionSuiteSummary(1, 1, 1, 0, 0),
                                List.of()));
        mockMvc.perform(
                        post(
                                        "/api/test/conversations/{cid}/runtime-trace-regression-suite-definitions/{id}/execute",
                                        conversationId,
                                        definitionId)
                                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        verify(definitionService, never()).create(any(), any());
        verify(definitionService, never()).update(any(), any(), any());
        verify(definitionService, never()).delete(any(), any());
        verify(definitionService, never()).listSummariesForUser(any());
        verify(definitionService, never()).loadByIdForUser(any(), any());
    }
}
