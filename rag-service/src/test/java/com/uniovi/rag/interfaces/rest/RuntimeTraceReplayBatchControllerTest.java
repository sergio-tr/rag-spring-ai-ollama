package com.uniovi.rag.interfaces.rest;


import com.uniovi.rag.application.service.runtime.tracereplaybatch.RuntimeTraceReplayBatchService;
import com.uniovi.rag.configuration.ReplayBatchRestJacksonConfiguration;
import com.uniovi.rag.domain.runtime.tracereplay.RuntimeTraceReplayOutcome;
import com.uniovi.rag.domain.runtime.tracereplaybatch.RuntimeTraceReplayBatchItemOutcome;
import com.uniovi.rag.domain.runtime.tracereplaybatch.RuntimeTraceReplayBatchItemResult;
import com.uniovi.rag.domain.runtime.tracereplaybatch.RuntimeTraceReplayBatchOutcome;
import com.uniovi.rag.domain.runtime.tracereplaybatch.RuntimeTraceReplayBatchRequest;
import com.uniovi.rag.domain.runtime.tracereplaybatch.RuntimeTraceReplayBatchResult;
import com.uniovi.rag.domain.runtime.tracereplaybatch.RuntimeTraceReplayBatchSelection;
import com.uniovi.rag.domain.runtime.tracereplaybatch.RuntimeTraceReplayBatchSummary;
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

@WebMvcTest(controllers = RuntimeTraceReplayBatchController.class)
@ContextConfiguration(classes = RagWebMvcTestApplication.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({RuntimeTraceReplayBatchController.class, ReplayBatchRestJacksonConfiguration.class})
@ActiveProfiles("test")
class RuntimeTraceReplayBatchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RuntimeTraceReplayBatchService batchService;

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
                Arrays.stream(RuntimeTraceReplayBatchController.class.getDeclaredMethods())
                        .filter(m -> m.isAnnotationPresent(PostMapping.class))
                        .count();
        assertThat(n).isEqualTo(2);
    }

    @Test
    void query_string_on_trace_ids_route_returns_400() throws Exception {
        when(batchService.execute(any())).thenReturn(emptySelectionByTraceIds(0, 0));
        mockMvc.perform(
                        post(path("/runtime-traces/replays/batch"))
                                .queryParam("x", "1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"traceIds\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void query_string_on_conversation_route_returns_400() throws Exception {
        when(batchService.execute(any())).thenReturn(conversationEmptySelection());
        mockMvc.perform(
                        post(path("/conversations/{cid}/runtime-traces/replays/batch"), conversationId)
                                .queryParam("x", "1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void trace_ids_route_unknown_json_field_returns_400() throws Exception {
        mockMvc.perform(
                        post(path("/runtime-traces/replays/batch"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"traceIds\":[],\"extra\":1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void conversation_route_unknown_json_field_returns_400() throws Exception {
        mockMvc.perform(
                        post(path("/conversations/{cid}/runtime-traces/replays/batch"), conversationId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"workflowName\":\"x\",\"oops\":1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void trace_ids_route_wrong_traceIds_type_returns_400() throws Exception {
        mockMvc.perform(
                        post(path("/runtime-traces/replays/batch"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"traceIds\":\"nope\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void trace_ids_route_missing_traceIds_key_returns_400() throws Exception {
        mockMvc.perform(
                        post(path("/runtime-traces/replays/batch"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void trace_ids_route_null_body_returns_400() throws Exception {
        mockMvc.perform(post(path("/runtime-traces/replays/batch")).contentType(MediaType.APPLICATION_JSON))
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
                        post(path("/runtime-traces/replays/batch"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(sb.toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void trace_ids_route_null_element_returns_400() throws Exception {
        mockMvc.perform(
                        post(path("/runtime-traces/replays/batch"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"traceIds\":[null]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void trace_ids_route_null_traceIds_value_returns_400() throws Exception {
        mockMvc.perform(
                        post(path("/runtime-traces/replays/batch"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"traceIds\":null}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void trace_ids_route_empty_list_returns_200_empty_selection() throws Exception {
        when(batchService.execute(any())).thenReturn(emptySelectionByTraceIds(0, 0));
        mockMvc.perform(
                        post(path("/runtime-traces/replays/batch"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"traceIds\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batchOutcome").value("EMPTY_SELECTION"))
                .andExpect(jsonPath("$.requestedCount").value(0))
                .andExpect(jsonPath("$.selectedCount").value(0))
                .andExpect(jsonPath("$.processedCount").value(0));
    }

    @Test
    void not_attempted_from_service_returns_400() throws Exception {
        when(batchService.execute(any())).thenReturn(notAttempted(3, 0));
        mockMvc.perform(
                        post(path("/runtime-traces/replays/batch"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"traceIds\":[\"" + UUID.randomUUID() + "\"]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void trace_ids_route_inaccessible_trace_still_200_with_item_outcomes() throws Exception {
        UUID tid = UUID.randomUUID();
        var item =
                new RuntimeTraceReplayBatchItemResult(
                        tid,
                        Optional.empty(),
                        0,
                        RuntimeTraceReplayBatchItemOutcome.ORIGINAL_TRACE_NOT_FOUND_OR_INACCESSIBLE,
                        Optional.empty(),
                        "",
                        "",
                        "",
                        "",
                        0,
                        false,
                        false,
                        false,
                        false);
        var sum = new RuntimeTraceReplayBatchSummary(1, 0, 0, 0, 1, 0);
        var result =
                new RuntimeTraceReplayBatchResult(1, 1, RuntimeTraceReplayBatchOutcome.COMPLETED_MIXED, sum, List.of(item));
        when(batchService.execute(any())).thenReturn(result);
        mockMvc.perform(
                        post(path("/runtime-traces/replays/batch"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"traceIds\":[\"" + tid + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].requestedTraceId").value(tid.toString()))
                .andExpect(jsonPath("$.items[0].mismatches").doesNotExist())
                .andExpect(jsonPath("$.items[0].originalTraceLoaded").value(false))
                .andExpect(jsonPath("$.items[0].resolvedOriginalTraceId").doesNotExist());
    }

    @Test
    void completed_batch_returns_200() throws Exception {
        UUID tid = UUID.randomUUID();
        var item =
                new RuntimeTraceReplayBatchItemResult(
                        tid,
                        Optional.of(tid),
                        0,
                        RuntimeTraceReplayBatchItemOutcome.REPLAY_SUCCEEDED,
                        Optional.of(RuntimeTraceReplayOutcome.REPLAY_SUCCEEDED.name()),
                        "a",
                        "",
                        "",
                        "",
                        0,
                        true,
                        true,
                        false,
                        false);
        var sum = new RuntimeTraceReplayBatchSummary(1, 1, 0, 0, 0, 0);
        var result =
                new RuntimeTraceReplayBatchResult(
                        1, 1, RuntimeTraceReplayBatchOutcome.COMPLETED_ALL_REPLAY_SUCCEEDED, sum, List.of(item));
        when(batchService.execute(any())).thenReturn(result);
        mockMvc.perform(
                        post(path("/runtime-traces/replays/batch"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"traceIds\":[\"" + tid + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batchMode").value("BY_TRACE_IDS"))
                .andExpect(jsonPath("$.batchOutcome").value("COMPLETED_ALL_REPLAY_SUCCEEDED"))
                .andExpect(jsonPath("$.processedCount").value(1));
    }

    @Test
    void conversation_not_found_returns_404() throws Exception {
        when(batchService.execute(any())).thenThrow(new NotFoundException("conversation not found"));
        mockMvc.perform(
                        post(path("/conversations/{cid}/runtime-traces/replays/batch"), conversationId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void conversation_empty_selection_returns_200() throws Exception {
        when(batchService.execute(any())).thenReturn(conversationEmptySelection());
        mockMvc.perform(
                        post(path("/conversations/{cid}/runtime-traces/replays/batch"), conversationId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batchOutcome").value("EMPTY_SELECTION"))
                .andExpect(jsonPath("$.requestedCount").value(1))
                .andExpect(jsonPath("$.selectedCount").value(0));
    }

    @Test
    void malformed_conversation_uuid_in_path_returns_400() throws Exception {
        mockMvc.perform(
                        post(path("/conversations/not-a-uuid/runtime-traces/replays/batch"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void conversation_route_trims_blank_workflow_to_no_filter() throws Exception {
        when(batchService.execute(any())).thenReturn(conversationEmptySelection());
        mockMvc.perform(
                        post(path("/conversations/{cid}/runtime-traces/replays/batch"), conversationId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"workflowName\":\"   \"}"))
                .andExpect(status().isOk());
        ArgumentCaptor<RuntimeTraceReplayBatchRequest> cap = ArgumentCaptor.forClass(RuntimeTraceReplayBatchRequest.class);
        verify(batchService).execute(cap.capture());
        var sel = (RuntimeTraceReplayBatchSelection.ByConversation) cap.getValue().selection();
        assertThat(sel.workflowName()).isEmpty();
    }

    private static RuntimeTraceReplayBatchResult emptySelectionByTraceIds(int requested, int selected) {
        var summary = RuntimeTraceReplayBatchSummary.zeros();
        return new RuntimeTraceReplayBatchResult(
                requested, selected, RuntimeTraceReplayBatchOutcome.EMPTY_SELECTION, summary, List.of());
    }

    private static RuntimeTraceReplayBatchResult conversationEmptySelection() {
        var summary = RuntimeTraceReplayBatchSummary.zeros();
        return new RuntimeTraceReplayBatchResult(1, 0, RuntimeTraceReplayBatchOutcome.EMPTY_SELECTION, summary, List.of());
    }

    private static RuntimeTraceReplayBatchResult notAttempted(int requested, int selected) {
        var summary = RuntimeTraceReplayBatchSummary.zeros();
        return new RuntimeTraceReplayBatchResult(
                requested, selected, RuntimeTraceReplayBatchOutcome.NOT_ATTEMPTED, summary, List.of());
    }
}
