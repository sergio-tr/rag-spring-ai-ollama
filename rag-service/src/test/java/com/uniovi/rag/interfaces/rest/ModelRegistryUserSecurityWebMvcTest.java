package com.uniovi.rag.interfaces.rest;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.uniovi.rag.configuration.SecurityConfiguration;
import com.uniovi.rag.domain.AllowedModelType;
import com.uniovi.rag.domain.product.ModelRegistryAvailabilityStatus;
import com.uniovi.rag.interfaces.rest.dto.modelregistry.ModelRegistryItemDto;
import com.uniovi.rag.interfaces.rest.dto.modelregistry.ModelRegistryResponseDto;
import com.uniovi.rag.interfaces.rest.support.ApiErrorController;
import com.uniovi.rag.interfaces.rest.support.ApiGlobalExceptionHandler;
import com.uniovi.rag.interfaces.rest.support.RagApiExceptionHandler;
import com.uniovi.rag.security.JwtAuthenticationFilter;
import com.uniovi.rag.security.JwtService;
import com.uniovi.rag.service.async.AsyncTaskService;
import com.uniovi.rag.service.model.ModelRegistryService;
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

@WebMvcTest(controllers = { ModelRegistryController.class, ApiErrorController.class })
@ContextConfiguration(classes = RagWebMvcTestApplication.class)
@AutoConfigureMockMvc(addFilters = true)
@Import({
        ModelRegistryController.class,
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
class ModelRegistryUserSecurityWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private ModelRegistryService modelRegistryService;

    @MockitoBean
    private AsyncTaskService asyncTaskService;

    @Test
    void getRegistry_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v5/model-registry").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getRegistry_withUserToken_returns200() throws Exception {
        ModelRegistryResponseDto body = new ModelRegistryResponseDto(
                true,
                null,
                List.of(new ModelRegistryItemDto(
                        "mistral:7b", AllowedModelType.LLM, ModelRegistryAvailabilityStatus.MISSING, "missing", null)),
                List.of());
        when(modelRegistryService.snapshot()).thenReturn(body);

        String token = jwtService.createAccessToken(UUID.randomUUID(), "u@test", "USER");
        mockMvc.perform(get("/api/v5/model-registry")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.llmModels[0].modelId").value("mistral:7b"));
    }
}
