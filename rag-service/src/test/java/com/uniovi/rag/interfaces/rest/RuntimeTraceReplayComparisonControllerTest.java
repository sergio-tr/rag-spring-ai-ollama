package com.uniovi.rag.interfaces.rest;

import com.uniovi.rag.application.service.runtime.tracecomparison.RuntimeTraceReplayComparisonService;
import com.uniovi.rag.domain.runtime.tracecomparison.RuntimeTraceReplayAnswerComparisonStatus;
import com.uniovi.rag.domain.runtime.tracecomparison.RuntimeTraceReplayComparisonOutcome;
import com.uniovi.rag.domain.runtime.tracecomparison.RuntimeTraceReplayComparisonReplayEcho;
import com.uniovi.rag.domain.runtime.tracecomparison.RuntimeTraceReplayComparisonResult;
import com.uniovi.rag.domain.runtime.tracereplay.RuntimeTraceReplayMode;
import com.uniovi.rag.domain.runtime.tracereplay.RuntimeTraceReplayOutcome;
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

@WebMvcTest(controllers = RuntimeTraceReplayComparisonController.class)
@ContextConfiguration(classes = RagWebMvcTestApplication.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(RuntimeTraceReplayComparisonController.class)
@ActiveProfiles("test")
class RuntimeTraceReplayComparisonControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RuntimeTraceReplayComparisonService runtimeTraceReplayComparisonService;

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
        when(runtimeTraceReplayComparisonService.compare(any())).thenReturn(sampleOkResult());
        mockMvc.perform(get("/api/v5/runtime-traces/{traceId}/replay-comparison", traceId).queryParam("x", "1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void query_string_on_message_path_returns_400() throws Exception {
        when(runtimeTraceReplayComparisonService.compare(any())).thenReturn(sampleOkResult());
        mockMvc.perform(
                        get(
                                        "/api/v5/conversations/{cid}/messages/{mid}/runtime-trace/replay-comparison",
                                        conversationId,
                                        messageId)
                                .queryParam("x", "1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void original_not_found_returns_404() throws Exception {
        when(runtimeTraceReplayComparisonService.compare(any())).thenReturn(notFoundResult());
        mockMvc.perform(get("/api/v5/runtime-traces/{traceId}/replay-comparison", traceId))
                .andExpect(status().isNotFound());
    }

    @Test
    void replay_unsupported_returns_200_with_outcome_in_body() throws Exception {
        when(runtimeTraceReplayComparisonService.compare(any())).thenReturn(replayUnsupportedResult());
        mockMvc.perform(get("/api/v5/runtime-traces/{traceId}/replay-comparison", traceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comparisonOutcome").value("REPLAY_UNSUPPORTED"))
                .andExpect(jsonPath("$.originalRouteKind").value("DIRECT_WORKFLOW_ROUTE"));
    }

    private static RuntimeTraceReplayComparisonResult sampleOkResult() {
        UUID tid = UUID.randomUUID();
        return new RuntimeTraceReplayComparisonResult(
                UUID.randomUUID(),
                UUID.randomUUID(),
                tid,
                UUID.randomUUID(),
                UUID.randomUUID(),
                RuntimeTraceReplayMode.BY_TRACE_ID,
                new RuntimeTraceReplayComparisonReplayEcho(Optional.of(tid), Optional.empty(), Optional.empty()),
                RuntimeTraceReplayComparisonOutcome.COMPARISON_SUCCEEDED_EXACT_MATCH,
                RuntimeTraceReplayOutcome.REPLAY_SUCCEEDED,
                RuntimeTraceReplayAnswerComparisonStatus.NOT_COMPARABLE_ORIGINAL_ABSENT,
                true,
                "ok",
                List.of(),
                "DIRECT_WORKFLOW_ROUTE",
                "DIRECT_WORKFLOW_ROUTE",
                "DirectLlmWorkflow",
                "DirectLlmWorkflow");
    }

    private static RuntimeTraceReplayComparisonResult notFoundResult() {
        return new RuntimeTraceReplayComparisonResult(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                null,
                RuntimeTraceReplayMode.BY_TRACE_ID,
                new RuntimeTraceReplayComparisonReplayEcho(Optional.empty(), Optional.empty(), Optional.empty()),
                RuntimeTraceReplayComparisonOutcome.ORIGINAL_TRACE_NOT_FOUND_OR_INACCESSIBLE,
                RuntimeTraceReplayOutcome.NOT_ATTEMPTED,
                RuntimeTraceReplayAnswerComparisonStatus.NOT_COMPARABLE_ORIGINAL_ABSENT,
                false,
                "nf",
                List.of(),
                "",
                "",
                "",
                "");
    }

    private static RuntimeTraceReplayComparisonResult replayUnsupportedResult() {
        UUID tid = UUID.randomUUID();
        return new RuntimeTraceReplayComparisonResult(
                UUID.randomUUID(),
                UUID.randomUUID(),
                tid,
                UUID.randomUUID(),
                UUID.randomUUID(),
                RuntimeTraceReplayMode.BY_TRACE_ID,
                new RuntimeTraceReplayComparisonReplayEcho(Optional.of(tid), Optional.empty(), Optional.empty()),
                RuntimeTraceReplayComparisonOutcome.REPLAY_UNSUPPORTED,
                RuntimeTraceReplayOutcome.UNSUPPORTED_ROUTE_FAMILY,
                RuntimeTraceReplayAnswerComparisonStatus.REPLAY_ABSENT,
                false,
                "uns",
                List.of(),
                "DIRECT_WORKFLOW_ROUTE",
                "",
                "DirectLlmWorkflow",
                "");
    }
}
