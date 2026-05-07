package com.uniovi.rag.application.service.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.uniovi.rag.application.service.evaluation.LabExperimentalPresetCatalogService;
import com.uniovi.rag.application.service.runtime.config.RuntimeConfigValidationService;
import com.uniovi.rag.infrastructure.persistence.jpa.ConversationEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.ProjectEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.RagPresetEntity;
import com.uniovi.rag.interfaces.rest.dto.RuntimeConfigValidateResponse;
import com.uniovi.rag.interfaces.rest.dto.RuntimeIndexCompatibilityDto;
import com.uniovi.rag.service.config.ChatPresetDefaults;
import com.uniovi.rag.service.project.ProjectAccessService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChatRuntimeStateServiceTest {

    private ProjectAccessService projectAccessService;
    private ChatPresetDefaults chatPresetDefaults;
    private LabExperimentalPresetCatalogService experimentalPresetCatalogService;
    private RuntimeConfigValidationService runtimeConfigValidationService;

    private ChatRuntimeStateService sut;

    @BeforeEach
    void setup() {
        projectAccessService = mock(ProjectAccessService.class);
        chatPresetDefaults = mock(ChatPresetDefaults.class);
        experimentalPresetCatalogService = mock(LabExperimentalPresetCatalogService.class);
        runtimeConfigValidationService = mock(RuntimeConfigValidationService.class);
        sut =
                new ChatRuntimeStateService(
                        projectAccessService,
                        chatPresetDefaults,
                        experimentalPresetCatalogService,
                        runtimeConfigValidationService);
    }

    @Test
    void runtimeState_normalizesOverride_keysEqualToBaseAreRemoved_andIsCustomReflectsManualKeys() {
        UUID uid = UUID.randomUUID();
        UUID cid = UUID.randomUUID();
        UUID pid = UUID.randomUUID();

        ConversationEntity conv = mock(ConversationEntity.class);
        ProjectEntity project = mock(ProjectEntity.class);
        when(project.getId()).thenReturn(pid);
        when(conv.getProject()).thenReturn(project);
        when(conv.getPreset()).thenReturn(null);
        when(conv.getRuntimeOverride()).thenReturn(Map.of("useRetrieval", true, "reasoningEnabled", true));
        when(projectAccessService.requireConversationForUser(uid, cid)).thenReturn(conv);

        when(chatPresetDefaults.effectivePresetIdForApi(null)).thenReturn(ChatPresetDefaults.DETERMINISTIC_DEFAULT_CHAT_PRESET_ID);

        // Base effective config: useRetrieval=true, reasoningEnabled=false
        when(runtimeConfigValidationService.validate(eq(uid), any()))
                .thenReturn(
                        new RuntimeConfigValidateResponse(
                                true,
                                true,
                                Map.of("useRetrieval", true, "reasoningEnabled", false),
                                List.of(),
                                List.of(),
                                "dense",
                                new RuntimeIndexCompatibilityDto(null, null, null, Map.of(), false),
                                false));

        var dto = sut.getRuntimeState(uid, cid);

        // useRetrieval matches base => removed; reasoningEnabled differs => kept
        assertThat(dto.runtimeOverride()).containsOnlyKeys("reasoningEnabled");
        assertThat(dto.manualOverrideKeys()).containsExactly("reasoningEnabled");
        assertThat(dto.isCustom()).isTrue();
    }

    @Test
    void runtimeState_whenAllOverridesEqualToBase_isCustomFalse_andOverrideEmpty() {
        UUID uid = UUID.randomUUID();
        UUID cid = UUID.randomUUID();
        UUID pid = UUID.randomUUID();

        ConversationEntity conv = mock(ConversationEntity.class);
        ProjectEntity project = mock(ProjectEntity.class);
        when(project.getId()).thenReturn(pid);
        when(conv.getProject()).thenReturn(project);
        when(conv.getPreset()).thenReturn(null);
        when(conv.getRuntimeOverride()).thenReturn(Map.of("useRetrieval", true));
        when(projectAccessService.requireConversationForUser(uid, cid)).thenReturn(conv);

        when(chatPresetDefaults.effectivePresetIdForApi(null)).thenReturn(ChatPresetDefaults.DETERMINISTIC_DEFAULT_CHAT_PRESET_ID);

        when(runtimeConfigValidationService.validate(eq(uid), any()))
                .thenReturn(
                        new RuntimeConfigValidateResponse(
                                true,
                                true,
                                Map.of("useRetrieval", true),
                                List.of(),
                                List.of(),
                                "dense",
                                new RuntimeIndexCompatibilityDto(null, null, null, Map.of(), false),
                                false));

        var dto = sut.getRuntimeState(uid, cid);
        assertThat(dto.runtimeOverride()).isEmpty();
        assertThat(dto.manualOverrideKeys()).isEmpty();
        assertThat(dto.isCustom()).isFalse();
    }
}

