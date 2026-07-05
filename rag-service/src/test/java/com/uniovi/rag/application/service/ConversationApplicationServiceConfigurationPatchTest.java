package com.uniovi.rag.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uniovi.rag.application.service.chat.IndexAwareChatPresetDefaultService;
import com.uniovi.rag.application.service.chat.RuntimeConfigurationInvalidException;
import com.uniovi.rag.application.service.config.ChatPresetDefaults;
import com.uniovi.rag.application.service.evaluation.LabExperimentalPresetCatalogService;
import com.uniovi.rag.application.service.preset.PresetService;
import com.uniovi.rag.application.service.project.ProjectAccessService;
import com.uniovi.rag.application.service.runtime.config.RuntimeConfigValidationService;
import com.uniovi.rag.infrastructure.persistence.ConversationRepository;
import com.uniovi.rag.infrastructure.persistence.KnowledgeDocumentRepository;
import com.uniovi.rag.infrastructure.persistence.MessageRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.ConversationEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.ProjectEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.RagPresetEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.UserEntity;
import com.uniovi.rag.interfaces.rest.dto.PatchConversationRequest;
import com.uniovi.rag.interfaces.rest.dto.RuntimeConfigValidateRequest;
import com.uniovi.rag.interfaces.rest.dto.RuntimeConfigValidateResponse;
import com.uniovi.rag.interfaces.rest.dto.RuntimeIndexCompatibilityDto;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ConversationApplicationServiceConfigurationPatchTest {

    @Mock private ProjectAccessService projectAccessService;
    @Mock private ConversationRepository conversationRepository;
    @Mock private MessageRepository messageRepository;
    @Mock private KnowledgeDocumentRepository knowledgeDocumentRepository;
    @Mock private PresetService presetService;
    @Mock private ChatPresetDefaults chatPresetDefaults;
    @Mock private IndexAwareChatPresetDefaultService indexAwareChatPresetDefaultService;
    @Mock private LabExperimentalPresetCatalogService experimentalPresetCatalogService;
    @Mock private RuntimeConfigValidationService runtimeConfigValidationService;

    @InjectMocks private ConversationApplicationService service;

    private UUID userId;
    private UUID conversationId;
    private ConversationEntity conversation;
    private Map<String, Object> baseEffectiveConfig;
    private Map<String, Object> storedOverride = new LinkedHashMap<>();

    @BeforeEach
    void setUp() {
        storedOverride = new LinkedHashMap<>();
        userId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        UserEntity owner = mock(UserEntity.class);
        ProjectEntity project = mock(ProjectEntity.class);
        when(project.getId()).thenReturn(projectId);
        lenient().when(project.getOwner()).thenReturn(owner);

        conversation = mock(ConversationEntity.class);
        lenient().when(conversation.getId()).thenReturn(conversationId);
        lenient().when(conversation.getProject()).thenReturn(project);
        lenient().when(conversation.getRuntimeOverride()).thenAnswer(inv -> storedOverride);
        doAnswer(
                        inv -> {
                            storedOverride = new LinkedHashMap<>(inv.getArgument(0));
                            return null;
                        })
                .when(conversation)
                .setRuntimeOverride(any());

        baseEffectiveConfig = new LinkedHashMap<>();
        baseEffectiveConfig.put("useAdvisor", true);
        baseEffectiveConfig.put("rankerEnabled", true);
        baseEffectiveConfig.put("postRetrievalEnabled", true);

        when(projectAccessService.requireConversationForUser(userId, conversationId)).thenReturn(conversation);
        when(chatPresetDefaults.effectivePresetIdForApi(any())).thenReturn(UUID.randomUUID());
        when(runtimeConfigValidationService.validate(any(), any(RuntimeConfigValidateRequest.class)))
                .thenAnswer(
                        inv -> {
                            RuntimeConfigValidateRequest req = inv.getArgument(1);
                            Map<String, Object> effective = new LinkedHashMap<>(baseEffectiveConfig);
                            if (req.overrides() != null) {
                                effective.putAll(req.overrides());
                            }
                            return validResponse(effective);
                        });
        when(conversationRepository.save(any(ConversationEntity.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void sequentialFalsePatches_rejectsWhenDisablingPresetBaseFeatures() {
        assertThatThrownBy(
                        () ->
                                service.patchConversation(
                                        userId,
                                        conversationId,
                                        new PatchConversationRequest(
                                                null,
                                                null,
                                                null,
                                                null,
                                                Map.of("useAdvisor", false),
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null)))
                .isInstanceOf(RuntimeConfigurationInvalidException.class);
    }

    @Test
    void clearRuntimeOverride_resetsCustomConfiguration() {
        storedOverride = new LinkedHashMap<>(Map.of("useAdvisor", false));

        service.patchConversation(
                userId,
                conversationId,
                new PatchConversationRequest(null, null, null, null, null, true, null, null, null, null, null));

        ArgumentCaptor<ConversationEntity> cap = ArgumentCaptor.forClass(ConversationEntity.class);
        verify(conversationRepository).save(cap.capture());
        assertThat(cap.getValue().getRuntimeOverride()).isEmpty();
    }

    @Test
    void presetChange_clearsCustomConfiguration() {
        storedOverride = new LinkedHashMap<>(Map.of("useAdvisor", false));
        UUID newPresetId = UUID.randomUUID();
        RagPresetEntity oldPreset = mock(RagPresetEntity.class);
        when(oldPreset.getId()).thenReturn(UUID.randomUUID());
        when(conversation.getPreset()).thenReturn(oldPreset);
        RagPresetEntity preset = mock(RagPresetEntity.class);
        when(preset.getId()).thenReturn(newPresetId);
        when(presetService.requireVisiblePreset(userId, newPresetId)).thenReturn(preset);

        service.patchConversation(
                userId,
                conversationId,
                new PatchConversationRequest(
                        null, newPresetId.toString(), null, null, null, null, null, null, null, null, null));

        ArgumentCaptor<ConversationEntity> cap = ArgumentCaptor.forClass(ConversationEntity.class);
        verify(conversationRepository).save(cap.capture());
        assertThat(cap.getValue().getRuntimeOverride()).isEmpty();
    }

    private static RuntimeConfigValidateResponse validResponse(Map<String, Object> effective) {
        return new RuntimeConfigValidateResponse(
                true,
                true,
                effective,
                List.of(),
                List.of(),
                "dense_chunk_workflow",
                new RuntimeIndexCompatibilityDto(null, null, null, Map.of(), false, null, null, true, "UNKNOWN"),
                false);
    }
}
