package com.uniovi.rag.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.uniovi.rag.application.service.evaluation.LabExperimentalPresetCatalogService;
import com.uniovi.rag.application.service.runtime.config.RuntimeConfigValidationService;
import com.uniovi.rag.domain.ProjectDocumentStatus;
import com.uniovi.rag.infrastructure.persistence.ConversationRepository;
import com.uniovi.rag.infrastructure.persistence.KnowledgeDocumentRepository;
import com.uniovi.rag.infrastructure.persistence.MessageRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.ConversationEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.ProjectEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.RagPresetEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.UserEntity;
import com.uniovi.rag.interfaces.rest.dto.ExperimentalPresetCatalogItemDto;
import com.uniovi.rag.interfaces.rest.dto.PatchConversationRequest;
import com.uniovi.rag.interfaces.rest.dto.RuntimeConfigValidateResponse;
import com.uniovi.rag.interfaces.rest.dto.RuntimeIndexCompatibilityDto;
import com.uniovi.rag.service.config.ChatPresetDefaults;
import com.uniovi.rag.service.preset.PresetService;
import com.uniovi.rag.service.project.ProjectAccessService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class ConversationApplicationServicePresetPatchTest {

    private ProjectAccessService projectAccessService;
    private ConversationRepository conversationRepository;
    private MessageRepository messageRepository;
    private KnowledgeDocumentRepository knowledgeDocumentRepository;
    private PresetService presetService;
    private ChatPresetDefaults chatPresetDefaults;
    private LabExperimentalPresetCatalogService experimentalPresetCatalogService;
    private RuntimeConfigValidationService runtimeConfigValidationService;

    private ConversationApplicationService sut;

    @BeforeEach
    void setup() {
        projectAccessService = mock(ProjectAccessService.class);
        conversationRepository = mock(ConversationRepository.class);
        messageRepository = mock(MessageRepository.class);
        knowledgeDocumentRepository = mock(KnowledgeDocumentRepository.class);
        presetService = mock(PresetService.class);
        chatPresetDefaults = mock(ChatPresetDefaults.class);
        experimentalPresetCatalogService = mock(LabExperimentalPresetCatalogService.class);
        runtimeConfigValidationService = mock(RuntimeConfigValidationService.class);

        sut =
                new ConversationApplicationService(
                        projectAccessService,
                        conversationRepository,
                        messageRepository,
                        knowledgeDocumentRepository,
                        presetService,
                        chatPresetDefaults,
                        experimentalPresetCatalogService,
                        runtimeConfigValidationService);
    }

    @Test
    void patchConversation_allowsExperimentalPreset_whenChatSelectableTrue() {
        UUID uid = UUID.randomUUID();
        UUID cid = UUID.randomUUID();
        UUID presetId = UUID.fromString("cafe0001-0001-4001-8001-000000000014");

        ConversationEntity conv = mockConversation(uid, cid);
        when(projectAccessService.requireConversationForUser(uid, cid)).thenReturn(conv);
        when(chatPresetDefaults.effectivePresetIdForApi(any()))
                .thenAnswer(inv -> inv.getArgument(0) != null ? inv.getArgument(0) : ChatPresetDefaults.DETERMINISTIC_DEFAULT_CHAT_PRESET_ID);
        when(knowledgeDocumentRepository.countByProject_IdAndStatus(any(), eq(ProjectDocumentStatus.READY))).thenReturn(0L);
        when(runtimeConfigValidationService.validate(eq(uid), any()))
                .thenReturn(
                        new RuntimeConfigValidateResponse(
                                true,
                                true,
                                Map.of("useRetrieval", true),
                                List.of(),
                                List.of(),
                                "dense_chunk_workflow",
                                new RuntimeIndexCompatibilityDto(null, null, null, Map.of(), false, null, null, true, "UNKNOWN"),
                                false));

        RagPresetEntity preset = mock(RagPresetEntity.class);
        when(preset.getId()).thenReturn(presetId);
        when(preset.getTags()).thenReturn(List.of("experimental"));
        when(presetService.requireVisiblePreset(eq(uid), eq(presetId))).thenReturn(preset);

        when(experimentalPresetCatalogService.list())
                .thenReturn(
                        List.of(
                                new ExperimentalPresetCatalogItemDto(
                                        presetId.toString(),
                                        "P4",
                                        "S2",
                                        "P4 preset",
                                        "desc",
                                        null,
                                        List.of(),
                                        true,
                                        "EXECUTABLE",
                                        null,
                                        false,
                                        Map.of(),
                                        List.of("EXECUTED"),
                                        true,
                                        true,
                                        false,
                                        true,
                                        true,
                                        true,
                                        true)));

        // Should not throw.
        sut.patchConversation(uid, cid, new PatchConversationRequest(null, presetId.toString(), null, null, null, null, null));
    }

    @Test
    void patchConversation_rejectsExperimentalPreset_whenChatSelectableFalse_withClearMessage() {
        UUID uid = UUID.randomUUID();
        UUID cid = UUID.randomUUID();
        UUID presetId = UUID.fromString("cafe0001-0001-4001-8001-000000000011");

        ConversationEntity conv = mockConversation(uid, cid);
        when(projectAccessService.requireConversationForUser(uid, cid)).thenReturn(conv);
        when(chatPresetDefaults.effectivePresetIdForApi(any()))
                .thenAnswer(inv -> inv.getArgument(0) != null ? inv.getArgument(0) : ChatPresetDefaults.DETERMINISTIC_DEFAULT_CHAT_PRESET_ID);
        when(knowledgeDocumentRepository.countByProject_IdAndStatus(any(), eq(ProjectDocumentStatus.READY))).thenReturn(0L);
        when(runtimeConfigValidationService.validate(eq(uid), any()))
                .thenReturn(
                        new RuntimeConfigValidateResponse(
                                true,
                                true,
                                Map.of("useRetrieval", true),
                                List.of(),
                                List.of(),
                                "dense_chunk_workflow",
                                new RuntimeIndexCompatibilityDto(null, null, null, Map.of(), false, null, null, true, "UNKNOWN"),
                                false));

        RagPresetEntity preset = mock(RagPresetEntity.class);
        when(preset.getId()).thenReturn(presetId);
        when(preset.getTags()).thenReturn(List.of("experimental"));
        when(presetService.requireVisiblePreset(eq(uid), eq(presetId))).thenReturn(preset);

        when(experimentalPresetCatalogService.list())
                .thenReturn(
                        List.of(
                                new ExperimentalPresetCatalogItemDto(
                                        presetId.toString(),
                                        "P11",
                                        "S3",
                                        "P11 preset",
                                        "desc",
                                        null,
                                        List.of(),
                                        false,
                                        "NOT_SUPPORTED",
                                        "Blocked for test",
                                        true,
                                        Map.of(),
                                        List.of("EXECUTED", "NOT_SUPPORTED", "FAILED", "SKIPPED"),
                                        false,
                                        true,
                                        true,
                                        true,
                                        true,
                                        true,
                                        true)));

        assertThatThrownBy(
                        () ->
                                sut.patchConversation(
                                        uid,
                                        cid,
                                        new PatchConversationRequest(
                                                null, presetId.toString(), null, null, null, null, null)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(
                        ex -> {
                            ResponseStatusException rse = (ResponseStatusException) ex;
                            assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                            assertThat(rse.getReason()).contains("not selectable in Chat");
                        });

        // No persistence side-effects on rejection.
        verifyNoInteractions(conversationRepository);
    }

    private static ConversationEntity mockConversation(UUID uid, UUID cid) {
        ConversationEntity conv = mock(ConversationEntity.class);
        when(conv.getId()).thenReturn(cid);
        UserEntity user = mock(UserEntity.class);
        when(user.getId()).thenReturn(uid);
        when(conv.getUser()).thenReturn(user);
        ProjectEntity project = mock(ProjectEntity.class);
        when(project.getId()).thenReturn(UUID.randomUUID());
        when(conv.getProject()).thenReturn(project);
        return conv;
    }
}

