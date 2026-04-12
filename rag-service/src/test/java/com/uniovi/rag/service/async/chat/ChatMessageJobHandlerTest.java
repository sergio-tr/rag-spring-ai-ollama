package com.uniovi.rag.service.async.chat;

import com.uniovi.rag.application.exception.RagServiceException;
import com.uniovi.rag.application.model.QueryResponse;
import com.uniovi.rag.application.service.ChatMessageWorkService;
import com.uniovi.rag.domain.AsyncTaskType;
import com.uniovi.rag.domain.exception.ErrorCode;
import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.infrastructure.persistence.jpa.AsyncTaskEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.ProjectEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.UserEntity;
import com.uniovi.rag.service.async.AsyncTaskMutationService;
import com.uniovi.rag.service.chat.ChatRetrievalSourceContributor;
import com.uniovi.rag.service.query.ProcessQueryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.HttpStatus;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatMessageJobHandlerTest {

    @Mock
    private ProcessQueryService processQueryService;

    @Mock
    private ChatRetrievalSourceContributor chatRetrievalSourceContributor;

    @Mock
    private ChatJobCancellationRegistry cancellationRegistry;

    @Mock
    private ChatMessageWorkService chatMessageWorkService;

    @Mock
    private AsyncTaskMutationService mutation;

    @InjectMocks
    private ChatMessageJobHandler handler;

    @Test
    void taskType_isChatMessage() {
        assertEquals(AsyncTaskType.CHAT_MESSAGE, handler.taskType());
    }

    @Test
    void run_nullPayload_marksFailed() {
        UUID taskId = UUID.randomUUID();
        AsyncTaskEntity task = org.mockito.Mockito.mock(AsyncTaskEntity.class);
        when(task.getId()).thenReturn(taskId);
        when(task.getRequestPayload()).thenReturn(null);

        handler.run(task, mutation);

        verify(mutation).markFailed(taskId, "Missing request payload");
        verifyNoMoreInteractions(processQueryService, chatMessageWorkService);
    }

    @Test
    void run_missingProject_marksFailed() {
        UUID taskId = UUID.randomUUID();
        AsyncTaskEntity task = org.mockito.Mockito.mock(AsyncTaskEntity.class);
        UserEntity user = mockUser();
        when(task.getId()).thenReturn(taskId);
        when(task.getUser()).thenReturn(user);
        when(task.getProject()).thenReturn(null);
        when(task.getRequestPayload()).thenReturn(validPayload());

        handler.run(task, mutation);

        verify(mutation).markFailed(taskId, "Missing project scope on chat task");
    }

    @Test
    void run_blankUserText_marksFailed() {
        UUID taskId = UUID.randomUUID();
        AsyncTaskEntity task = org.mockito.Mockito.mock(AsyncTaskEntity.class);
        UserEntity user = mockUser();
        ProjectEntity project = mockProject();
        when(task.getId()).thenReturn(taskId);
        when(task.getUser()).thenReturn(user);
        when(task.getProject()).thenReturn(project);
        Map<String, Object> p = validPayload();
        p.put(ChatJobPayloadKeys.USER_TEXT, "   ");
        when(task.getRequestPayload()).thenReturn(p);

        handler.run(task, mutation);

        verify(mutation).markFailed(taskId, "Missing userText in payload");
    }

    @Test
    void run_cancelledBeforeGeneration_marksCancelled() {
        UUID taskId = UUID.randomUUID();
        UUID convId = UUID.randomUUID();
        UUID asstId = UUID.randomUUID();
        AsyncTaskEntity task = org.mockito.Mockito.mock(AsyncTaskEntity.class);
        UserEntity user = mockUser();
        ProjectEntity project = mockProject();
        when(task.getId()).thenReturn(taskId);
        when(task.getUser()).thenReturn(user);
        when(task.getProject()).thenReturn(project);
        when(task.getRequestPayload()).thenReturn(validPayload(convId, asstId));
        when(cancellationRegistry.isCancelled(taskId)).thenReturn(true);

        handler.run(task, mutation);

        verify(chatMessageWorkService).markAssistantProcessing(asstId);
        verify(chatMessageWorkService).applyAssistantCancelled(asstId, convId);
        verify(mutation).markCancelled(taskId, "Stopped");
        verify(cancellationRegistry).clear(taskId);
    }

    @Test
    void run_success_marksSucceededWithResult() {
        UUID taskId = UUID.randomUUID();
        UUID convId = UUID.randomUUID();
        UUID asstId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        UserEntity user = mockUser(userId);
        ProjectEntity project = mockProject(projectId);

        AsyncTaskEntity task = org.mockito.Mockito.mock(AsyncTaskEntity.class);
        when(task.getId()).thenReturn(taskId);
        when(task.getUser()).thenReturn(user);
        when(task.getProject()).thenReturn(project);
        when(task.getRequestPayload()).thenReturn(validPayload(convId, asstId));

        when(cancellationRegistry.isCancelled(taskId)).thenReturn(false);
        when(processQueryService.generateResponseForChat(
                        eq("hello"),
                        eq("m1"),
                        eq(userId),
                        eq(projectId),
                        eq(convId),
                        eq(List.of("d1")),
                        any()))
                .thenReturn(QueryResponse.fromLLM("answer text here", QueryType.BOOLEAN_QUERY));
        when(chatRetrievalSourceContributor.buildSources(
                        eq(userId), eq(projectId), eq(convId), eq(List.of("d1")), eq("hello")))
                .thenReturn(List.of(Map.of("id", "s1")));
        when(chatMessageWorkService.currentTraceId()).thenReturn("trace-1");

        handler.run(task, mutation);

        verify(chatMessageWorkService)
                .applyAssistantSuccess(
                        eq(asstId),
                        eq(convId),
                        eq("answer text here"),
                        any(),
                        eq("BOOLEAN_QUERY"),
                        eq("trace-1"),
                        any(),
                        eq("m1"),
                        any());
        verify(mutation).markSucceeded(eq(taskId), any());
        verify(cancellationRegistry).clear(taskId);
    }

    @Test
    void run_ragServiceException_marksFailedWithPublicMessage() {
        UUID taskId = UUID.randomUUID();
        UUID convId = UUID.randomUUID();
        UUID asstId = UUID.randomUUID();

        AsyncTaskEntity task = org.mockito.Mockito.mock(AsyncTaskEntity.class);
        UserEntity user = mockUser();
        ProjectEntity project = mockProject();
        when(task.getId()).thenReturn(taskId);
        when(task.getUser()).thenReturn(user);
        when(task.getProject()).thenReturn(project);
        when(task.getRequestPayload()).thenReturn(validPayload(convId, asstId));

        when(cancellationRegistry.isCancelled(taskId)).thenReturn(false);
        when(processQueryService.generateResponseForChat(any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(
                        new RagServiceException(
                                ErrorCode.INTERNAL_ERROR,
                                HttpStatus.BAD_REQUEST,
                                "public-msg",
                                "internal",
                                null));

        handler.run(task, mutation);

        verify(chatMessageWorkService).applyAssistantError(asstId, convId, "public-msg");
        verify(mutation).markFailed(taskId, "public-msg");
        verify(cancellationRegistry).clear(taskId);
    }

    @Test
    void run_genericException_marksFailedWithMessage() {
        UUID taskId = UUID.randomUUID();
        UUID convId = UUID.randomUUID();
        UUID asstId = UUID.randomUUID();

        AsyncTaskEntity task = org.mockito.Mockito.mock(AsyncTaskEntity.class);
        UserEntity user = mockUser();
        ProjectEntity project = mockProject();
        when(task.getId()).thenReturn(taskId);
        when(task.getUser()).thenReturn(user);
        when(task.getProject()).thenReturn(project);
        when(task.getRequestPayload()).thenReturn(validPayload(convId, asstId));

        when(cancellationRegistry.isCancelled(taskId)).thenReturn(false);
        when(processQueryService.generateResponseForChat(any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("boom"));

        handler.run(task, mutation);

        verify(chatMessageWorkService).applyAssistantError(asstId, convId, "boom");
        verify(mutation).markFailed(taskId, "boom");
        verify(cancellationRegistry).clear(taskId);
    }

    private static UserEntity mockUser() {
        return mockUser(UUID.randomUUID());
    }

    private static UserEntity mockUser(UUID id) {
        UserEntity u = org.mockito.Mockito.mock(UserEntity.class);
        when(u.getId()).thenReturn(id);
        return u;
    }

    private static ProjectEntity mockProject() {
        return mockProject(UUID.randomUUID());
    }

    private static ProjectEntity mockProject(UUID id) {
        ProjectEntity p = org.mockito.Mockito.mock(ProjectEntity.class);
        when(p.getId()).thenReturn(id);
        return p;
    }

    private static Map<String, Object> validPayload() {
        return validPayload(UUID.randomUUID(), UUID.randomUUID());
    }

    private static Map<String, Object> validPayload(UUID conversationId, UUID assistantMessageId) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put(ChatJobPayloadKeys.CONVERSATION_ID, conversationId.toString());
        p.put(ChatJobPayloadKeys.USER_MESSAGE_ID, UUID.randomUUID().toString());
        p.put(ChatJobPayloadKeys.ASSISTANT_MESSAGE_ID, assistantMessageId.toString());
        p.put(ChatJobPayloadKeys.LLM_MODEL, "m1");
        p.put(ChatJobPayloadKeys.USER_TEXT, "hello");
        p.put(ChatJobPayloadKeys.DOCUMENT_FILTER, List.of("d1"));
        return p;
    }
}
