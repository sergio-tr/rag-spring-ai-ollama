package com.uniovi.rag.interfaces.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.application.config.ConfigurationSchemaProvider;
import com.uniovi.rag.application.config.RuntimeConfigResolutionInput;
import com.uniovi.rag.application.service.ResolvedConfigSnapshotApplicationService;
import com.uniovi.rag.application.service.RuntimeConfigResolutionService;
import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.domain.config.capability.CapabilitySet;
import com.uniovi.rag.domain.config.indexing.ReindexImpact;
import com.uniovi.rag.domain.config.prompt.SystemPromptLayers;
import com.uniovi.rag.domain.config.runtime.ConfigProvenance;
import com.uniovi.rag.domain.config.runtime.ResolvedRuntimeConfig;
import com.uniovi.rag.domain.config.validation.CompatibilityResult;
import com.uniovi.rag.domain.config.validation.CompatibilityViolation;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.interfaces.rest.dto.RuntimeConfigPreviewRequest;
import com.uniovi.rag.security.RagPrincipal;
import com.uniovi.rag.application.service.config.UserProjectConfigurationService;
import com.uniovi.rag.testsupport.webmvc.RagWebMvcTestApplication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.uniovi.rag.testsupport.RagApiTestPaths.path;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;

@WebMvcTest(controllers = ConfigController.class)
@ContextConfiguration(classes = RagWebMvcTestApplication.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({ConfigController.class, ConfigurationSchemaProvider.class})
class ConfigControllerPreviewWebMvcTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private UserProjectConfigurationService userProjectConfigurationService;
    @MockitoBean private RuntimeConfigResolutionService runtimeConfigResolutionService;
    @MockitoBean private ResolvedConfigSnapshotApplicationService resolvedConfigSnapshotApplicationService;

    private UUID userId;

    @BeforeEach
    void setUser() {
        userId = UUID.randomUUID();
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
    void preview_whenCompatibilityInvalid_returns400WithFirstErrorMessage() throws Exception {
        ResolvedRuntimeConfig resolved =
                new ResolvedRuntimeConfig(
                        RagConfig.fromFeatureConfiguration(new RagFeatureConfiguration(), 10, 0.7, "a", "b", "c", "simple"),
                        CapabilitySet.fromRagConfig(
                                RagConfig.fromFeatureConfiguration(new RagFeatureConfiguration(), 10, 0.7, "a", "b", "c", "simple")),
                        new CompatibilityResult(
                                List.of(CompatibilityViolation.of("x", "bad config")),
                                List.of(),
                                List.of()),
                        ReindexImpact.none(),
                        SystemPromptLayers.empty(),
                        "sys",
                        new ConfigProvenance(null, null, null, List.of(), null, null),
                        null);

        when(runtimeConfigResolutionService.preview(any(RuntimeConfigResolutionInput.class))).thenReturn(resolved);

        RuntimeConfigPreviewRequest body =
                new RuntimeConfigPreviewRequest(UUID.randomUUID(), null, null, Map.of(), List.of(), null);

        mockMvc.perform(
                        post(path("/config/preview"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(status().reason(containsString("bad config")));

        verify(runtimeConfigResolutionService).preview(any(RuntimeConfigResolutionInput.class));
    }
}

