package com.uniovi.rag.interfaces.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.application.service.runtime.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunPersistenceService;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteOutcome;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteSummary;
import com.uniovi.rag.domain.runtime.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunId;
import com.uniovi.rag.domain.runtime.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunSnapshot;
import com.uniovi.rag.domain.runtime.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunSourceType;
import com.uniovi.rag.domain.runtime.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunSummary;
import com.uniovi.rag.security.RagPrincipal;
import com.uniovi.rag.testsupport.webmvc.RagWebMvcTestApplication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

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

@WebMvcTest(controllers = RuntimeTraceRegressionSuiteRunController.class)
@ContextConfiguration(classes = RagWebMvcTestApplication.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(RuntimeTraceRegressionSuiteRunController.class)
@ActiveProfiles("test")
class RuntimeTraceRegressionSuiteRunControllerTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RuntimeTraceRegressionSuiteRunPersistenceService runPersistenceService;

    private UUID userId;
    private UUID runId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
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

    @Test
    void list_noQueryString_emptyService_returns200_emptyRuns() throws Exception {
        when(runPersistenceService.listSummariesForUser(userId)).thenReturn(List.of());
        mockMvc.perform(get("/api/v5/runtime-trace-regression-suite-runs"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"runs\":[]}", true));
        verify(runPersistenceService, times(1)).listSummariesForUser(any());
        verify(runPersistenceService, never()).loadByIdForUser(any(), any());
        verify(runPersistenceService, never()).createRun(any(), any(), any(), any());
    }

    @Test
    void list_noQueryString_twoSummaries_preservesOrder_andSingleRootKey() throws Exception {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        Instant t = Instant.parse("2024-01-01T00:00:00Z");
        List<RuntimeTraceRegressionSuiteRunSummary> rows =
                List.of(
                        new RuntimeTraceRegressionSuiteRunSummary(
                                new RuntimeTraceRegressionSuiteRunId(id1),
                                RuntimeTraceRegressionSuiteRunSourceType.AD_HOC,
                                Optional.empty(),
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
                                Optional.of(UUID.randomUUID()),
                                RuntimeTraceRegressionSuiteOutcome.COMPLETED_WITH_ENTRY_EXECUTION_FAILURES,
                                t,
                                2,
                                2,
                                0,
                                1,
                                0));
        when(runPersistenceService.listSummariesForUser(userId)).thenReturn(rows);
        MvcResult result =
                mockMvc.perform(get("/api/v5/runtime-trace-regression-suite-runs"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.runs.length()").value(2))
                        .andExpect(jsonPath("$.runs[0].id").value(id1.toString()))
                        .andExpect(jsonPath("$.runs[1].id").value(id2.toString()))
                        .andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("hibernateLazyInitializer");
        JsonNode root = OBJECT_MAPPER.readTree(result.getResponse().getContentAsString());
        Set<String> top = new HashSet<>();
        root.fieldNames().forEachRemaining(top::add);
        assertThat(top).containsExactly("runs");
        verify(runPersistenceService, times(1)).listSummariesForUser(any());
        verify(runPersistenceService, never()).loadByIdForUser(any(), any());
    }

    @Test
    void list_queryParam_returns400_emptyBody() throws Exception {
        mockMvc.perform(get("/api/v5/runtime-trace-regression-suite-runs").queryParam("x", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(""));
        verify(runPersistenceService, never()).listSummariesForUser(any());
        verify(runPersistenceService, never()).loadByIdForUser(any(), any());
    }

    @Test
    void detail_validUuid_queryParam_returns400_emptyBody() throws Exception {
        mockMvc.perform(get("/api/v5/runtime-trace-regression-suite-runs/{id}", runId).queryParam("x", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(""));
        verify(runPersistenceService, never()).loadByIdForUser(any(), any());
        verify(runPersistenceService, never()).listSummariesForUser(any());
    }

    @Test
    void detail_validUuid_snapshot_returns200_strictTopLevelKeys() throws Exception {
        RuntimeTraceRegressionSuiteRunSnapshot snap =
                new RuntimeTraceRegressionSuiteRunSnapshot(
                        new RuntimeTraceRegressionSuiteRunId(runId),
                        userId,
                        RuntimeTraceRegressionSuiteRunSourceType.AD_HOC,
                        Optional.empty(),
                        RuntimeTraceRegressionSuiteOutcome.COMPLETED_ALL_BATCH_RETURNS,
                        new RuntimeTraceRegressionSuiteSummary(1, 1, 1, 0, 0),
                        Instant.parse("2024-03-01T12:00:00Z"),
                        List.of());
        when(runPersistenceService.loadByIdForUser(runId, userId)).thenReturn(Optional.of(snap));
        MvcResult result =
                mockMvc.perform(get("/api/v5/runtime-trace-regression-suite-runs/{id}", runId))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.id").value(runId.toString()))
                        .andExpect(jsonPath("$.definitionId").value(nullValue()))
                        .andExpect(jsonPath("$.entries").isArray())
                        .andExpect(jsonPath("$.entries", hasSize(0)))
                        .andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("hibernateLazyInitializer");
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
        verify(runPersistenceService, times(1)).loadByIdForUser(any(), any());
        verify(runPersistenceService, never()).listSummariesForUser(any());
        verify(runPersistenceService, never()).createRun(any(), any(), any(), any());
    }

    static Stream<Arguments> detailEmptyOptionalCases() {
        return Stream.of(Arguments.of(UUID.randomUUID(), "unknown"), Arguments.of(UUID.randomUUID(), "wrong_owner"));
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("detailEmptyOptionalCases")
    void detail_emptyOptional_returns404(UUID id, String ignoredLabel) throws Exception {
        when(runPersistenceService.loadByIdForUser(id, userId)).thenReturn(Optional.empty());
        mockMvc.perform(get("/api/v5/runtime-trace-regression-suite-runs/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(
                        r ->
                                assertThat(r.getResolvedException())
                                        .isInstanceOf(NotFoundException.class)
                                        .hasMessage("run not found"));
        verify(runPersistenceService, times(1)).loadByIdForUser(id, userId);
        verify(runPersistenceService, never()).listSummariesForUser(any());
    }

    @Test
    void detail_malformedUuid_returns400_emptyBody() throws Exception {
        mockMvc.perform(get("/api/v5/runtime-trace-regression-suite-runs/{id}", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(""));
        verify(runPersistenceService, never()).loadByIdForUser(any(), any());
        verify(runPersistenceService, never()).listSummariesForUser(any());
    }
}
