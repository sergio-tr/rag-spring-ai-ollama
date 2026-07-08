package com.uniovi.rag.interfaces.rest;

import static com.uniovi.rag.testsupport.RagApiTestPaths.path;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.uniovi.rag.application.service.ChatMessageApplicationService;
import com.uniovi.rag.application.service.async.AsyncTaskService;
import com.uniovi.rag.application.service.evaluation.LabJobEventService;
import com.uniovi.rag.application.service.evaluation.LabJobLifecycleService;
import com.uniovi.rag.application.service.evaluation.LabJobSseHub;
import com.uniovi.rag.configuration.LabAsyncConfiguration;
import com.uniovi.rag.security.RagPrincipal;
import com.uniovi.rag.testsupport.webmvc.RagWebMvcTestApplication;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

@WebMvcTest(controllers = LabJobController.class)
@ContextConfiguration(classes = RagWebMvcTestApplication.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({LabJobController.class, LabAsyncConfiguration.class})
class LabJobCancellationControllerTest {

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
    void cancel_evaluationJob_returnsNoContent() throws Exception {
        doNothing().when(labJobLifecycleService).cancelEvaluationJob(eq(userId), eq(taskId));

        mockMvc.perform(post(path("/lab/jobs/{id}/cancel"), taskId)).andExpect(status().isNoContent());

        verify(labJobLifecycleService).cancelEvaluationJob(userId, taskId);
        verify(chatMessageApplicationService, never()).cancelChatTask(eq(userId), eq(taskId));
    }

    @Test
    void cancel_notFoundEvaluationJob_fallsBackToChatCancel() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"))
                .when(labJobLifecycleService)
                .cancelEvaluationJob(eq(userId), eq(taskId));
        doNothing().when(chatMessageApplicationService).cancelChatTask(eq(userId), eq(taskId));

        mockMvc.perform(post(path("/lab/jobs/{id}/cancel"), taskId)).andExpect(status().isNoContent());

        verify(chatMessageApplicationService).cancelChatTask(userId, taskId);
    }
}
