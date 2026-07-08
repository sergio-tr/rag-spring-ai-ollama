package com.uniovi.rag.application.service.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uniovi.rag.application.service.ConversationApplicationService;
import com.uniovi.rag.application.service.config.ChatPresetDefaults;
import com.uniovi.rag.application.service.evaluation.LabExperimentalPresetCatalogService;
import com.uniovi.rag.application.service.preset.PresetService;
import com.uniovi.rag.application.service.project.ProjectAccessService;
import com.uniovi.rag.application.service.runtime.config.RuntimeConfigValidationService;
import com.uniovi.rag.domain.ProjectDocumentStatus;
import com.uniovi.rag.infrastructure.persistence.ConversationRepository;
import com.uniovi.rag.infrastructure.persistence.KnowledgeDocumentRepository;
import com.uniovi.rag.infrastructure.persistence.MessageRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.ConversationEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.ProjectEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.UserEntity;
import com.uniovi.rag.interfaces.rest.dto.ConversationDto;
import com.uniovi.rag.interfaces.rest.dto.CreateConversationRequest;
import com.uniovi.rag.interfaces.rest.dto.RuntimeConfigValidateResponse;
import com.uniovi.rag.interfaces.rest.dto.RuntimeIndexCompatibilityDto;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class NewConversationInitialRuntimeOverrideTest {

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

    @BeforeEach
    void stubValidation() {
        lenient().when(chatPresetDefaults.loadDeterministicDefaultPreset()).thenReturn(Optional.empty());
        lenient()
                .when(indexAwareChatPresetDefaultService.resolveDefaultPresetId(any(), any()))
                .thenReturn(Optional.of(ChatPresetDefaults.DETERMINISTIC_DEFAULT_CHAT_PRESET_ID));
        lenient()
                .when(runtimeConfigValidationService.validateDraft(any(), any(), any(), any()))
                .thenReturn(
                        new RuntimeConfigValidateResponse(
                                true,
                                true,
                                Map.of("topK", 8, "similarityThreshold", 0.25),
                                List.of(),
                                List.of(),
                                "dense_chunk_workflow",
                                new RuntimeIndexCompatibilityDto(null, null, null, Map.of(), false, null, null, true, "UNKNOWN"),
                                false));
        lenient()
                .when(knowledgeDocumentRepository.countByProject_IdAndStatus(any(), eq(ProjectDocumentStatus.READY)))
                .thenReturn(0L);
    }

    @Test
    void createConversation_persistsRetrievalInitialRuntimeOverride() {
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UserEntity owner = mock(UserEntity.class);
        ProjectEntity project = mock(ProjectEntity.class);
        when(project.getOwner()).thenReturn(owner);
        when(project.getId()).thenReturn(projectId);
        when(projectAccessService.requireOwnedProject(userId, projectId)).thenReturn(project);
        when(conversationRepository.save(any(ConversationEntity.class)))
                .thenAnswer(
                        inv -> {
                            ConversationEntity e = inv.getArgument(0);
                            ReflectionTestUtils.setField(e, "id", UUID.randomUUID());
                            return e;
                        });

        Map<String, Object> overrides = Map.of("topK", 12, "similarityThreshold", 0.4);
        ConversationDto dto =
                service.createConversation(
                        userId, projectId, new CreateConversationRequest(null, null, null, overrides));

        ArgumentCaptor<ConversationEntity> cap = ArgumentCaptor.forClass(ConversationEntity.class);
        verify(conversationRepository).save(cap.capture());
        assertEquals(12, cap.getValue().getRuntimeOverride().get("topK"));
        assertEquals(0.4, cap.getValue().getRuntimeOverride().get("similarityThreshold"));
        assertThat(dto.runtimeOverride()).containsEntry("topK", 12).containsEntry("similarityThreshold", 0.4);
    }
}
