package com.uniovi.rag.interfaces.rest;


import static com.uniovi.rag.testsupport.RagApiTestPaths.path;
import com.uniovi.rag.application.service.ChatMessageApplicationService;
import com.uniovi.rag.application.service.evaluation.LabJobEventService;
import com.uniovi.rag.application.service.evaluation.LabJobLifecycleService;
import com.uniovi.rag.application.service.evaluation.LabJobSseHub;
import com.uniovi.rag.interfaces.rest.dto.ActiveLabJobDto;
import com.uniovi.rag.interfaces.rest.dto.AsyncTaskStatusDto;
import com.uniovi.rag.interfaces.rest.dto.LabJobEventDto;
import com.uniovi.rag.configuration.LabAsyncConfiguration;
import com.uniovi.rag.testsupport.webmvc.RagWebMvcTestApplication;
import com.uniovi.rag.security.RagPrincipal;
import com.uniovi.rag.application.service.async.AsyncTaskService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@WebMvcTest(controllers = LabJobController.class)
@ContextConfiguration(classes = RagWebMvcTestApplication.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({LabJobController.class, LabAsyncConfiguration.class})
class LabJobControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AsyncTaskService asyncTaskService;

    @MockitoBean
    private ChatMessageApplicationService chatMessageApplicationService;

    @MockitoBean
    private LabJobLifecycleService labJobLifecycleService;

    @MockitoBean
    private LabJobEventService labJobEventService;

    @MockitoBean
    private LabJobSseHub labJobSseHub;

    private UUID userId;
    private UUID taskId;

    @BeforeEach
    void setUser() {
        userId = UUID.randomUUID();
        taskId = UUID.randomUUID();
        RagPrincipal principal = new RagPrincipal(userId, "u@test", "USER");
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getJob_returnsDto() throws Exception {
        when(asyncTaskService.getStatus(eq(taskId), eq(userId)))
                .thenReturn(
                        new AsyncTaskStatusDto(
                                taskId,
                                "EVAL_RAG",
                                "RUNNING",
                                "x",
                                null,
                                null,
                                false,
                                Instant.parse("2025-01-01T00:00:00Z"),
                                Instant.parse("2025-01-01T00:00:01Z"),
                                Instant.parse("2025-01-01T00:00:02Z"),
                                null,
                                null));

        mockMvc.perform(get(path("/lab/jobs/{id}"), taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(taskId.toString()))
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.taskType").value("EVAL_RAG"));
    }

    @Test
    void active_returnsActiveJobs() throws Exception {
        when(labJobLifecycleService.listActiveJobs(eq(userId)))
                .thenReturn(List.of(new ActiveLabJobDto(
                        taskId,
                        "RAG_PRESET_END_TO_END",
                        UUID.randomUUID(),
                        null,
                        null,
                        "RUNNING",
                        "x",
                        Instant.parse("2026-01-01T00:00:00Z"),
                        Instant.parse("2026-01-01T00:00:01Z"),
                        "/lab/jobs/" + taskId,
                        "/lab/jobs/" + taskId + "/events",
                        true
                )));

        mockMvc.perform(get(path("/lab/jobs/active")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].jobId").value(taskId.toString()))
                .andExpect(jsonPath("$[0].status").value("RUNNING"));
    }

    @Test
    void eventsHistory_returnsPersistedEventsSinceCursor() throws Exception {
        when(asyncTaskService.getStatus(eq(taskId), eq(userId)))
                .thenReturn(
                        new AsyncTaskStatusDto(
                                taskId,
                                "EVAL_RAG",
                                "RUNNING",
                                "x",
                                null,
                                null,
                                false,
                                Instant.parse("2025-01-01T00:00:00Z"),
                                Instant.parse("2025-01-01T00:00:01Z"),
                                Instant.parse("2025-01-01T00:00:02Z"),
                                null,
                                null));
        when(labJobEventService.listEvents(eq(taskId), eq(userId), eq(1L)))
                .thenReturn(List.of(new LabJobEventDto(
                        2L,
                        taskId,
                        "PROGRESS",
                        "RUNNING",
                        "step 2",
                        "step 2",
                        Instant.parse("2025-01-01T00:00:03Z"),
                        Map.of())));

        mockMvc.perform(get(path("/lab/jobs/{id}/events"), taskId).param("stream", "false").param("since", "1"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$[0].eventId").value(2))
                .andExpect(jsonPath("$[0].type").value("PROGRESS"));
    }

    @Test
    void cancel_delegatesToLifecycleService() throws Exception {
        doNothing().when(labJobLifecycleService).cancelEvaluationJob(eq(userId), eq(taskId));

        mockMvc.perform(post(path("/lab/jobs/{id}/cancel"), taskId)).andExpect(status().isNoContent());
    }

    @Test
    void cancel_notFound_fallsBackToChatCancel() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "missing"))
                .when(labJobLifecycleService)
                .cancelEvaluationJob(eq(userId), eq(taskId));
        doNothing().when(chatMessageApplicationService).cancelChatTask(eq(userId), eq(taskId));

        mockMvc.perform(post(path("/lab/jobs/{id}/cancel"), taskId)).andExpect(status().isNoContent());
    }

    @Test
    void eventsStream_opensSse() throws Exception {
        when(asyncTaskService.getStatus(eq(taskId), eq(userId)))
                .thenReturn(
                        new AsyncTaskStatusDto(
                                taskId,
                                "EVAL_RAG",
                                "SUCCEEDED",
                                "done",
                                Map.of("ok", true),
                                null,
                                true,
                                Instant.parse("2025-01-01T00:00:00Z"),
                                Instant.parse("2025-01-01T00:00:01Z"),
                                Instant.parse("2025-01-01T00:00:02Z"),
                                Instant.parse("2025-01-01T00:00:05Z"),
                                null));
        when(labJobEventService.buildSnapshot(eq(taskId), eq(userId)))
                .thenReturn(new LabJobEventDto(
                        0L,
                        taskId,
                        "SNAPSHOT",
                        "SUCCEEDED",
                        "done",
                        "snapshot",
                        Instant.parse("2025-01-01T00:00:05Z"),
                        Map.of("snapshot", true)));
        when(labJobEventService.listEvents(eq(taskId), eq(userId), isNull()))
                .thenReturn(List.of());

        mockMvc.perform(get(path("/lab/jobs/{id}/events"), taskId))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/event-stream"));
    }
}
