package com.uniovi.rag.interfaces.rest.admin;

import com.uniovi.rag.configuration.SecurityConfiguration;
import com.uniovi.rag.interfaces.rest.support.ApiErrorController;
import com.uniovi.rag.interfaces.rest.support.ApiGlobalExceptionHandler;
import com.uniovi.rag.interfaces.rest.support.RagApiExceptionHandler;
import com.uniovi.rag.security.JwtAuthenticationFilter;
import com.uniovi.rag.security.JwtService;
import com.uniovi.rag.service.admin.AdminModelsService;
import com.uniovi.rag.service.async.AsyncTaskService;
import com.uniovi.rag.testsupport.webmvc.RagWebMvcTestApplication;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = { AdminModelsController.class, AdminHealthController.class, ApiErrorController.class })
@ContextConfiguration(classes = RagWebMvcTestApplication.class)
@AutoConfigureMockMvc(addFilters = true)
@Import({
        AdminModelsController.class,
        AdminHealthController.class,
        SecurityConfiguration.class,
        JwtService.class,
        JwtAuthenticationFilter.class,
        ApiGlobalExceptionHandler.class,
        RagApiExceptionHandler.class
})
@TestPropertySource(properties = {
        "rag.jwt.secret=test-secret-key-for-jwt-signing-must-be-long-enough-32",
        "rag.api.product-base-path=/api/v5",
        "spring.mvc.throw-exception-if-no-handler-found=true",
        "spring.web.resources.add-mappings=false"
})
class AdminV5ModelsSecurityWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private AdminModelsService adminModelsService;

    @MockitoBean
    private AsyncTaskService asyncTaskService;

    @Test
    void adminHealth_withUserToken_returns403() throws Exception {
        String token = jwtService.createAccessToken(UUID.randomUUID(), "u@test", "USER");
        mockMvc.perform(get("/api/v5/admin/health")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void listModels_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v5/admin/models").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listModels_withUserToken_returns403() throws Exception {
        when(adminModelsService.list()).thenReturn(List.of());
        String token = jwtService.createAccessToken(UUID.randomUUID(), "u@test", "USER");
        mockMvc.perform(get("/api/v5/admin/models")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void listModels_withAdminToken_returns200() throws Exception {
        when(adminModelsService.list()).thenReturn(List.of());
        String token = jwtService.createAccessToken(UUID.randomUUID(), "a@test", "ADMIN");
        mockMvc.perform(get("/api/v5/admin/models")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void pullModel_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/api/v5/admin/models/pull")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"model\":\"m1\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void pullModel_withUserToken_returns403() throws Exception {
        String token = jwtService.createAccessToken(UUID.randomUUID(), "u@test", "USER");
        mockMvc.perform(post("/api/v5/admin/models/pull")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"model\":\"m1\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void pullModel_withAdminToken_returns202() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        when(asyncTaskService.submitOllamaPull(eq(userId), eq("m2"))).thenReturn(jobId);
        String token = jwtService.createAccessToken(userId, "a@test", "ADMIN");

        mockMvc.perform(post("/api/v5/admin/models/pull")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"model\":\"m2\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").value(jobId.toString()))
                .andExpect(jsonPath("$.pollPath").value("/api/v5/lab/jobs/" + jobId));

        verify(asyncTaskService).submitOllamaPull(userId, "m2");
    }
}
