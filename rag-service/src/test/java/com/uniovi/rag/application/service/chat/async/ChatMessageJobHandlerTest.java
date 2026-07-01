package com.uniovi.rag.application.service.chat.async;

import com.uniovi.rag.application.exception.RagServiceException;
import com.uniovi.rag.application.result.chat.ChatSource;
import com.uniovi.rag.application.result.chat.QueryResponse;
import com.uniovi.rag.application.service.ChatMessageWorkService;
import com.uniovi.rag.application.service.chat.ChatRetrievalSourceContributor;
import com.uniovi.rag.domain.AsyncTaskType;
import com.uniovi.rag.domain.exception.ErrorCode;
import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.infrastructure.persistence.jpa.AsyncTaskEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.ProjectEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.UserEntity;
import com.uniovi.rag.application.service.async.AsyncTaskMutationService;
import com.uniovi.rag.application.service.runtime.execution.RuntimeQueryExecutionService;
import com.uniovi.rag.infrastructure.observability.RuntimeObservability;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatMessageJobHandlerTest {

    @Mock
    private RuntimeQueryExecutionService runtimeQueryExecutionService;

    @Mock
    private ChatJobCancellationRegistry cancellationRegistry;

    @Mock
    private ChatMessageWorkService chatMessageWorkService;

    @Mock
    private ChatRetrievalSourceContributor chatRetrievalSourceContributor;

    @Mock
    private AsyncTaskMutationService mutation;

    @Mock
    private ObjectProvider<RuntimeObservability> runtimeObservability;

    @Mock
    private RuntimeObservability observability;

    @Mock
    private com.uniovi.rag.configuration.RagRuntimeProperties ragRuntimeProperties;

    @InjectMocks
    private ChatMessageJobHandler handler;

    @BeforeEach
    void stubSourceContributor() {
        lenient().when(ragRuntimeProperties.hasSecondaryModel()).thenReturn(false);
        lenient()
                .when(chatRetrievalSourceContributor.buildSources(any(), any(), any(), any(), any()))
                .thenReturn(List.of());
    }

    @Test
    void taskType_isChatMessage() {
        assertEquals(AsyncTaskType.CHAT_MESSAGE, handler.taskType());
    }

    @Test
    void run_nullPayload_marksFailed() {
        UUID taskId = UUID.randomUUID();
        AsyncTaskEntity task = Mockito.mock(AsyncTaskEntity.class);
        when(task.getId()).thenReturn(taskId);
        when(task.getRequestPayload()).thenReturn(null);

        handler.run(task, mutation);

        verify(mutation).markFailed(taskId, "Missing request payload");
        verifyNoMoreInteractions(runtimeQueryExecutionService, chatMessageWorkService);
    }

    @Test
    void run_missingProject_marksFailed() {
        UUID taskId = UUID.randomUUID();
        AsyncTaskEntity task = Mockito.mock(AsyncTaskEntity.class);
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
        AsyncTaskEntity task = Mockito.mock(AsyncTaskEntity.class);
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
        AsyncTaskEntity task = Mockito.mock(AsyncTaskEntity.class);
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

        AsyncTaskEntity task = Mockito.mock(AsyncTaskEntity.class);
        when(task.getId()).thenReturn(taskId);
        when(task.getUser()).thenReturn(user);
        when(task.getProject()).thenReturn(project);
        when(task.getRequestPayload()).thenReturn(validPayload(convId, asstId));

        when(cancellationRegistry.isCancelled(taskId)).thenReturn(false);
        when(runtimeQueryExecutionService.generateResponseForChat(
                        eq("hello"),
                        eq("m1"),
                        eq(userId),
                        eq(projectId),
                        eq(convId),
                        eq(List.of("d1")),
                        any()))
                .thenReturn(QueryResponse.fromLLMWithSources(
                        "answer text here",
                        QueryType.BOOLEAN_QUERY,
                        List.of(new ChatSource("s1", null, "f.pdf", "snip", 0.5, "distance", 1, null, null))));
        when(chatMessageWorkService.currentTraceId()).thenReturn("trace-1");

        handler.run(task, mutation);

        verify(chatMessageWorkService)
                .applyAssistantSuccess(
                        eq(asstId),
                        eq(convId),
                        eq("answer text here"),
                        eq(List.of(new ChatSource("s1", null, "f.pdf", "snip", 0.5, "distance", 1, null, null))),
                        eq("BOOLEAN_QUERY"),
                        eq("trace-1"),
                        any(),
                        eq("m1"),
                        any(),
                        eq(Map.of()));
        verify(mutation).markSucceeded(eq(taskId), any());
        verify(cancellationRegistry).clear(taskId);
    }

    @Test
    void run_lateCancellationWithNonEmptyAnswer_persistsSuccess() {
        UUID taskId = UUID.randomUUID();
        UUID convId = UUID.randomUUID();
        UUID asstId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        UserEntity user = mockUser(userId);
        ProjectEntity project = mockProject(projectId);

        AsyncTaskEntity task = Mockito.mock(AsyncTaskEntity.class);
        when(task.getId()).thenReturn(taskId);
        when(task.getUser()).thenReturn(user);
        when(task.getProject()).thenReturn(project);
        when(task.getRequestPayload()).thenReturn(validPayload(convId, asstId));

        when(cancellationRegistry.isCancelled(taskId)).thenReturn(false, true);
        when(runtimeQueryExecutionService.generateResponseForChat(
                        eq("hello"),
                        eq("m1"),
                        eq(userId),
                        eq(projectId),
                        eq(convId),
                        eq(List.of("d1")),
                        any()))
                .thenReturn(QueryResponse.fromLLMWithSources(
                        "deterministic answer",
                        QueryType.BOOLEAN_QUERY,
                        List.of(new ChatSource("s1", null, "f.pdf", "snip", 0.5, "distance", 1, null, null))));
        when(chatMessageWorkService.currentTraceId()).thenReturn("trace-1");

        handler.run(task, mutation);

        verify(chatMessageWorkService)
                .applyAssistantSuccess(
                        eq(asstId),
                        eq(convId),
                        eq("deterministic answer"),
                        any(),
                        eq("BOOLEAN_QUERY"),
                        eq("trace-1"),
                        any(),
                        eq("m1"),
                        any(),
                        eq(Map.of()));
        verify(mutation).markSucceeded(eq(taskId), any());
        verify(chatMessageWorkService, never()).applyAssistantCancelled(asstId, convId);
        verify(mutation, never()).markCancelled(taskId, "Stopped");
        verify(cancellationRegistry).clear(taskId);
    }

    @Test
    void run_ragServiceException_marksFailedWithPublicMessage() {
        UUID taskId = UUID.randomUUID();
        UUID convId = UUID.randomUUID();
        UUID asstId = UUID.randomUUID();

        AsyncTaskEntity task = Mockito.mock(AsyncTaskEntity.class);
        UserEntity user = mockUser();
        ProjectEntity project = mockProject();
        when(task.getId()).thenReturn(taskId);
        when(task.getUser()).thenReturn(user);
        when(task.getProject()).thenReturn(project);
        when(task.getRequestPayload()).thenReturn(validPayload(convId, asstId));

        when(cancellationRegistry.isCancelled(taskId)).thenReturn(false);
        when(runtimeQueryExecutionService.generateResponseForChat(any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(
                        new RagServiceException(
                                ErrorCode.INTERNAL_ERROR,
                                HttpStatus.BAD_REQUEST,
                                "public-msg",
                                "internal",
                                null));
        when(runtimeObservability.getIfAvailable()).thenReturn(observability);

        handler.run(task, mutation);

        verify(chatMessageWorkService).applyAssistantError(asstId, convId, "public-msg");
        verify(mutation).markFailed(taskId, "public-msg", "INTERNAL_ERROR");
        verify(observability).chatFailed("INTERNAL_ERROR");
        verify(cancellationRegistry).clear(taskId);
    }

    @Test
    void run_genericException_marksFailedWithMessage() {
        UUID taskId = UUID.randomUUID();
        UUID convId = UUID.randomUUID();
        UUID asstId = UUID.randomUUID();

        AsyncTaskEntity task = Mockito.mock(AsyncTaskEntity.class);
        UserEntity user = mockUser();
        ProjectEntity project = mockProject();
        when(task.getId()).thenReturn(taskId);
        when(task.getUser()).thenReturn(user);
        when(task.getProject()).thenReturn(project);
        when(task.getRequestPayload()).thenReturn(validPayload(convId, asstId));

        when(cancellationRegistry.isCancelled(taskId)).thenReturn(false);
        when(runtimeQueryExecutionService.generateResponseForChat(any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("boom"));
        when(runtimeObservability.getIfAvailable()).thenReturn(observability);

        handler.run(task, mutation);

        verify(chatMessageWorkService).applyAssistantError(asstId, convId, "boom");
        verify(mutation).markFailed(taskId, "boom");
        verify(observability).chatFailed("unknown");
        verify(cancellationRegistry).clear(taskId);
    }

    @Test
    void run_genericException_withHtml_doesNotLeakMarkupToAssistantOrTask() {
        UUID taskId = UUID.randomUUID();
        UUID convId = UUID.randomUUID();
        UUID asstId = UUID.randomUUID();

        AsyncTaskEntity task = Mockito.mock(AsyncTaskEntity.class);
        UserEntity user = mockUser();
        ProjectEntity project = mockProject();
        when(task.getId()).thenReturn(taskId);
        when(task.getUser()).thenReturn(user);
        when(task.getProject()).thenReturn(project);
        when(task.getRequestPayload()).thenReturn(validPayload(convId, asstId));

        when(cancellationRegistry.isCancelled(taskId)).thenReturn(false);
        when(runtimeQueryExecutionService.generateResponseForChat(any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("<html><body>502</body></html>"));

        handler.run(task, mutation);

        verify(chatMessageWorkService)
                .applyAssistantError(asstId, convId, "Something went wrong while generating a reply.");
        verify(mutation).markFailed(taskId, "Something went wrong while generating a reply.");
        verify(cancellationRegistry).clear(taskId);
    }

    @Test
    void ragAnswerIncludesSources_whenPipelineOmitsThem() {
        UUID taskId = UUID.randomUUID();
        UUID convId = UUID.randomUUID();
        UUID asstId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        UserEntity user = mockUser(userId);
        ProjectEntity project = mockProject(projectId);

        AsyncTaskEntity task = Mockito.mock(AsyncTaskEntity.class);
        when(task.getId()).thenReturn(taskId);
        when(task.getUser()).thenReturn(user);
        when(task.getProject()).thenReturn(project);
        when(task.getRequestPayload()).thenReturn(validPayload(convId, asstId));

        when(cancellationRegistry.isCancelled(taskId)).thenReturn(false);
        when(runtimeQueryExecutionService.generateResponseForChat(
                        eq("hello"),
                        eq("m1"),
                        eq(userId),
                        eq(projectId),
                        eq(convId),
                        eq(List.of("d1")),
                        any()))
                .thenReturn(QueryResponse.fromTool("tool answer", "getDuration", QueryType.GET_DURATION));
        when(chatRetrievalSourceContributor.buildSources(
                        eq(userId), eq(projectId), eq(convId), eq(List.of("d1")), eq("hello")))
                .thenReturn(List.of(Map.of(
                        "document_id", "doc-1",
                        "filename", "acta-1.txt",
                        "snippet", "fecha del acta",
                        "distance", 0.12)));
        when(chatMessageWorkService.currentTraceId()).thenReturn("trace-1");

        handler.run(task, mutation);

        verify(chatMessageWorkService)
                .applyAssistantSuccess(
                        eq(asstId),
                        eq(convId),
                        eq("tool answer"),
                        eq(List.of(new ChatSource("doc-1", null, "acta-1.txt", "fecha del acta", 0.12, "distance", null, null, null))),
                        eq("GET_DURATION"),
                        eq("trace-1"),
                        any(),
                        eq("m1"),
                        any(),
                        eq(Map.of()));
        verify(mutation).markSucceeded(eq(taskId), any());
    }

    private static UserEntity mockUser() {
        return mockUser(UUID.randomUUID());
    }

    private static UserEntity mockUser(UUID id) {
        UserEntity u = Mockito.mock(UserEntity.class);
        when(u.getId()).thenReturn(id);
        return u;
    }

    private static ProjectEntity mockProject() {
        return mockProject(UUID.randomUUID());
    }

    private static ProjectEntity mockProject(UUID id) {
        ProjectEntity p = Mockito.mock(ProjectEntity.class);
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
