package com.uniovi.rag.interfaces.rest.me;

import static com.uniovi.rag.testsupport.RagApiTestPaths.path;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.uniovi.rag.application.service.config.ChatPresetDefaults;
import com.uniovi.rag.application.service.config.llm.TaskModelSettingsService;
import com.uniovi.rag.application.service.project.ProjectAccessService;
import com.uniovi.rag.application.service.runtime.config.RuntimeConfigValidationService;
import com.uniovi.rag.infrastructure.persistence.jpa.ConversationEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.ProjectEntity;
import com.uniovi.rag.interfaces.rest.dto.RuntimeConfigValidateResponse;
import com.uniovi.rag.interfaces.rest.dto.RuntimeIndexCompatibilityDto;
import com.uniovi.rag.security.RagPrincipal;
import com.uniovi.rag.testsupport.webmvc.RagWebMvcTestApplication;
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

@WebMvcTest(controllers = MeEffectiveRuntimeController.class)
@ContextConfiguration(classes = RagWebMvcTestApplication.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(MeEffectiveRuntimeController.class)
class MeEffectiveRuntimeControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RuntimeConfigValidationService runtimeConfigValidationService;

    @MockitoBean
    private TaskModelSettingsService taskModelSettingsService;

    @MockitoBean
    private ProjectAccessService projectAccessService;

    @MockitoBean
    private ChatPresetDefaults chatPresetDefaults;

    private UUID userId;
    private UUID projectId;
    private UUID conversationId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        RagPrincipal principal = new RagPrincipal(userId, "user@test.local", "USER");
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));

        ProjectEntity project = mock(ProjectEntity.class);
        when(project.getId()).thenReturn(projectId);
        ConversationEntity conversation = mock(ConversationEntity.class);
        when(conversation.getProject()).thenReturn(project);
        when(conversation.getPreset()).thenReturn(null);
        when(conversation.getRuntimeOverride()).thenReturn(null);
        when(projectAccessService.requireConversationForUser(userId, conversationId)).thenReturn(conversation);
        when(chatPresetDefaults.effectivePresetIdForApi(null))
                .thenReturn(ChatPresetDefaults.DETERMINISTIC_DEFAULT_CHAT_PRESET_ID);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void effectiveRuntime_returnsMergedSummary() throws Exception {
        when(runtimeConfigValidationService.validate(eq(userId), any()))
                .thenReturn(
                        new RuntimeConfigValidateResponse(
                                true,
                                true,
                                Map.of("classifierModelId", "default", "llmModel", "gemma4:12b", "topK", 8, "similarityThreshold", 0.35),
                                List.of(),
                                List.of(),
                                null,
                                new RuntimeIndexCompatibilityDto(
                                        null,
                                        null,
                                        null,
                                        Map.of("embeddingModelId", "nomic-embed-text", "materializationStrategy", "CHUNK_LEVEL"),
                                        true,
                                        null,
                                        null,
                                        true,
                                        "COMPATIBLE"),
                                false));
        when(taskModelSettingsService.getEffectiveForUser(userId, projectId))
                .thenReturn(
                        Map.of(
                                "roles",
                                List.of(
                                        Map.of(
                                                "roleId",
                                                "final_answer",
                                                "modelId",
                                                "gemma4:12b",
                                                "label",
                                                "Final answer"))));

        mockMvc.perform(
                        get(path("/me/llm/effective-runtime"))
                                .param("projectId", projectId.toString())
                                .param("conversationId", conversationId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").value(projectId.toString()))
                .andExpect(jsonPath("$.conversationId").value(conversationId.toString()))
                .andExpect(jsonPath("$.classifierModelId").value("default"))
                .andExpect(jsonPath("$.snapshotEmbeddingModelId").value("nomic-embed-text"))
                .andExpect(jsonPath("$.effectiveConfig.llmModel").value("gemma4:12b"))
                .andExpect(jsonPath("$.taskRoles[0].modelId").value("gemma4:12b"))
                .andExpect(jsonPath("$.retrievalTopK").value(8))
                .andExpect(jsonPath("$.retrievalSimilarityThreshold").value(0.35))
                .andExpect(jsonPath("$.materializationStrategy").value("CHUNK_LEVEL"));
    }
}
