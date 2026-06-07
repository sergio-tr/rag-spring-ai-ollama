package com.uniovi.rag.interfaces.rest;

import com.uniovi.rag.application.service.ChatMessageApplicationService;
import com.uniovi.rag.application.service.async.AsyncTaskService;
import com.uniovi.rag.application.service.evaluation.LabJobEventService;
import com.uniovi.rag.application.service.evaluation.LabJobLifecycleService;
import com.uniovi.rag.application.service.evaluation.LabJobSseHub;
import com.uniovi.rag.configuration.LabAsyncConfiguration;
import com.uniovi.rag.configuration.SecurityConfiguration;
import com.uniovi.rag.interfaces.rest.support.ApiErrorController;
import com.uniovi.rag.interfaces.rest.support.ApiGlobalExceptionHandler;
import com.uniovi.rag.interfaces.rest.support.RagApiExceptionHandler;
import com.uniovi.rag.security.JwtAuthenticationFilter;
import com.uniovi.rag.security.JwtService;
import com.uniovi.rag.testsupport.webmvc.RagWebMvcTestApplication;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static com.uniovi.rag.testsupport.RagApiTestPaths.path;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {LabJobController.class, ApiErrorController.class})
@ContextConfiguration(classes = RagWebMvcTestApplication.class)
@AutoConfigureMockMvc(addFilters = true)
@Import({
        LabJobController.class,
        LabAsyncConfiguration.class,
        SecurityConfiguration.class,
        JwtService.class,
        JwtAuthenticationFilter.class,
        ApiGlobalExceptionHandler.class,
        RagApiExceptionHandler.class
})
@TestPropertySource(
        properties = {
            "rag.jwt.secret=test-secret-key-for-jwt-signing-must-be-long-enough-32",
            "rag.api.product-base-path=/api/v5",
            "spring.mvc.throw-exception-if-no-handler-found=true",
            "spring.web.resources.add-mappings=false"
        })
class LabJobEndpointSecurityWebMvcTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private AsyncTaskService asyncTaskService;
    @MockitoBean private ChatMessageApplicationService chatMessageApplicationService;
    @MockitoBean private LabJobLifecycleService labJobLifecycleService;
    @MockitoBean private LabJobEventService labJobEventService;
    @MockitoBean private LabJobSseHub labJobSseHub;

    @Test
    void eventsStream_withoutToken_returnsJson401() throws Exception {
        UUID taskId = UUID.randomUUID();
        mockMvc.perform(get(path("/lab/jobs/{id}/events"), taskId).accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.path").value("/api/v5/lab/jobs/" + taskId + "/events"));
    }
}
