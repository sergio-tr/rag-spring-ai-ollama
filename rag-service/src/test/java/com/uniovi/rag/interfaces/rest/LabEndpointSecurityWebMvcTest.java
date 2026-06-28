package com.uniovi.rag.interfaces.rest;

import com.uniovi.rag.application.port.ClassifierLabPort;
import com.uniovi.rag.application.evaluation.workbook.EvaluationReferenceBundleLoader;
import com.uniovi.rag.application.evaluation.workbook.ReferenceBundleSnapshot;
import com.uniovi.rag.application.service.evaluation.LabExperimentalPresetCatalogService;
import com.uniovi.rag.configuration.SecurityConfiguration;
import com.uniovi.rag.interfaces.rest.support.ApiErrorController;
import com.uniovi.rag.interfaces.rest.support.ApiGlobalExceptionHandler;
import com.uniovi.rag.interfaces.rest.support.RagApiExceptionHandler;
import com.uniovi.rag.security.JwtAuthenticationFilter;
import com.uniovi.rag.security.JwtService;
import com.uniovi.rag.application.service.async.AsyncTaskService;
import com.uniovi.rag.application.service.classifier.ClassifierModelRegistryService;
import com.uniovi.rag.testsupport.webmvc.RagWebMvcTestApplication;
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

import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = { LabController.class, ApiErrorController.class })
@ContextConfiguration(classes = RagWebMvcTestApplication.class)
@AutoConfigureMockMvc(addFilters = true)
@Import({
        LabController.class,
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
class LabEndpointSecurityWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private ClassifierLabPort classifierLabClient;

    @MockitoBean
    private AsyncTaskService asyncTaskService;

    @MockitoBean
    private EvaluationReferenceBundleLoader referenceBundleLoader;

    @MockitoBean
    private LabExperimentalPresetCatalogService experimentalPresetCatalogService;

    @MockitoBean
    private ClassifierModelRegistryService classifierModelRegistryService;

    @Test
    void labStatus_withoutToken_returnsJson401() throws Exception {
        mockMvc.perform(get("/api/v5/lab/status").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.path").value("/api/v5/lab/status"));
    }

    @Test
    void labStatus_withUserToken_returns200() throws Exception {
        when(referenceBundleLoader.getSnapshot()).thenReturn(ReferenceBundleSnapshot.classpathMissing());
        when(classifierLabClient.isConfigured()).thenReturn(false);

        String token = jwtService.createAccessToken(UUID.randomUUID(), "u@test", "USER");
        mockMvc.perform(get("/api/v5/lab/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.referenceBundleAvailable").exists())
                .andExpect(jsonPath("$.datasets").exists())
                .andExpect(jsonPath("$.evaluations").exists());
    }
}

