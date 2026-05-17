package com.uniovi.rag.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uniovi.rag.application.port.AfterCommitTaskScheduler;
import com.uniovi.rag.application.service.chat.RuntimeConfigurationInvalidException;
import com.uniovi.rag.application.service.runtime.config.RuntimeConfigValidationService;
import com.uniovi.rag.infrastructure.persistence.AsyncTaskRepository;
import com.uniovi.rag.infrastructure.persistence.ConversationDraftRepository;
import com.uniovi.rag.infrastructure.persistence.ConversationRepository;
import com.uniovi.rag.infrastructure.persistence.MessageRepository;
import com.uniovi.rag.infrastructure.persistence.UserRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.ConversationEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.ProjectEntity;
import com.uniovi.rag.interfaces.rest.dto.PostMessageRequest;
import com.uniovi.rag.interfaces.rest.dto.RuntimeConfigValidateResponse;
import com.uniovi.rag.interfaces.rest.dto.RuntimeConfigValidationIssueDto;
import com.uniovi.rag.interfaces.rest.dto.RuntimeIndexCompatibilityDto;
import com.uniovi.rag.service.async.AsyncLabTaskRunner;
import com.uniovi.rag.service.async.AsyncTaskMutationService;
import com.uniovi.rag.service.async.chat.ChatJobCancellationRegistry;
import com.uniovi.rag.service.config.ChatPresetDefaults;
import com.uniovi.rag.service.project.ProjectAccessService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChatMessageApplicationServiceValidationTest {

    private ProjectAccessService projectAccessService;
    private MessageRepository messageRepository;
    private AsyncTaskRepository asyncTaskRepository;
    private AfterCommitTaskScheduler afterCommitTaskScheduler;
    private RuntimeConfigValidationService runtimeConfigValidationService;
    private ChatPresetDefaults chatPresetDefaults;
    private ChatMessageApplicationService sut;

    @BeforeEach
    void setup() {
        projectAccessService = mock(ProjectAccessService.class);
        messageRepository = mock(MessageRepository.class);
        asyncTaskRepository = mock(AsyncTaskRepository.class);
        afterCommitTaskScheduler = mock(AfterCommitTaskScheduler.class);
        runtimeConfigValidationService = mock(RuntimeConfigValidationService.class);
        chatPresetDefaults = mock(ChatPresetDefaults.class);
        sut =
                new ChatMessageApplicationService(
                        projectAccessService,
                        messageRepository,
                        mock(ConversationRepository.class),
                        mock(ConversationDraftRepository.class),
                        mock(UserRepository.class),
                        asyncTaskRepository,
                        mock(AsyncLabTaskRunner.class),
                        afterCommitTaskScheduler,
                        mock(ChatJobCancellationRegistry.class),
                        mock(AsyncTaskMutationService.class),
                        mock(ChatMessageWorkService.class),
                        runtimeConfigValidationService,
                        chatPresetDefaults);
    }

    @Test
    void enqueueMessageRejectsInvalidRuntimeBeforePersistingMessages() {
        UUID uid = UUID.randomUUID();
        UUID cid = UUID.randomUUID();
        UUID pid = UUID.randomUUID();
        ConversationEntity conv = mock(ConversationEntity.class);
        ProjectEntity project = mock(ProjectEntity.class);
        when(project.getId()).thenReturn(pid);
        when(conv.getProject()).thenReturn(project);
        when(conv.getPreset()).thenReturn(null);
        when(projectAccessService.requireConversationForUser(uid, cid)).thenReturn(conv);
        when(chatPresetDefaults.effectivePresetIdForApi(null))
                .thenReturn(ChatPresetDefaults.DETERMINISTIC_DEFAULT_CHAT_PRESET_ID);
        when(runtimeConfigValidationService.validate(eq(uid), any()))
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
                                new RuntimeIndexCompatibilityDto(
                                        null,
                                        null,
                                        null,
                                        Map.of(),
                                        false,
                                        null,
                                        null,
                                        true,
                                        "UNKNOWN"),
                                false));

        assertThatThrownBy(
                        () ->
                                sut.enqueueMessage(
                                        uid,
                                        cid,
                                        new PostMessageRequest("hello", null, null)))
                .isInstanceOf(RuntimeConfigurationInvalidException.class)
                .satisfies(
                        ex ->
                                assertThat(((RuntimeConfigurationInvalidException) ex).code())
                                        .isEqualTo("UNSUPPORTED_RUNTIME_CONFIGURATION"));

        verify(messageRepository, never()).save(any());
        verify(asyncTaskRepository, never()).save(any());
        verify(afterCommitTaskScheduler, never()).scheduleAfterCommit(any());
    }
}
