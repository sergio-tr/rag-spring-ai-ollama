package com.uniovi.rag.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import com.uniovi.rag.interfaces.rest.dto.PatchConversationRequest;
import com.uniovi.rag.interfaces.rest.dto.RuntimeConfigValidateResponse;
import com.uniovi.rag.interfaces.rest.dto.RuntimeConfigValidationIssueDto;
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

class ConversationApplicationServicePatchValidationTest {

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
    void patchConversation_rejectsUnsupportedRuntimeOverride_beforePersistence() {
        UUID uid = UUID.randomUUID();
        UUID cid = UUID.randomUUID();
        UUID pid = UUID.randomUUID();

        ConversationEntity conv = mock(ConversationEntity.class);
        ProjectEntity project = mock(ProjectEntity.class);
        when(project.getId()).thenReturn(pid);
        when(conv.getProject()).thenReturn(project);
        when(conv.getPreset()).thenReturn(null);
        when(conv.getRuntimeOverride()).thenReturn(Map.of());
        when(projectAccessService.requireConversationForUser(uid, cid)).thenReturn(conv);

        when(chatPresetDefaults.effectivePresetIdForApi(null))
                .thenReturn(ChatPresetDefaults.DETERMINISTIC_DEFAULT_CHAT_PRESET_ID);
        when(knowledgeDocumentRepository.countByProject_IdAndStatus(pid, ProjectDocumentStatus.READY)).thenReturn(0L);

        // Base config validate (overrides empty)
        when(runtimeConfigValidationService.validate(eq(uid), any()))
                .thenReturn(
                        new RuntimeConfigValidateResponse(
                                true,
                                true,
                                Map.of("useRetrieval", true),
                                List.of(),
                                List.of(),
                                "wf",
                                new RuntimeIndexCompatibilityDto(null, null, null, Map.of(), false),
                                false))
                .thenReturn(
                        // Effective config validate (after override) -> unsupported
                        new RuntimeConfigValidateResponse(
                                false,
                                false,
                                Map.of(),
                                List.of(
                                        new RuntimeConfigValidationIssueDto(
                                                "UNSUPPORTED_RUNTIME_CONFIGURATION", null, "bad", "ERROR")),
                                List.of(),
                                null,
                                new RuntimeIndexCompatibilityDto(null, null, null, Map.of(), false),
                                false));

        assertThatThrownBy(
                        () ->
                                sut.patchConversation(
                                        uid,
                                        cid,
                                        new PatchConversationRequest(
                                                null,
                                                null,
                                                null,
                                                null,
                                                Map.of("useRetrieval", false),
                                                null,
                                                null)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(
                        ex -> {
                            ResponseStatusException rse = (ResponseStatusException) ex;
                            assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                        });

        verifyNoInteractions(conversationRepository);
        verify(conv, never()).setRuntimeOverride(any());
        verify(conv, never()).setPreset(any());
    }
}

