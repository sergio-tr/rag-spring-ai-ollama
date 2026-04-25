package com.uniovi.rag.application.service;

import com.uniovi.rag.application.port.AfterCommitTaskScheduler;
import com.uniovi.rag.domain.AsyncTaskType;
import com.uniovi.rag.domain.MessageRole;
import com.uniovi.rag.infrastructure.persistence.AsyncTaskRepository;
import com.uniovi.rag.infrastructure.persistence.ConversationDraftRepository;
import com.uniovi.rag.infrastructure.persistence.ConversationRepository;
import com.uniovi.rag.infrastructure.persistence.MessageRepository;
import com.uniovi.rag.infrastructure.persistence.UserRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.AsyncTaskEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.ConversationDraftEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.ConversationEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.MessageEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.ProjectEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.UserEntity;
import com.uniovi.rag.interfaces.rest.dto.ChatMessageAcceptedDto;
import com.uniovi.rag.interfaces.rest.dto.ConversationDraftDto;
import com.uniovi.rag.interfaces.rest.dto.PostMessageRequest;
import com.uniovi.rag.service.async.AsyncLabTaskRunner;
import com.uniovi.rag.service.async.AsyncTaskMutationService;
import com.uniovi.rag.service.async.chat.ChatJobCancellationRegistry;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatMessageApplicationServiceTest {

    @Mock
    private ProjectAccessService projectAccessService;

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private ConversationDraftRepository conversationDraftRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AsyncTaskRepository asyncTaskRepository;

    @Mock
    private AsyncLabTaskRunner asyncLabTaskRunner;

    @Mock
    private AfterCommitTaskScheduler afterCommitTaskScheduler;

    @Mock
    private ChatJobCancellationRegistry chatJobCancellationRegistry;

    @Mock
    private AsyncTaskMutationService asyncTaskMutationService;

    @Mock
    private ChatMessageWorkService chatMessageWorkService;

    @InjectMocks
    private ChatMessageApplicationService service;

    @BeforeEach
    void wireAfterCommitToRunImmediately() {
        lenient()
                .doAnswer(
                        inv -> {
                            inv.getArgument(0, Runnable.class).run();
                            return null;
                        })
                .when(afterCommitTaskScheduler)
                .scheduleAfterCommit(any());
    }

    @BeforeEach
    void stubMessageSaveAssignsId() {
        lenient()
                .when(messageRepository.save(any(MessageEntity.class)))
                .thenAnswer(
                        inv -> {
                            MessageEntity m = inv.getArgument(0);
                            if (m.getId() == null) {
                                ReflectionTestUtils.setField(m, "id", UUID.randomUUID());
                            }
                            return m;
                        });
    }

    @BeforeEach
    void stubAsyncTaskSaveAssignsId() {
        lenient()
                .when(asyncTaskRepository.save(any(AsyncTaskEntity.class)))
                .thenAnswer(
                        inv -> {
                            AsyncTaskEntity e = inv.getArgument(0);
                            if (e.getId() == null) {
                                ReflectionTestUtils.setField(e, "id", UUID.randomUUID());
                            }
                            return e;
                        });
    }

    @Test
    void enqueueMessage_blankContent_throwsBadRequest() {
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        ConversationEntity conv = mock(ConversationEntity.class);
        when(projectAccessService.requireConversationForUser(userId, conversationId)).thenReturn(conv);

        ResponseStatusException ex =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                service.enqueueMessage(
                                        userId, conversationId, new PostMessageRequest("   ", null, null)));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void enqueueMessage_newThread_persistsMessagesSchedulesRunner() {
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();

        ConversationEntity conv = mock(ConversationEntity.class);
        ProjectEntity project = mock(ProjectEntity.class);
        when(conv.getProject()).thenReturn(project);
        when(conv.getDocumentFilter()).thenReturn(List.of());
        when(projectAccessService.requireConversationForUser(userId, conversationId)).thenReturn(conv);

        when(asyncTaskRepository.findByUser_IdAndTaskTypeAndStatusIn(
                        eq(userId), eq(AsyncTaskType.CHAT_MESSAGE), any()))
                .thenReturn(List.of());

        when(messageRepository.findMaxSeqByConversationId(conversationId)).thenReturn(0);

        UserEntity user = mock(UserEntity.class);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        ChatMessageAcceptedDto dto =
                service.enqueueMessage(userId, conversationId, new PostMessageRequest("hi", "model-x", null));

        assertThat(dto.jobId()).isNotNull();
        assertThat(dto.userMessageId()).isNotNull();
        assertThat(dto.assistantMessageId()).isNotNull();

        verify(conversationRepository).save(conv);
        ArgumentCaptor<UUID> taskIdCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(asyncLabTaskRunner).execute(taskIdCaptor.capture());
        assertThat(taskIdCaptor.getValue()).isEqualTo(dto.jobId());
    }

    @Test
    void enqueueMessage_continueBranch_reusesUserMessage() {
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID continueId = UUID.randomUUID();

        ConversationEntity conv = mock(ConversationEntity.class);
        when(conv.getId()).thenReturn(conversationId);
        ProjectEntity project = mock(ProjectEntity.class);
        when(conv.getProject()).thenReturn(project);
        when(conv.getDocumentFilter()).thenReturn(null);
        when(projectAccessService.requireConversationForUser(userId, conversationId)).thenReturn(conv);

        when(asyncTaskRepository.findByUser_IdAndTaskTypeAndStatusIn(
                        eq(userId), eq(AsyncTaskType.CHAT_MESSAGE), any()))
                .thenReturn(List.of());

        MessageEntity existingUser = mock(MessageEntity.class);
        when(existingUser.getConversation()).thenReturn(conv);
        when(existingUser.getRole()).thenReturn(MessageRole.USER);
        when(existingUser.getDeletedAt()).thenReturn(null);
        when(existingUser.getContent()).thenReturn("same");
        when(existingUser.getId()).thenReturn(continueId);
        when(messageRepository.findById(continueId)).thenReturn(Optional.of(existingUser));

        when(messageRepository.findMaxSeqByConversationId(conversationId)).thenReturn(4);

        UserEntity user = mock(UserEntity.class);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        ChatMessageAcceptedDto dto =
                service.enqueueMessage(
                        userId, conversationId, new PostMessageRequest("same", null, continueId));

        assertThat(dto.jobId()).isNotNull();
        verify(asyncLabTaskRunner).execute(any(UUID.class));
    }

    @Test
    void editUserMessage_blankContent_throwsBadRequest() {
        ResponseStatusException ex =
                assertThrows(
                        ResponseStatusException.class,
                        () -> service.editUserMessage(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), " "));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void getDraft_missing_returnsEmptyDraft() {
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        when(projectAccessService.requireConversationForUser(userId, conversationId))
                .thenReturn(mock(ConversationEntity.class));
        when(conversationDraftRepository.findById(conversationId)).thenReturn(Optional.empty());

        ConversationDraftDto dto = service.getDraft(userId, conversationId);

        assertThat(dto.content()).isEmpty();
        assertThat(dto.updatedAt()).isEqualTo(Instant.EPOCH);
    }

    @Test
    void putDraft_createsWhenAbsent() {
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();

        ConversationEntity conv = mock(ConversationEntity.class);
        when(projectAccessService.requireConversationForUser(userId, conversationId)).thenReturn(conv);
        when(conversationDraftRepository.findById(conversationId)).thenReturn(Optional.empty());
        when(conversationDraftRepository.save(any(ConversationDraftEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ConversationDraftDto dto = service.putDraft(userId, conversationId, "draft text");

        assertThat(dto.content()).isEqualTo("draft text");
        assertThat(dto.updatedAt()).isNotNull();
    }
}
