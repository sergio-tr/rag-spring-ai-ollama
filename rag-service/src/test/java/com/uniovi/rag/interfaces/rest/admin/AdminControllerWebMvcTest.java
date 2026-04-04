package com.uniovi.rag.interfaces.rest.admin;

import com.uniovi.rag.configuration.RagApiPathProperties;
import com.uniovi.rag.interfaces.rest.support.RagWebMvcTestApplication;
import com.uniovi.rag.infrastructure.llm.ollama.OllamaModelProvisioningService;
import com.uniovi.rag.security.RagPrincipal;
import com.uniovi.rag.service.admin.AdminSystemDefaultsService;
import com.uniovi.rag.service.admin.AllowlistAdminService;
import com.uniovi.rag.service.async.AsyncTaskService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminController.class)
@ContextConfiguration(classes = RagWebMvcTestApplication.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(AdminController.class)
class AdminControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AllowlistAdminService allowlistAdminService;

    @MockitoBean
    private AdminSystemDefaultsService adminSystemDefaultsService;

    @MockitoBean
    private OllamaModelProvisioningService ollamaModelProvisioningService;

    @MockitoBean
    private AsyncTaskService asyncTaskService;

    @MockitoBean
    private RagApiPathProperties apiPathProperties;

    private UUID userId;

    @BeforeEach
    void setAdmin() {
        when(apiPathProperties.getProductBasePath()).thenReturn("/api/v5");
        userId = UUID.randomUUID();
        RagPrincipal principal = new RagPrincipal(userId, "admin@test", "ADMIN");
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                principal,
                                null,
                                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void pullModel_sync_ok() throws Exception {
        doNothing().when(ollamaModelProvisioningService).ensureModelPresent(eq("m1"));

        mockMvc.perform(
                        post("/api/admin/ollama/pull?sync=true")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"model\":\"m1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.model").value("m1"));

        verify(ollamaModelProvisioningService).ensureModelPresent("m1");
    }

    @Test
    void pullModel_async_returnsAccepted() throws Exception {
        UUID job = UUID.randomUUID();
        when(asyncTaskService.submitOllamaPull(eq(userId), eq("m2"))).thenReturn(job);

        mockMvc.perform(
                        post("/api/admin/ollama/pull?sync=false")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"model\":\"m2\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").value(job.toString()));

        verify(asyncTaskService).submitOllamaPull(userId, "m2");
    }
}
