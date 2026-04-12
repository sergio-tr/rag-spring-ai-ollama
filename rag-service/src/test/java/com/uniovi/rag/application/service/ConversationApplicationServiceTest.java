package com.uniovi.rag.application.service;

import com.uniovi.rag.domain.MessageRole;
import com.uniovi.rag.domain.MessageProcessingStatus;
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
import com.uniovi.rag.service.preset.PresetService;
import com.uniovi.rag.service.project.ProjectAccessService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

    @InjectMocks
    private ConversationApplicationService service;

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
        when(knowledgeDocumentRepository.findByIdAndProject_Id(docId, projectId)).thenReturn(Optional.of(mock()));
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

        service.patchConversation(userId, convId, new PatchConversationRequest("New title", null, null, null));
        verify(c).setTitle("New title");
        verify(c).touchUpdated();
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
                                        userId, convId, new PatchConversationRequest(null, "bad-uuid", null, null)));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(conversationRepository, never()).save(any());
    }

    @Test
    void patchConversation_setsPresetWhenValid() {
        UUID userId = UUID.randomUUID();
        UUID convId = UUID.randomUUID();
        UUID presetId = UUID.randomUUID();
        ConversationEntity c = mock(ConversationEntity.class);
        when(projectAccessService.requireConversationForUser(userId, convId)).thenReturn(c);
        RagPresetEntity preset = mock(RagPresetEntity.class);
        when(presetService.requireVisiblePreset(userId, presetId)).thenReturn(preset);

        service.patchConversation(
                userId, convId, new PatchConversationRequest(null, presetId.toString(), null, null));
        verify(c).setPreset(preset);
        verify(c).touchUpdated();
        verify(conversationRepository).save(c);
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
}
