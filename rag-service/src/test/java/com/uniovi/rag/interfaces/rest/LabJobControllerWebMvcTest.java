package com.uniovi.rag.interfaces.rest;


import static com.uniovi.rag.testsupport.RagApiTestPaths.path;
import com.uniovi.rag.application.service.ChatMessageApplicationService;
import com.uniovi.rag.application.service.evaluation.LabJobLifecycleService;
import com.uniovi.rag.interfaces.rest.dto.ActiveLabJobDto;
import com.uniovi.rag.interfaces.rest.dto.AsyncTaskStatusDto;
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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
}
