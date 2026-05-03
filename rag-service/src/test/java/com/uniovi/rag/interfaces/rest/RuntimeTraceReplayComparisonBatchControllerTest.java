package com.uniovi.rag.interfaces.rest;


import com.uniovi.rag.application.service.runtime.tracecomparisonbatch.RuntimeTraceReplayComparisonBatchService;
import com.uniovi.rag.configuration.TraceComparisonBatchRestJacksonConfiguration;
import com.uniovi.rag.domain.runtime.tracecomparison.RuntimeTraceReplayComparisonOutcome;
import com.uniovi.rag.domain.runtime.tracecomparisonbatch.RuntimeTraceReplayComparisonBatchItemResult;
import com.uniovi.rag.domain.runtime.tracecomparisonbatch.RuntimeTraceReplayComparisonBatchOutcome;
import com.uniovi.rag.domain.runtime.tracecomparisonbatch.RuntimeTraceReplayComparisonBatchRequest;
import com.uniovi.rag.domain.runtime.tracecomparisonbatch.RuntimeTraceReplayComparisonBatchResult;
import com.uniovi.rag.domain.runtime.tracecomparisonbatch.RuntimeTraceReplayComparisonBatchSelection;
import com.uniovi.rag.domain.runtime.tracecomparisonbatch.RuntimeTraceReplayComparisonBatchSummary;
import com.uniovi.rag.domain.runtime.tracereplay.RuntimeTraceReplayOutcome;
import com.uniovi.rag.security.RagPrincipal;
import com.uniovi.rag.testsupport.webmvc.RagWebMvcTestApplication;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import org.springframework.web.bind.annotation.PostMapping;

import static com.uniovi.rag.testsupport.RagApiTestPaths.path;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = RuntimeTraceReplayComparisonBatchController.class)
@ContextConfiguration(classes = RagWebMvcTestApplication.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({RuntimeTraceReplayComparisonBatchController.class, TraceComparisonBatchRestJacksonConfiguration.class})
@ActiveProfiles("test")
class RuntimeTraceReplayComparisonBatchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RuntimeTraceReplayComparisonBatchService batchService;

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
                Arrays.stream(RuntimeTraceReplayComparisonBatchController.class.getDeclaredMethods())
                        .filter(m -> m.isAnnotationPresent(PostMapping.class))
                        .count();
        assertThat(n).isEqualTo(2);
    }

    @Test
    void query_string_on_trace_ids_route_returns_400() throws Exception {
        when(batchService.execute(any())).thenReturn(emptySelection(0, 0));
        mockMvc.perform(
                        post(path("/runtime-traces/replay-comparisons/batch"))
                                .queryParam("x", "1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"traceIds\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void query_string_on_conversation_route_returns_400() throws Exception {
        when(batchService.execute(any())).thenReturn(emptySelection(0, 0));
        mockMvc.perform(
                        post(
                                        path("/conversations/{cid}/runtime-traces/replay-comparisons/batch"),
                                        conversationId)
                                .queryParam("x", "1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void trace_ids_route_unknown_json_field_returns_400() throws Exception {
        mockMvc.perform(
                        post(path("/runtime-traces/replay-comparisons/batch"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"traceIds\":[],\"extra\":1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void conversation_route_unknown_json_field_returns_400() throws Exception {
        mockMvc.perform(
                        post(
                                        path("/conversations/{cid}/runtime-traces/replay-comparisons/batch"),
                                        conversationId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"workflowName\":\"x\",\"oops\":1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void trace_ids_route_wrong_traceIds_type_returns_400() throws Exception {
        mockMvc.perform(
                        post(path("/runtime-traces/replay-comparisons/batch"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"traceIds\":\"nope\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void trace_ids_route_missing_traceIds_key_returns_400() throws Exception {
        mockMvc.perform(
                        post(path("/runtime-traces/replay-comparisons/batch"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void trace_ids_route_null_body_returns_400() throws Exception {
        mockMvc.perform(post(path("/runtime-traces/replay-comparisons/batch")).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    void trace_ids_route_more_than_50_ids_returns_400() throws Exception {
        StringBuilder sb = new StringBuilder("{\"traceIds\":[");
        for (int i = 0; i < 51; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('"').append(UUID.randomUUID()).append('"');
        }
        sb.append("]}");
        mockMvc.perform(
                        post(path("/runtime-traces/replay-comparisons/batch"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(sb.toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void trace_ids_route_null_element_returns_400() throws Exception {
        mockMvc.perform(
                        post(path("/runtime-traces/replay-comparisons/batch"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"traceIds\":[null]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void trace_ids_route_empty_list_returns_200_empty_selection() throws Exception {
        when(batchService.execute(any())).thenReturn(emptySelection(0, 0));
        mockMvc.perform(
                        post(path("/runtime-traces/replay-comparisons/batch"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"traceIds\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batchOutcome").value("EMPTY_SELECTION"))
                .andExpect(jsonPath("$.requestedCount").value(0))
                .andExpect(jsonPath("$.selectedCount").value(0));
    }

    @Test
    void not_attempted_from_service_returns_400() throws Exception {
        when(batchService.execute(any())).thenReturn(notAttempted(3, 0));
        mockMvc.perform(
                        post(path("/runtime-traces/replay-comparisons/batch"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"traceIds\":[\"" + UUID.randomUUID() + "\"]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void trace_ids_route_inaccessible_trace_still_200_with_item_outcomes() throws Exception {
        UUID tid = UUID.randomUUID();
        var item =
                new RuntimeTraceReplayComparisonBatchItemResult(
                        0,
                        tid,
                        Optional.empty(),
                        RuntimeTraceReplayComparisonOutcome.ORIGINAL_TRACE_NOT_FOUND_OR_INACCESSIBLE.name(),
                        RuntimeTraceReplayOutcome.NOT_ATTEMPTED.name(),
                        false,
                        "UNAVAILABLE",
                        "x",
                        0,
                        0,
                        false,
                        false);
        var sum = new RuntimeTraceReplayComparisonBatchSummary(1, 1, 1, 0, 0, 0, 0, 0, 0, 1);
        var result =
                new RuntimeTraceReplayComparisonBatchResult(
                        RuntimeTraceReplayComparisonBatchOutcome.COMPLETED_MIXED, sum, List.of(item), 1, 1);
        when(batchService.execute(any())).thenReturn(result);
        mockMvc.perform(
                        post(path("/runtime-traces/replay-comparisons/batch"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"traceIds\":[\"" + tid + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].requestedTraceId").value(tid.toString()))
                .andExpect(jsonPath("$.items[0].mismatches").doesNotExist())
                .andExpect(jsonPath("$.items[0].originalTraceLoaded").value(false))
                .andExpect(jsonPath("$.items[0].resolvedOriginalTraceId").doesNotExist());
    }

    @Test
    void conversation_not_found_returns_404() throws Exception {
        when(batchService.execute(any())).thenThrow(new NotFoundException("conversation not found"));
        mockMvc.perform(
                        post(
                                        path("/conversations/{cid}/runtime-traces/replay-comparisons/batch"),
                                        conversationId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void conversation_empty_selection_returns_200() throws Exception {
        when(batchService.execute(any())).thenReturn(emptySelection(0, 0));
        mockMvc.perform(
                        post(
                                        path("/conversations/{cid}/runtime-traces/replay-comparisons/batch"),
                                        conversationId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batchOutcome").value("EMPTY_SELECTION"));
    }

    @Test
    void malformed_conversation_uuid_in_path_returns_400() throws Exception {
        mockMvc.perform(
                        post(path("/conversations/not-a-uuid/runtime-traces/replay-comparisons/batch"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void requested_count_by_trace_ids_equals_raw_list_size() throws Exception {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        var sum = new RuntimeTraceReplayComparisonBatchSummary(2, 2, 2, 2, 0, 0, 0, 0, 0, 0);
        var row =
                new RuntimeTraceReplayComparisonBatchItemResult(
                        0, a, Optional.of(a), "COMPARISON_SUCCEEDED_EXACT_MATCH", "REPLAY_SUCCEEDED", true, "MATCHED", "", 0, 0, false, false);
        var result =
                new RuntimeTraceReplayComparisonBatchResult(
                        RuntimeTraceReplayComparisonBatchOutcome.COMPLETED_ALL_EXACT_MATCH,
                        sum,
                        List.of(row),
                        2,
                        2);
        when(batchService.execute(any())).thenReturn(result);
        mockMvc.perform(
                        post(path("/runtime-traces/replay-comparisons/batch"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"traceIds\":[\"" + a + "\",\"" + b + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestedCount").value(2))
                .andExpect(jsonPath("$.selectedCount").value(2));
    }

    @Test
    void requested_count_by_conversation_equals_selected_count() throws Exception {
        var sum = new RuntimeTraceReplayComparisonBatchSummary(3, 3, 3, 3, 0, 0, 0, 0, 0, 0);
        var result =
                new RuntimeTraceReplayComparisonBatchResult(
                        RuntimeTraceReplayComparisonBatchOutcome.COMPLETED_ALL_EXACT_MATCH, sum, List.of(), 3, 3);
        when(batchService.execute(any())).thenReturn(result);
        mockMvc.perform(
                        post(
                                        path("/conversations/{cid}/runtime-traces/replay-comparisons/batch"),
                                        conversationId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestedCount").value(3))
                .andExpect(jsonPath("$.selectedCount").value(3));
    }

    @Test
    void conversation_route_trims_blank_workflow_to_no_filter() throws Exception {
        when(batchService.execute(any())).thenReturn(emptySelection(0, 0));
        mockMvc.perform(
                        post(
                                        path("/conversations/{cid}/runtime-traces/replay-comparisons/batch"),
                                        conversationId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"workflowName\":\"   \"}"))
                .andExpect(status().isOk());
        ArgumentCaptor<RuntimeTraceReplayComparisonBatchRequest> cap =
                ArgumentCaptor.forClass(RuntimeTraceReplayComparisonBatchRequest.class);
        verify(batchService).execute(cap.capture());
        var sel = (RuntimeTraceReplayComparisonBatchSelection.ByConversation) cap.getValue().selection();
        assertThat(sel.workflowName()).isEmpty();
    }

    private static RuntimeTraceReplayComparisonBatchResult emptySelection(int requested, int selected) {
        var summary =
                new RuntimeTraceReplayComparisonBatchSummary(requested, selected, 0, 0, 0, 0, 0, 0, 0, 0);
        return new RuntimeTraceReplayComparisonBatchResult(
                RuntimeTraceReplayComparisonBatchOutcome.EMPTY_SELECTION, summary, List.of(), requested, selected);
    }

    private static RuntimeTraceReplayComparisonBatchResult notAttempted(int requested, int selected) {
        var summary =
                new RuntimeTraceReplayComparisonBatchSummary(requested, selected, 0, 0, 0, 0, 0, 0, 0, 0);
        return new RuntimeTraceReplayComparisonBatchResult(
                RuntimeTraceReplayComparisonBatchOutcome.NOT_ATTEMPTED, summary, List.of(), requested, selected);
    }
}
