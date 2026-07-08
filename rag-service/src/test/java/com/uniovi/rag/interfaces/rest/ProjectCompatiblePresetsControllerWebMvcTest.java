package com.uniovi.rag.interfaces.rest;

import static com.uniovi.rag.testsupport.RagApiTestPaths.path;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.uniovi.rag.application.service.chat.ProjectCompatiblePresetsService;
import com.uniovi.rag.configuration.RagApiPathProperties;
import com.uniovi.rag.domain.chat.CompatibleProductPreset;
import com.uniovi.rag.domain.chat.PresetIndexCompatibility;
import com.uniovi.rag.domain.chat.ProjectCompatiblePresetsCatalog;
import com.uniovi.rag.domain.chat.RuntimePresetIndexRequirements;
import com.uniovi.rag.domain.chat.RuntimeSnapshotCapabilities;
import com.uniovi.rag.domain.preset.UserRagPreset;
import com.uniovi.rag.security.RagPrincipal;
import com.uniovi.rag.testsupport.webmvc.RagWebMvcTestApplication;
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

@WebMvcTest(controllers = ProjectCompatiblePresetsController.class)
@ContextConfiguration(classes = RagWebMvcTestApplication.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ProjectCompatiblePresetsController.class)
class ProjectCompatiblePresetsControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProjectCompatiblePresetsService projectCompatiblePresetsService;

    @MockitoBean
    private RagApiPathProperties apiPathProperties;

    private UUID userId;
    private UUID projectId;

    @BeforeEach
    void setUser() {
        userId = UUID.randomUUID();
        projectId = UUID.randomUUID();
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
    void list_returnsProjectScopedCompatiblePresets() throws Exception {
        UUID presetId = UUID.randomUUID();
        Instant now = Instant.now();
        UserRagPreset preset =
                new UserRagPreset(
                        presetId,
                        "Demo_Best",
                        null,
                        List.of("demo"),
                        Map.of("useRetrieval", true),
                        true,
                        now,
                        now);
        CompatibleProductPreset item =
                new CompatibleProductPreset(
                        preset,
                        new RuntimePresetIndexRequirements("CHUNK_LEVEL", false),
                        new PresetIndexCompatibility(true, null, null, null, true));
        when(projectCompatiblePresetsService.list(eq(userId), eq(projectId), isNull()))
                .thenReturn(
                        new ProjectCompatiblePresetsCatalog(
                                projectId,
                                "mxbai-embed-large",
                                true,
                                2L,
                                new RuntimeSnapshotCapabilities("CHUNK_LEVEL", false, "mxbai-embed-large", 400, 40),
                                List.of(item),
                                List.of()));

        mockMvc.perform(get(path("/projects/{projectId}/compatible-presets"), projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").value(projectId.toString()))
                .andExpect(jsonPath("$.hasActiveIndex").value(true))
                .andExpect(jsonPath("$.readyDocumentCount").value(2))
                .andExpect(jsonPath("$.productPresets[0].preset.id").value(presetId.toString()))
                .andExpect(jsonPath("$.productPresets[0].compatibility.selectable").value(true));
    }

    @Test
    void list_forwardsEmbeddingModelIdQueryParam() throws Exception {
        when(projectCompatiblePresetsService.list(eq(userId), eq(projectId), eq("nomic-embed-text")))
                .thenReturn(
                        new ProjectCompatiblePresetsCatalog(
                                projectId,
                                "nomic-embed-text",
                                false,
                                0L,
                                null,
                                List.of(),
                                List.of()));

        mockMvc.perform(
                        get(path("/projects/{projectId}/compatible-presets"), projectId)
                                .queryParam("embeddingModelId", "nomic-embed-text"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.effectiveEmbeddingModelId").value("nomic-embed-text"));
    }
}
