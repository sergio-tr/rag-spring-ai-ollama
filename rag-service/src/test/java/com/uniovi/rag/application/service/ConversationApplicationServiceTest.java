package com.uniovi.rag.application.service;

import com.uniovi.rag.domain.MessageRole;
import com.uniovi.rag.domain.MessageProcessingStatus;
import com.uniovi.rag.domain.ProjectDocumentStatus;
import com.uniovi.rag.domain.knowledge.CorpusScope;
import com.uniovi.rag.interfaces.rest.dto.ConversationDto;
import com.uniovi.rag.interfaces.rest.dto.CreateConversationRequest;
import com.uniovi.rag.interfaces.rest.dto.MessageDto;
import com.uniovi.rag.interfaces.rest.dto.PatchConversationRequest;
import com.uniovi.rag.infrastructure.persistence.ConversationRepository;
import com.uniovi.rag.infrastructure.persistence.KnowledgeDocumentRepository;
import com.uniovi.rag.infrastructure.persistence.MessageRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.ConversationEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.MessageEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.ProjectEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.RagPresetEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.UserEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeDocumentEntity;
import com.uniovi.rag.application.service.evaluation.LabExperimentalPresetCatalogService;
import com.uniovi.rag.application.service.chat.RuntimeConfigurationInvalidException;
import com.uniovi.rag.application.service.runtime.config.RuntimeConfigValidationService;
import com.uniovi.rag.interfaces.rest.dto.RuntimeConfigValidateResponse;
import com.uniovi.rag.interfaces.rest.dto.RuntimeConfigValidationIssueDto;
import com.uniovi.rag.interfaces.rest.dto.RuntimeIndexCompatibilityDto;
import com.uniovi.rag.interfaces.rest.dto.ExperimentalPresetCatalogItemDto;
import com.uniovi.rag.service.config.ChatPresetDefaults;
import com.uniovi.rag.service.preset.PresetService;
import com.uniovi.rag.service.project.ProjectAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationApplicationServiceTest {

    @Mock
    private ProjectAccessService projectAccessService;

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private KnowledgeDocumentRepository knowledgeDocumentRepository;

    @Mock
    private PresetService presetService;

    @Mock
    private ChatPresetDefaults chatPresetDefaults;

    @Mock
    private LabExperimentalPresetCatalogService experimentalPresetCatalogService;

    @Mock
    private RuntimeConfigValidationService runtimeConfigValidationService;

    @InjectMocks
    private ConversationApplicationService service;

    @BeforeEach
    void stubChatPresetDefaults() {
        lenient().when(chatPresetDefaults.loadDeterministicDefaultPreset()).thenReturn(Optional.empty());
        lenient()
                .when(chatPresetDefaults.effectivePresetIdForApi(any()))
                .thenAnswer(
                        inv -> {
                            UUID id = inv.getArgument(0, UUID.class);
                            return id != null ? id : ChatPresetDefaults.DETERMINISTIC_DEFAULT_CHAT_PRESET_ID;
                        });

        lenient()
                .when(runtimeConfigValidationService.validateDraft(any(), any(), any(), any()))
                .thenReturn(
                        new RuntimeConfigValidateResponse(
                                true,
                                true,
                                Map.of(),
                                List.of(),
                                List.of(),
                                "dense_chunk_workflow",
                                new RuntimeIndexCompatibilityDto(null, null, null, Map.of(), false, null, null, true, "UNKNOWN"),
                                false));
        lenient()
                .when(runtimeConfigValidationService.validate(any(), any()))
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
        lenient()
                .when(knowledgeDocumentRepository.countByProject_IdAndStatus(any(), eq(ProjectDocumentStatus.READY)))
                .thenReturn(0L);
    }

    @Test
    void listMessages_returnsEmptyWhenNoMessages() {
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        ConversationEntity conv = mock(ConversationEntity.class);
        when(projectAccessService.requireConversationForUser(userId, conversationId)).thenReturn(conv);
        when(messageRepository.findByConversation_IdAndDeletedAtIsNullOrderBySeqAsc(conversationId))
                .thenReturn(List.of());

        assertThat(service.listMessages(userId, conversationId)).isEmpty();
    }

    @Test
    void listConversations_mapsDtos() {
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID cid = UUID.randomUUID();
        when(projectAccessService.requireOwnedProject(userId, projectId)).thenReturn(mock(ProjectEntity.class));
        ConversationEntity c = mock(ConversationEntity.class);
        when(c.getId()).thenReturn(cid);
        when(c.getTitle()).thenReturn("T");
        when(c.getUpdatedAt()).thenReturn(Instant.EPOCH);
        when(c.getPreset()).thenReturn(null);
        when(c.getDocumentFilter()).thenReturn(List.of());
        when(conversationRepository.findByProject_IdAndUser_IdOrderByUpdatedAtDesc(projectId, userId))
                .thenReturn(List.of(c));

        List<ConversationDto> out = service.listConversations(userId, projectId);
        assertThat(out).hasSize(1);
        assertEquals(cid, out.getFirst().id());
        assertEquals("T", out.getFirst().title());
        assertEquals(ChatPresetDefaults.DETERMINISTIC_DEFAULT_CHAT_PRESET_ID, out.getFirst().effectivePresetId());
    }

    @Test
    void createConversation_usesDefaultTitleAndSaves() {
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

        ConversationDto dto = service.createConversation(userId, projectId, new CreateConversationRequest(null, null));
        assertThat(dto.title()).isEqualTo("New chat");
        assertEquals(ChatPresetDefaults.DETERMINISTIC_DEFAULT_CHAT_PRESET_ID, dto.effectivePresetId());
    }

    @Test
    void createConversation_setsDeterministicDemoWorstEntityWhenMigrationRowPresent() {
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UserEntity owner = mock(UserEntity.class);
        ProjectEntity project = mock(ProjectEntity.class);
        when(project.getOwner()).thenReturn(owner);
        when(project.getId()).thenReturn(projectId);
        when(projectAccessService.requireOwnedProject(userId, projectId)).thenReturn(project);

        RagPresetEntity demoWorst = mock(RagPresetEntity.class);
        when(demoWorst.getId()).thenReturn(ChatPresetDefaults.DETERMINISTIC_DEFAULT_CHAT_PRESET_ID);
        when(chatPresetDefaults.loadDeterministicDefaultPreset()).thenReturn(Optional.of(demoWorst));

        when(conversationRepository.save(any(ConversationEntity.class)))
                .thenAnswer(
                        inv -> {
                            ConversationEntity e = inv.getArgument(0);
                            ReflectionTestUtils.setField(e, "id", UUID.randomUUID());
                            return e;
                        });

        service.createConversation(userId, projectId, new CreateConversationRequest(null, null));

        ArgumentCaptor<ConversationEntity> cap = ArgumentCaptor.forClass(ConversationEntity.class);
        verify(conversationRepository).save(cap.capture());
        assertSame(demoWorst, cap.getValue().getPreset());
    }

    @Test
    void createConversation_validatesDocumentFilterAgainstProject() {
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        UserEntity owner = mock(UserEntity.class);
        ProjectEntity project = mock(ProjectEntity.class);
        when(project.getOwner()).thenReturn(owner);
        when(project.getId()).thenReturn(projectId);
        when(projectAccessService.requireOwnedProject(userId, projectId)).thenReturn(project);
        var doc = mock(KnowledgeDocumentEntity.class);
        when(doc.getStatus()).thenReturn(ProjectDocumentStatus.READY);
        when(doc.getCorpusScope()).thenReturn(CorpusScope.PROJECT_SHARED);
        when(knowledgeDocumentRepository.findByIdAndProject_Id(docId, projectId)).thenReturn(Optional.of(doc));
        when(conversationRepository.save(any(ConversationEntity.class)))
                .thenAnswer(
                        inv -> {
                            ConversationEntity e = inv.getArgument(0);
                            ReflectionTestUtils.setField(e, "id", UUID.randomUUID());
                            return e;
                        });

        service.createConversation(
                userId, projectId, new CreateConversationRequest("x", List.of(docId.toString())));
        verify(conversationRepository).save(any(ConversationEntity.class));
    }

    @Test
    void createConversation_invalidDocumentId_badRequest() {
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        ProjectEntity project = mock(ProjectEntity.class);
        when(project.getId()).thenReturn(projectId);
        when(projectAccessService.requireOwnedProject(userId, projectId)).thenReturn(project);

        ResponseStatusException ex =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                service.createConversation(
                                        userId, projectId, new CreateConversationRequest("x", List.of("not-uuid"))));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void patchConversation_titleUpdates() {
        UUID userId = UUID.randomUUID();
        UUID convId = UUID.randomUUID();
        ConversationEntity c = mock(ConversationEntity.class);
        when(projectAccessService.requireConversationForUser(userId, convId)).thenReturn(c);

        service.patchConversation(userId, convId, new PatchConversationRequest("New title", null, null, null, null, null, null, null, null, null, null));
        verify(c).setTitle("New title");
        verify(c).touchUpdated();
        verify(conversationRepository).save(c);
    }

    @Test
    void patchConversation_persistsLlmModelAfterRuntimeValidation() {
        UUID userId = UUID.randomUUID();
        UUID convId = UUID.randomUUID();
        ConversationEntity c = mock(ConversationEntity.class);
        ProjectEntity project = mock(ProjectEntity.class);
        when(project.getId()).thenReturn(UUID.randomUUID());
        when(c.getProject()).thenReturn(project);
        when(projectAccessService.requireConversationForUser(userId, convId)).thenReturn(c);
        when(c.getLlmModel()).thenReturn(null);

        service.patchConversation(
                userId,
                convId,
                new PatchConversationRequest(null, null, null, null, null, null, null, null, "phi3:latest", null, null));

        verify(c).setLlmModel("phi3:latest");
        verify(c, never()).setRuntimeOverride(any());
        verify(conversationRepository).save(c);
    }

    @Test
    void patchConversation_invalidPresetId_badRequest() {
        UUID userId = UUID.randomUUID();
        UUID convId = UUID.randomUUID();
        ConversationEntity c = mock(ConversationEntity.class);
        when(projectAccessService.requireConversationForUser(userId, convId)).thenReturn(c);

        ResponseStatusException ex =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                service.patchConversation(
                                        userId,
                                        convId,
                                        new PatchConversationRequest(
                                                null, "bad-uuid", null, null, null, null, null, null, null, null, null)));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(conversationRepository, never()).save(any());
    }

    @Test
    void patchConversation_setsPresetWhenValid() {
        UUID userId = UUID.randomUUID();
        UUID convId = UUID.randomUUID();
        UUID presetId = UUID.randomUUID();
        ConversationEntity c = mock(ConversationEntity.class);
        ProjectEntity project = mock(ProjectEntity.class);
        when(project.getId()).thenReturn(UUID.randomUUID());
        when(c.getProject()).thenReturn(project);
        when(c.getRuntimeOverride()).thenReturn(Map.of());
        when(projectAccessService.requireConversationForUser(userId, convId)).thenReturn(c);
        RagPresetEntity preset = mock(RagPresetEntity.class);
        when(preset.getId()).thenReturn(presetId);
        when(presetService.requireVisiblePreset(userId, presetId)).thenReturn(preset);

        service.patchConversation(
                userId,
                convId,
                new PatchConversationRequest(null, presetId.toString(), null, null, null, null, null, null, null, null, null));
        verify(c).setPreset(preset);
        verify(c).touchUpdated();
        verify(conversationRepository).save(c);
    }

    @Test
    void patchConversation_rejectsExperimentalPreset_whenChatSelectableFalse() {
        UUID userId = UUID.randomUUID();
        UUID convId = UUID.randomUUID();
        UUID presetId = UUID.randomUUID();
        ConversationEntity c = mock(ConversationEntity.class);
        when(projectAccessService.requireConversationForUser(userId, convId)).thenReturn(c);

        RagPresetEntity preset = mock(RagPresetEntity.class);
        when(preset.getId()).thenReturn(presetId);
        when(preset.getTags()).thenReturn(List.of("experimental", "tfg"));
        when(presetService.requireVisiblePreset(userId, presetId)).thenReturn(preset);
        when(experimentalPresetCatalogService.list())
                .thenReturn(
                        List.of(
                                new ExperimentalPresetCatalogItemDto(
                                        presetId.toString(),
                                        "PX",
                                        "S2",
                                        "Blocked preset",
                                        "desc",
                                        null,
                                        List.of(),
                                        false,
                                        "NOT_SUPPORTED",
                                        "STRUCTURED_SEARCH_WITH_RETRIEVAL_NOT_SUPPORTED",
                                        false,
                                        Map.of(),
                                        List.of("EXECUTED", "NOT_SUPPORTED", "FAILED", "SKIPPED"),
                                        false,
                                        true,
                                        true,
                                        false,
                                        false,
                                        false,
                                        true,
                                        0,
                                        null,
                                        "{}")));

        ResponseStatusException ex =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                service.patchConversation(
                                        userId,
                                        convId,
                                        new PatchConversationRequest(
                                                null, presetId.toString(), null, null, null, null, null, null, null, null, null)));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(conversationRepository, never()).save(any());
    }

    @Test
    void patchConversation_acceptsExperimentalPresetWhenChatSelectable() {
        UUID userId = UUID.randomUUID();
        UUID convId = UUID.randomUUID();
        UUID presetId = UUID.randomUUID();
        ConversationEntity c = mock(ConversationEntity.class);
        ProjectEntity project = mock(ProjectEntity.class);
        when(project.getId()).thenReturn(UUID.randomUUID());
        when(c.getProject()).thenReturn(project);
        when(c.getRuntimeOverride()).thenReturn(Map.of());
        when(projectAccessService.requireConversationForUser(userId, convId)).thenReturn(c);

        RagPresetEntity preset = mock(RagPresetEntity.class);
        when(preset.getId()).thenReturn(presetId);
        when(preset.getTags()).thenReturn(List.of("experimental", "tfg"));
        when(presetService.requireVisiblePreset(userId, presetId)).thenReturn(preset);

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
                                        List.of("USE_RETRIEVAL", "METADATA"),
                                        true,
                                        "EXECUTABLE",
                                        null,
                                        false,
                                        Map.of(),
                                        List.of("EXECUTED", "FAILED", "SKIPPED"),
                                        true,
                                        true,
                                        false,
                                        true,
                                        true,
                                        true,
                                        true,
                                        0,
                                        null,
                                        "{}")));

        service.patchConversation(
                userId,
                convId,
                new PatchConversationRequest(null, presetId.toString(), null, null, null, null, null, null, null, null, null));

        verify(c).setPreset(preset);
        verify(conversationRepository).save(c);
    }

    @Test
    void patchConversation_rejectsExperimentalPresetWhenNotChatSelectable_withClearReason() {
        UUID userId = UUID.randomUUID();
        UUID convId = UUID.randomUUID();
        UUID presetId = UUID.randomUUID();
        ConversationEntity c = mock(ConversationEntity.class);
        when(projectAccessService.requireConversationForUser(userId, convId)).thenReturn(c);

        RagPresetEntity preset = mock(RagPresetEntity.class);
        when(preset.getId()).thenReturn(presetId);
        when(preset.getTags()).thenReturn(List.of("experimental", "tfg"));
        when(presetService.requireVisiblePreset(userId, presetId)).thenReturn(preset);

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
                                        List.of("CLARIFICATION"),
                                        false,
                                        "REQUIRES_MULTI_TURN",
                                        "PRESET_CLARIFICATION_BENCHMARK_NOT_SUPPORTED",
                                        true,
                                        Map.of(),
                                        List.of("EXECUTED", "NOT_SUPPORTED", "FAILED", "SKIPPED"),
                                        false,
                                        true,
                                        true,
                                        true,
                                        true,
                                        true,
                                        true,
                                        0,
                                        null,
                                        "{}")));

        ResponseStatusException ex =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                service.patchConversation(
                                        userId,
                                        convId,
                                        new PatchConversationRequest(
                                                null, presetId.toString(), null, null, null, null, null, null, null, null, null)));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertThat(ex.getReason())
                .contains("not selectable in Chat")
                .contains("PRESET_CLARIFICATION_BENCHMARK_NOT_SUPPORTED");
        verify(conversationRepository, never()).save(any());
    }

    @Test
    void deleteConversation_notFound() {
        UUID userId = UUID.randomUUID();
        UUID convId = UUID.randomUUID();
        when(conversationRepository.findByIdAndUser_Id(convId, userId)).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class, () -> service.deleteConversation(userId, convId));
    }

    @Test
    void deleteConversation_ok() {
        UUID userId = UUID.randomUUID();
        UUID convId = UUID.randomUUID();
        when(conversationRepository.findByIdAndUser_Id(convId, userId)).thenReturn(Optional.of(mock()));
        service.deleteConversation(userId, convId);
        verify(conversationRepository).deleteById(convId);
    }

    @Test
    void listMessages_mapsMessageFields() {
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID mid = UUID.randomUUID();
        when(projectAccessService.requireConversationForUser(userId, conversationId)).thenReturn(mock(ConversationEntity.class));
        MessageEntity m = mock(MessageEntity.class);
        when(m.getId()).thenReturn(mid);
        when(m.getRole()).thenReturn(MessageRole.USER);
        when(m.getContent()).thenReturn("hi");
        when(m.getCreatedAt()).thenReturn(Instant.EPOCH);
        when(m.getSources()).thenReturn(null);
        when(m.getQueryType()).thenReturn(null);
        when(m.getPipelineSteps()).thenReturn(null);
        when(m.getStatus()).thenReturn(MessageProcessingStatus.DONE);
        when(m.getExecutionMetadata()).thenReturn(null);
        when(messageRepository.findByConversation_IdAndDeletedAtIsNullOrderBySeqAsc(conversationId))
                .thenReturn(List.of(m));

        List<MessageDto> result = service.listMessages(userId, conversationId);
        assertThat(result).hasSize(1);
        assertEquals(mid, result.getFirst().id());
        assertEquals("hi", result.getFirst().content());
    }

    @Test
    void createConversation_initialRuntimeOverride_persisted() {
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

        Map<String, Object> overrides = Map.of("reasoningEnabled", true);
        ConversationDto dto =
                service.createConversation(
                        userId, projectId, new CreateConversationRequest(null, null, null, overrides));

        ArgumentCaptor<ConversationEntity> cap = ArgumentCaptor.forClass(ConversationEntity.class);
        verify(conversationRepository).save(cap.capture());
        assertThat(cap.getValue().getRuntimeOverride()).containsEntry("reasoningEnabled", true);
        assertThat(dto.runtimeOverride()).containsEntry("reasoningEnabled", true);
    }

    @Test
    void createConversation_rejectsExperimentalWhenNotChatSelectable() {
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID presetId = UUID.randomUUID();
        UserEntity owner = mock(UserEntity.class);
        ProjectEntity project = mock(ProjectEntity.class);
        when(project.getOwner()).thenReturn(owner);
        when(project.getId()).thenReturn(projectId);
        when(projectAccessService.requireOwnedProject(userId, projectId)).thenReturn(project);

        RagPresetEntity preset = mock(RagPresetEntity.class);
        when(preset.getId()).thenReturn(presetId);
        when(preset.getTags()).thenReturn(List.of("experimental", "tfg"));
        when(presetService.requireVisiblePreset(userId, presetId)).thenReturn(preset);
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
                                        List.of("CLARIFICATION"),
                                        false,
                                        "REQUIRES_MULTI_TURN",
                                        "PRESET_CLARIFICATION_BENCHMARK_NOT_SUPPORTED",
                                        true,
                                        Map.of(),
                                        List.of("EXECUTED", "NOT_SUPPORTED", "FAILED", "SKIPPED"),
                                        false,
                                        true,
                                        true,
                                        true,
                                        true,
                                        true,
                                        true,
                                        0,
                                        null,
                                        "{}")));

        ResponseStatusException ex =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                service.createConversation(
                                        userId,
                                        projectId,
                                        new CreateConversationRequest(null, null, presetId.toString(), null)));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(conversationRepository, never()).save(any());
    }

    @Test
    void createConversation_acceptsExperimentalWhenChatSelectable() {
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID presetId = UUID.randomUUID();
        UserEntity owner = mock(UserEntity.class);
        ProjectEntity project = mock(ProjectEntity.class);
        when(project.getOwner()).thenReturn(owner);
        when(project.getId()).thenReturn(projectId);
        when(projectAccessService.requireOwnedProject(userId, projectId)).thenReturn(project);

        RagPresetEntity preset = mock(RagPresetEntity.class);
        when(preset.getId()).thenReturn(presetId);
        when(preset.getTags()).thenReturn(List.of("experimental", "tfg"));
        when(presetService.requireVisiblePreset(userId, presetId)).thenReturn(preset);
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
                                        List.of("USE_RETRIEVAL", "METADATA"),
                                        true,
                                        "EXECUTABLE",
                                        null,
                                        false,
                                        Map.of(),
                                        List.of("EXECUTED", "FAILED", "SKIPPED"),
                                        true,
                                        true,
                                        false,
                                        true,
                                        true,
                                        true,
                                        true,
                                        0,
                                        null,
                                        "{}")));

        when(conversationRepository.save(any(ConversationEntity.class)))
                .thenAnswer(
                        inv -> {
                            ConversationEntity e = inv.getArgument(0);
                            ReflectionTestUtils.setField(e, "id", UUID.randomUUID());
                            return e;
                        });

        ConversationDto dto =
                service.createConversation(
                        userId, projectId, new CreateConversationRequest(null, null, presetId.toString(), null));

        assertEquals(presetId, dto.presetId());
        verify(conversationRepository).save(any(ConversationEntity.class));
    }

    @Test
    void createConversation_unsupportedRuntime_badRequest() {
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UserEntity owner = mock(UserEntity.class);
        ProjectEntity project = mock(ProjectEntity.class);
        when(project.getOwner()).thenReturn(owner);
        when(project.getId()).thenReturn(projectId);
        when(projectAccessService.requireOwnedProject(userId, projectId)).thenReturn(project);
        when(runtimeConfigValidationService.validateDraft(any(), any(), any(), any()))
                .thenReturn(
                        new RuntimeConfigValidateResponse(
                                false,
                                false,
                                Map.of(),
                                List.of(
                                        new RuntimeConfigValidationIssueDto(
                                                "UNSUPPORTED_RUNTIME_CONFIGURATION",
                                                null,
                                                "bad",
                                                "ERROR")),
                                List.of(),
                                null,
                                new RuntimeIndexCompatibilityDto(null, null, null, Map.of(), false, null, null, true, "UNKNOWN"),
                                false));

        RuntimeConfigurationInvalidException ex =
                assertThrows(
                        RuntimeConfigurationInvalidException.class,
                        () -> service.createConversation(userId, projectId, new CreateConversationRequest(null, null, null, null)));
        assertEquals("UNSUPPORTED_RUNTIME_CONFIGURATION", ex.code());
        verify(conversationRepository, never()).save(any());
    }

    @Test
    void createConversation_indexConflictWithReadyDocs_conflict() {
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UserEntity owner = mock(UserEntity.class);
        ProjectEntity project = mock(ProjectEntity.class);
        when(project.getOwner()).thenReturn(owner);
        when(project.getId()).thenReturn(projectId);
        when(projectAccessService.requireOwnedProject(userId, projectId)).thenReturn(project);
        when(knowledgeDocumentRepository.countByProject_IdAndStatus(projectId, ProjectDocumentStatus.READY))
                .thenReturn(1L);
        when(runtimeConfigValidationService.validateDraft(any(), any(), any(), any()))
                .thenReturn(
                        new RuntimeConfigValidateResponse(
                                false,
                                false,
                                Map.of(),
                                List.of(
                                        new RuntimeConfigValidationIssueDto(
                                                "INDEX_REQUIRES_REINDEX",
                                                "metadataEnabled",
                                                "need reindex",
                                                "ERROR")),
                                List.of(),
                                null,
                                new RuntimeIndexCompatibilityDto(null, null, null, Map.of(), true, null, null, false, "REQUIRES_REINDEX"),
                                true));

        RuntimeConfigurationInvalidException ex =
                assertThrows(
                        RuntimeConfigurationInvalidException.class,
                        () -> service.createConversation(userId, projectId, new CreateConversationRequest(null, null, null, null)));
        assertEquals("INDEX_REQUIRES_REINDEX", ex.code());
        verify(conversationRepository, never()).save(any());
    }
}
