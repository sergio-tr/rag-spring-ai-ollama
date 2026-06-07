package com.uniovi.rag.interfaces.rest;

import static com.uniovi.rag.testsupport.RagApiTestPaths.path;
import static org.hamcrest.Matchers.containsString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.uniovi.rag.application.service.ChatMessageApplicationService;
import com.uniovi.rag.application.service.async.AsyncTaskService;
import com.uniovi.rag.application.service.evaluation.LabJobEventService;
import com.uniovi.rag.application.service.evaluation.LabJobLifecycleService;
import com.uniovi.rag.application.service.evaluation.LabJobSseHub;
import com.uniovi.rag.configuration.LabAsyncConfiguration;
import com.uniovi.rag.interfaces.rest.dto.AsyncTaskStatusDto;
import com.uniovi.rag.interfaces.rest.dto.LabJobEventDto;
import com.uniovi.rag.security.RagPrincipal;
import com.uniovi.rag.testsupport.webmvc.RagWebMvcTestApplication;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@WebMvcTest(controllers = LabJobController.class)
@ContextConfiguration(classes = RagWebMvcTestApplication.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({LabJobController.class, LabAsyncConfiguration.class})
class LabJobControllerSseBodyWebMvcTest {

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
    void eventsStream_emitsSnapshotImmediatelyWithAntiBufferHeaders() throws Exception {
        when(asyncTaskService.getStatus(eq(taskId), eq(userId)))
                .thenReturn(
                        new AsyncTaskStatusDto(
                                taskId,
                                "EVAL_LLM",
                                "QUEUED",
                                "queued",
                                null,
                                null,
                                false,
                                Instant.parse("2025-01-01T00:00:00Z"),
                                Instant.parse("2025-01-01T00:00:00Z"),
                                null,
                                null,
                                null));
        when(labJobEventService.buildSnapshot(eq(taskId), eq(userId)))
                .thenReturn(
                        new LabJobEventDto(
                                0L,
                                taskId,
                                "SNAPSHOT",
                                "ACCEPTED",
                                null,
                                "Live updates connected.",
                                Instant.parse("2025-01-01T00:00:00Z"),
                                Map.of("snapshot", true)));
        when(labJobEventService.listEvents(eq(taskId), eq(userId), isNull())).thenReturn(List.of());

        MvcResult result = mockMvc.perform(get(path("/lab/jobs/{id}/events"), taskId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("text/event-stream")))
                .andExpect(header().string("X-Accel-Buffering", "no"))
                .andExpect(header().string("Cache-Control", containsString("no-cache")))
                .andReturn();

        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).contains("event:job-event");
        assertThat(body).contains("\"type\":\"SNAPSHOT\"");
        assertThat(body).contains("\"status\":\"ACCEPTED\"");
        assertThat(body).contains("Live updates connected.");
    }
}
