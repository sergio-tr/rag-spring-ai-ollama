package com.uniovi.rag.interfaces.rest;

import static com.uniovi.rag.testsupport.RagApiTestPaths.path;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.uniovi.rag.application.service.chat.ChatRuntimeStateService;
import com.uniovi.rag.interfaces.rest.dto.ChatPresetSummaryDto;
import com.uniovi.rag.interfaces.rest.dto.ChatRuntimeStateDto;
import com.uniovi.rag.interfaces.rest.dto.ChatRuntimeValidationDto;
import com.uniovi.rag.interfaces.rest.dto.EffectiveRetrievalParametersDto;
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

@WebMvcTest(controllers = ChatRuntimeStateController.class)
@ContextConfiguration(classes = RagWebMvcTestApplication.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ChatRuntimeStateController.class)
class ChatRuntimeStateControllerWebMvcTest {

    private static final String USE_RETRIEVAL = "useRetrieval";
    private static final String LLM_MODEL = "llama3.1";
    private static final String CLASSIFIER_MODEL_ID = "classifier-v2";

    @Autowired private MockMvc mockMvc;

    @MockitoBean private ChatRuntimeStateService chatRuntimeStateService;

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
    void runtimeStateReturnsEffectiveConfigAndConversationModelPinsForPrincipal() throws Exception {
        UUID conversationId = UUID.randomUUID();
        UUID presetId = UUID.randomUUID();
        when(chatRuntimeStateService.getRuntimeState(eq(userId), eq(conversationId)))
                .thenReturn(
                        new ChatRuntimeStateDto(
                                conversationId,
                                null,
                                presetId,
                                new ChatPresetSummaryDto("DEFAULT", null, "Recommended", true, true, null, null),
                                Map.of(USE_RETRIEVAL, false),
                                Map.of(
                                        USE_RETRIEVAL,
                                        true,
                                        "llmModel",
                                        LLM_MODEL,
                                        "classifierModelId",
                                        CLASSIFIER_MODEL_ID),
                                LLM_MODEL,
                                CLASSIFIER_MODEL_ID,
                                true,
                                "CUSTOM",
                                Map.of(USE_RETRIEVAL, true),
                                List.of(USE_RETRIEVAL),
                                true,
                                new ChatRuntimeValidationDto(true, true, List.of(), List.of()),
                                true,
                                List.of(),
                                List.of(),
                                "ChunkDenseRagWorkflow",
                                null,
                                false,
                                null,
                                null,
                                List.of(),
                                null,
                                new EffectiveRetrievalParametersDto(
                                        8, 0.25, "USER_DEFAULTS", "USER_DEFAULTS")));

        mockMvc.perform(get(path("/conversations/{conversationId}/runtime-state"), conversationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationId").value(conversationId.toString()))
                .andExpect(jsonPath("$.effectivePresetId").value(presetId.toString()))
                .andExpect(jsonPath("$.effectiveConfig." + USE_RETRIEVAL).value(true))
                .andExpect(jsonPath("$.conversationLlmModel").value(LLM_MODEL))
                .andExpect(jsonPath("$.conversationClassifierModelId").value(CLASSIFIER_MODEL_ID))
                .andExpect(jsonPath("$.conversationModelsPinned").value(true))
                .andExpect(jsonPath("$.configurationMode").value("CUSTOM"))
                .andExpect(jsonPath("$.manualOverrideKeys[0]").value(USE_RETRIEVAL))
                .andExpect(jsonPath("$.isCustom").value(true));

        verify(chatRuntimeStateService).getRuntimeState(userId, conversationId);
    }
}
