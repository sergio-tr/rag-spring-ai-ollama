package com.uniovi.rag.interfaces.rest;

import com.uniovi.rag.application.service.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteService;
import com.uniovi.rag.configuration.RegressionSuiteRestJacksonConfiguration;
import com.uniovi.rag.domain.runtime.tracecomparisonbatch.RuntimeTraceReplayComparisonBatchOutcome;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteBatchReturnedEntryResult;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteEntryKind;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteOutcome;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteResult;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteSummary;
import com.uniovi.rag.security.RagPrincipal;
import com.uniovi.rag.testsupport.webmvc.RagWebMvcTestApplication;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = RuntimeTraceRegressionSuiteController.class)
@ContextConfiguration(classes = RagWebMvcTestApplication.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({RuntimeTraceRegressionSuiteController.class, RegressionSuiteRestJacksonConfiguration.class})
@ActiveProfiles("test")
class RuntimeTraceRegressionSuiteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RuntimeTraceRegressionSuiteService suiteService;

    private UUID userId;
    private UUID conversationId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
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
    void controller_exposes_exactly_two_post_mappings() {
        long n =
                Arrays.stream(RuntimeTraceRegressionSuiteController.class.getDeclaredMethods())
                        .filter(m -> m.isAnnotationPresent(org.springframework.web.bind.annotation.PostMapping.class))
                        .count();
        assertThat(n).isEqualTo(2);
    }

    @Test
    void explicit_route_query_string_returns_400() throws Exception {
        when(suiteService.execute(any())).thenReturn(emptySuiteResult());
        mockMvc.perform(
                        post("/api/v5/runtime-traces/regression-suite")
                                .queryParam("x", "1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"entries\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void conversation_route_query_string_returns_400() throws Exception {
        when(suiteService.execute(any())).thenReturn(emptySuiteResult());
        mockMvc.perform(
                        post("/api/v5/conversations/{cid}/runtime-traces/regression-suite", conversationId)
                                .queryParam("x", "1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"entries\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void explicit_route_unknown_json_key_returns_400() throws Exception {
        mockMvc.perform(
                        post("/api/v5/runtime-traces/regression-suite")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"entries\":[],\"extra\":1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void conversation_route_unknown_json_key_returns_400() throws Exception {
        mockMvc.perform(
                        post("/api/v5/conversations/{cid}/runtime-traces/regression-suite", conversationId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"entries\":[],\"oops\":1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void explicit_route_entries_length_21_returns_400() throws Exception {
        StringBuilder sb = new StringBuilder("{\"entries\":[");
        for (int i = 0; i < 21; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append("{\"kind\":\"BY_TRACE_IDS\",\"traceIds\":[]}");
        }
        sb.append("]}");
        mockMvc.perform(
                        post("/api/v5/runtime-traces/regression-suite")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(sb.toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void explicit_route_null_element_in_entries_returns_400() throws Exception {
        mockMvc.perform(
                        post("/api/v5/runtime-traces/regression-suite")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"entries\":[null]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void conversation_route_null_element_in_entries_returns_400() throws Exception {
        mockMvc.perform(
                        post("/api/v5/conversations/{cid}/runtime-traces/regression-suite", conversationId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"entries\":[null]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void explicit_route_entry_missing_kind_returns_400() throws Exception {
        mockMvc.perform(
                        post("/api/v5/runtime-traces/regression-suite")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"entries\":[{\"traceIds\":[]}]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void explicit_route_entry_unknown_kind_returns_400() throws Exception {
        mockMvc.perform(
                        post("/api/v5/runtime-traces/regression-suite")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"entries\":[{\"kind\":\"OTHER\",\"traceIds\":[]}]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void explicit_route_by_trace_ids_null_traceIds_returns_400() throws Exception {
        mockMvc.perform(
                        post("/api/v5/runtime-traces/regression-suite")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"entries\":[{\"kind\":\"BY_TRACE_IDS\",\"traceIds\":null}]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void explicit_route_by_trace_ids_null_uuid_element_returns_400() throws Exception {
        mockMvc.perform(
                        post("/api/v5/runtime-traces/regression-suite")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"entries\":[{\"kind\":\"BY_TRACE_IDS\",\"traceIds\":[null]}]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void explicit_route_by_trace_ids_more_than_50_ids_returns_400() throws Exception {
        StringBuilder ids = new StringBuilder();
        for (int i = 0; i < 51; i++) {
            if (i > 0) {
                ids.append(',');
            }
            ids.append('"').append(UUID.randomUUID()).append('"');
        }
        mockMvc.perform(
                        post("/api/v5/runtime-traces/regression-suite")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"entries\":[{\"kind\":\"BY_TRACE_IDS\",\"traceIds\":[" + ids + "]}]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void conversation_route_invalid_path_uuid_returns_400() throws Exception {
        mockMvc.perform(
                        post("/api/v5/conversations/not-a-uuid/runtime-traces/regression-suite")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"entries\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void empty_entries_returns_200_empty_suite() throws Exception {
        when(suiteService.execute(any())).thenReturn(emptySuiteResult());
        mockMvc.perform(
                        post("/api/v5/runtime-traces/regression-suite")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"entries\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suiteOutcome").value("EMPTY_SUITE"))
                .andExpect(jsonPath("$.summary.requestedEntryCount").value(0))
                .andExpect(jsonPath("$.entries").isArray())
                .andExpect(jsonPath("$.entries").isEmpty());
    }

    @Test
    void not_attempted_from_service_returns_400() throws Exception {
        when(suiteService.execute(any())).thenReturn(notAttemptedResult());
        mockMvc.perform(
                        post("/api/v5/runtime-traces/regression-suite")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"entries\":[{\"kind\":\"BY_TRACE_IDS\",\"traceIds\":[\""
                                                + UUID.randomUUID()
                                                + "\"]}]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void completed_returns_bounded_json_without_items() throws Exception {
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
        when(suiteService.execute(any()))
                .thenReturn(
                        new RuntimeTraceRegressionSuiteResult(
                                RuntimeTraceRegressionSuiteOutcome.COMPLETED_ALL_BATCH_RETURNS, summary, List.of(row)));
        mockMvc.perform(
                        post("/api/v5/runtime-traces/regression-suite")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"entries\":[{\"kind\":\"BY_TRACE_IDS\",\"traceIds\":[\""
                                                + UUID.randomUUID()
                                                + "\"]}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suiteOutcome").value("COMPLETED_ALL_BATCH_RETURNS"))
                .andExpect(jsonPath("$.entries[0].entryStatus").value("BATCH_RETURNED"))
                .andExpect(jsonPath("$.entries[0].batchOutcome").value("COMPLETED_ALL_EXACT_MATCH"))
                .andExpect(jsonPath("$.entries[0].items").doesNotExist());
    }

    private static RuntimeTraceRegressionSuiteResult emptySuiteResult() {
        return new RuntimeTraceRegressionSuiteResult(
                RuntimeTraceRegressionSuiteOutcome.EMPTY_SUITE,
                new RuntimeTraceRegressionSuiteSummary(0, 0, 0, 0, 0),
                List.of());
    }

    private static RuntimeTraceRegressionSuiteResult notAttemptedResult() {
        return new RuntimeTraceRegressionSuiteResult(
                RuntimeTraceRegressionSuiteOutcome.NOT_ATTEMPTED,
                new RuntimeTraceRegressionSuiteSummary(0, 0, 0, 0, 0),
                List.of());
    }
}
