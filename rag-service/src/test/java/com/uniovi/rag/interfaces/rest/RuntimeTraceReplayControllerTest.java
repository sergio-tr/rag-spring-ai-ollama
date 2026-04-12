package com.uniovi.rag.interfaces.rest;


import static com.uniovi.rag.testsupport.RagApiTestPaths.path;
import com.uniovi.rag.application.service.runtime.tracereplay.RuntimeTraceReplayService;
import com.uniovi.rag.domain.runtime.engine.ExecutionTrace;
import com.uniovi.rag.domain.runtime.tracereplay.RuntimeTraceReplayOutcome;
import com.uniovi.rag.domain.runtime.tracereplay.RuntimeTraceReplayResult;
import com.uniovi.rag.security.RagPrincipal;
import com.uniovi.rag.testsupport.webmvc.RagWebMvcTestApplication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = RuntimeTraceReplayController.class)
@ContextConfiguration(classes = RagWebMvcTestApplication.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(RuntimeTraceReplayController.class)
@ActiveProfiles("test")
class RuntimeTraceReplayControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RuntimeTraceReplayService runtimeTraceReplayService;

    private UUID userId;
    private UUID traceId;
    private UUID conversationId;
    private UUID messageId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        traceId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        messageId = UUID.randomUUID();

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
    void query_string_on_trace_path_returns_400() throws Exception {
        when(runtimeTraceReplayService.replay(any())).thenReturn(successResult());
        mockMvc.perform(get(path("/runtime-traces/{traceId}/replay"), traceId).queryParam("x", "1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void query_string_on_message_path_returns_400() throws Exception {
        when(runtimeTraceReplayService.replay(any())).thenReturn(successResult());
        mockMvc.perform(
                        get(
                                        path("/conversations/{cid}/messages/{mid}/runtime-trace/replay"),
                                        conversationId,
                                        messageId)
                                .queryParam("x", "1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void not_found_exception_from_replay_returns_404_on_trace_path() throws Exception {
        when(runtimeTraceReplayService.replay(any())).thenThrow(new NotFoundException("trace not found"));
        mockMvc.perform(get(path("/runtime-traces/{traceId}/replay"), traceId))
                .andExpect(status().isNotFound());
    }

    @Test
    void not_found_exception_from_replay_returns_404_on_message_path() throws Exception {
        when(runtimeTraceReplayService.replay(any())).thenThrow(new NotFoundException("trace not found"));
        mockMvc.perform(
                        get(
                                path("/conversations/{cid}/messages/{mid}/runtime-trace/replay"),
                                conversationId,
                                messageId))
                .andExpect(status().isNotFound());
    }

    @Test
    void replay_succeeded_returns_200_with_dto_on_trace_path() throws Exception {
        when(runtimeTraceReplayService.replay(any())).thenReturn(successResult());
        mockMvc.perform(get(path("/runtime-traces/{traceId}/replay"), traceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.selectorMode").value("BY_TRACE_ID"))
                .andExpect(jsonPath("$.replayOutcome").value("REPLAY_SUCCEEDED"))
                .andExpect(jsonPath("$.originalTraceId").value(traceId.toString()))
                .andExpect(jsonPath("$.conversationId").doesNotExist())
                .andExpect(jsonPath("$.messageId").doesNotExist())
                .andExpect(jsonPath("$.answerText").value("hello"))
                .andExpect(jsonPath("$.transientTraceSummary.stageCount").value(0))
                .andExpect(jsonPath("$.executionTraceJson").doesNotExist())
                .andExpect(jsonPath("$.stagesJson").doesNotExist());
    }

    @Test
    void replay_succeeded_returns_200_on_message_path_with_conversation_and_message_ids() throws Exception {
        when(runtimeTraceReplayService.replay(any())).thenReturn(successResult());
        mockMvc.perform(
                        get(
                                path("/conversations/{cid}/messages/{mid}/runtime-trace/replay"),
                                conversationId,
                                messageId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.selectorMode").value("BY_MESSAGE_ID"))
                .andExpect(jsonPath("$.replayOutcome").value("REPLAY_SUCCEEDED"))
                .andExpect(jsonPath("$.originalTraceId").doesNotExist())
                .andExpect(jsonPath("$.conversationId").value(conversationId.toString()))
                .andExpect(jsonPath("$.messageId").value(messageId.toString()));
    }

    @Test
    void unsupported_outcome_after_trace_load_returns_200_with_outcome_in_body() throws Exception {
        when(runtimeTraceReplayService.replay(any()))
                .thenReturn(RuntimeTraceReplayResult.unsupported(
                        RuntimeTraceReplayOutcome.UNSUPPORTED_ROUTE_FAMILY, Optional.of("detail")));
        mockMvc.perform(get(path("/runtime-traces/{traceId}/replay"), traceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replayOutcome").value("UNSUPPORTED_ROUTE_FAMILY"))
                .andExpect(jsonPath("$.failureDetail").value("detail"));
    }

    @Test
    void malformed_uuid_on_trace_path_returns_400() throws Exception {
        mockMvc.perform(get(path("/runtime-traces/{traceId}/replay"), "not-a-uuid"))
                .andExpect(status().isBadRequest());
    }

    private static RuntimeTraceReplayResult successResult() {
        return RuntimeTraceReplayResult.success("hello", ExecutionTrace.placeholder());
    }
}
