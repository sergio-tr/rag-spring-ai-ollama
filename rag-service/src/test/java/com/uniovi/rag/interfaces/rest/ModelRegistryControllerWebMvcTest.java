package com.uniovi.rag.interfaces.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.uniovi.rag.configuration.RagApiPathProperties;
import com.uniovi.rag.domain.AllowedModelType;
import com.uniovi.rag.domain.product.ModelRegistryAvailabilityStatus;
import com.uniovi.rag.interfaces.rest.dto.modelregistry.ModelRegistryItemDto;
import com.uniovi.rag.interfaces.rest.dto.modelregistry.ModelRegistryResponseDto;
import com.uniovi.rag.interfaces.rest.support.ApiErrorController;
import com.uniovi.rag.interfaces.rest.support.ApiGlobalExceptionHandler;
import com.uniovi.rag.interfaces.rest.support.RagApiExceptionHandler;
import com.uniovi.rag.security.RagPrincipal;
import com.uniovi.rag.application.service.async.AsyncTaskService;
import com.uniovi.rag.application.service.model.ModelRegistryService;
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
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

@WebMvcTest(controllers = { ModelRegistryController.class, ApiErrorController.class })
@ContextConfiguration(classes = RagWebMvcTestApplication.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({
        ModelRegistryController.class,
        ApiGlobalExceptionHandler.class,
        RagApiExceptionHandler.class
})
class ModelRegistryControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ModelRegistryService modelRegistryService;

    @MockitoBean
    private AsyncTaskService asyncTaskService;

    @MockitoBean
    private RagApiPathProperties apiPathProperties;

    private UUID userId;

    @BeforeEach
    void authUser() {
        userId = UUID.randomUUID();
        RagPrincipal principal = new RagPrincipal(userId, "u@test", "USER");
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        when(apiPathProperties.getProductBasePath()).thenReturn("/api/v5");
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getRegistry_returnsSnapshot() throws Exception {
        ModelRegistryResponseDto body = new ModelRegistryResponseDto(
                true,
                null,
                List.of(new ModelRegistryItemDto(
                        "gemma3:4b", AllowedModelType.LLM, ModelRegistryAvailabilityStatus.AVAILABLE, null, null)),
                List.of());
        when(modelRegistryService.snapshot()).thenReturn(body);

        mockMvc.perform(get("/api/v5/model-registry").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ollamaReachable").value(true))
                .andExpect(jsonPath("$.llmModels[0].modelId").value("gemma3:4b"));
    }

    @Test
    void postPull_whenAllowed_returns202() throws Exception {
        UUID job = UUID.randomUUID();
        doNothing().when(modelRegistryService).assertPullAllowed("gemma3:4b");
        when(asyncTaskService.submitOllamaPull(eq(userId), eq("gemma3:4b"))).thenReturn(job);

        mockMvc.perform(post("/api/v5/model-registry/pull")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"modelId\":\"gemma3:4b\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").value(job.toString()));

        verify(asyncTaskService).submitOllamaPull(userId, "gemma3:4b");
    }

    @Test
    void postPull_whenNotInRegistry_returns400() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "MODEL_NOT_IN_PRODUCT_REGISTRY"))
                .when(modelRegistryService)
                .assertPullAllowed(any());

        mockMvc.perform(post("/api/v5/model-registry/pull")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"modelId\":\"phi3\"}"))
                .andExpect(status().isBadRequest());
    }
}
