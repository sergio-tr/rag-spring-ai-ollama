package com.uniovi.rag.security;

import com.uniovi.rag.configuration.RagApiPathProperties;
import com.uniovi.rag.configuration.SecurityConfiguration;
import com.uniovi.rag.interfaces.rest.admin.AdminController;
import com.uniovi.rag.interfaces.rest.me.MeSummaryController;
import com.uniovi.rag.interfaces.rest.support.ApiErrorController;
import com.uniovi.rag.interfaces.rest.support.ApiGlobalExceptionHandler;
import com.uniovi.rag.interfaces.rest.support.RagApiExceptionHandler;
import com.uniovi.rag.application.service.me.MeSummaryApplicationService;
import com.uniovi.rag.infrastructure.llm.ollama.OllamaModelProvisioningService;
import com.uniovi.rag.service.admin.AdminSystemDefaultsService;
import com.uniovi.rag.service.admin.AllowlistAdminService;
import com.uniovi.rag.service.async.AsyncTaskService;
import com.uniovi.rag.testsupport.webmvc.RagWebMvcTestApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = { MeSummaryController.class, AdminController.class, ApiErrorController.class })
@ContextConfiguration(classes = RagWebMvcTestApplication.class)
@AutoConfigureMockMvc(addFilters = true)
@Import({
        SecurityConfiguration.class,
        JwtService.class,
        JwtAuthenticationFilter.class,
        ApiGlobalExceptionHandler.class,
        RagApiExceptionHandler.class
})
@TestPropertySource(properties = {
        "rag.jwt.secret=test-secret-key-for-jwt-signing-must-be-long-enough-32",
        "spring.mvc.throw-exception-if-no-handler-found=true",
        "spring.web.resources.add-mappings=false"
})
class SecurityErrorContractWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private MeSummaryApplicationService meSummaryApplicationService;

    // AdminController dependencies (should not be invoked when forbidden/unauthorized).
    @MockitoBean
    private AllowlistAdminService allowlistAdminService;

    @MockitoBean
    private AdminSystemDefaultsService adminSystemDefaultsService;

    @MockitoBean
    private OllamaModelProvisioningService ollamaModelProvisioningService;

    @MockitoBean
    private AsyncTaskService asyncTaskService;

    @Test
    void protectedRoute_withoutToken_returnsJson401() throws Exception {
        mockMvc.perform(get("/api/v5/me/summary").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.path").value("/api/v5/me/summary"));
    }

    @Test
    void adminRoute_withUserToken_returnsJson403() throws Exception {
        String token = jwtService.createAccessToken(UUID.randomUUID(), "u@test", "USER");
        mockMvc.perform(get("/api/admin/health")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.path").value("/api/admin/health"));
    }

    // 404 JSON contract is unit-tested at the controller level (ApiErrorControllerUnitTest) to avoid
    // slice ambiguity around BasicErrorController routing in @WebMvcTest.
}

