package com.uniovi.rag.service.async;

import com.uniovi.rag.domain.AsyncTaskStatus;
import com.uniovi.rag.domain.AsyncTaskType;
import com.uniovi.rag.infrastructure.persistence.AsyncTaskRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.AsyncTaskEntity;
import com.uniovi.rag.service.async.lab.LabJobHandler;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AsyncLabTaskRunnerTest {

    @Mock
    private AsyncTaskRepository asyncTaskRepository;

    @Mock
    private AsyncTaskMutationService mutation;

    @Mock
    private LabJobHandler chatHandler;

    @BeforeEach
    void defaultHandlerType() {
        lenient().when(chatHandler.taskType()).thenReturn(AsyncTaskType.CHAT_MESSAGE);
    }

    @Test
    void constructor_duplicateTaskType_throws() {
        when(chatHandler.taskType()).thenReturn(AsyncTaskType.CHAT_MESSAGE);
        LabJobHandler dup = Mockito.mock(LabJobHandler.class);
        when(dup.taskType()).thenReturn(AsyncTaskType.CHAT_MESSAGE);

        assertThrows(
                IllegalStateException.class,
                () ->
                        new AsyncLabTaskRunner(
                                asyncTaskRepository, mutation, List.of(chatHandler, dup), null, null, null));
    }

    @Test
    void runQueuedTask_missingTask_returnsEarly() throws Exception {
        AsyncLabTaskRunner runner =
                new AsyncLabTaskRunner(asyncTaskRepository, mutation, List.of(chatHandler), null, null, null);
        UUID taskId = UUID.randomUUID();
        when(asyncTaskRepository.findById(taskId)).thenReturn(Optional.empty());

        invokeRunQueuedTask(runner, taskId);

        verify(mutation, never()).markRunning(any());
    }

    @Test
    void runQueuedTask_notQueued_returnsEarly() throws Exception {
        AsyncLabTaskRunner runner =
                new AsyncLabTaskRunner(asyncTaskRepository, mutation, List.of(chatHandler), null, null, null);
        UUID taskId = UUID.randomUUID();
        AsyncTaskEntity e = Mockito.mock(AsyncTaskEntity.class);
        when(e.getStatus()).thenReturn(AsyncTaskStatus.RUNNING);
        when(asyncTaskRepository.findById(taskId)).thenReturn(Optional.of(e));

        invokeRunQueuedTask(runner, taskId);

        verify(mutation, never()).markRunning(any());
    }

    @Test
    void runQueuedTask_noHandler_marksFailed() throws Exception {
        LabJobHandler onlyChat = Mockito.mock(LabJobHandler.class);
        when(onlyChat.taskType()).thenReturn(AsyncTaskType.CHAT_MESSAGE);
        AsyncLabTaskRunner runner =
                new AsyncLabTaskRunner(asyncTaskRepository, mutation, List.of(onlyChat), null, null, null);
        UUID taskId = UUID.randomUUID();
        AsyncTaskEntity e = Mockito.mock(AsyncTaskEntity.class);
        when(e.getStatus()).thenReturn(AsyncTaskStatus.QUEUED);
        when(e.getTaskType()).thenReturn(AsyncTaskType.OLLAMA_PULL);
        when(asyncTaskRepository.findById(taskId)).thenReturn(Optional.of(e)).thenReturn(Optional.of(e));

        invokeRunQueuedTask(runner, taskId);

        verify(mutation).markRunning(taskId);
        verify(mutation).markFailed(eq(taskId), ArgumentMatchers.contains("No handler"));
    }

    @Test
    void runQueuedTask_invokesHandler() throws Exception {
        AsyncLabTaskRunner runner =
                new AsyncLabTaskRunner(asyncTaskRepository, mutation, List.of(chatHandler), null, null, null);
        UUID taskId = UUID.randomUUID();
        AsyncTaskEntity e = Mockito.mock(AsyncTaskEntity.class);
        when(e.getStatus()).thenReturn(AsyncTaskStatus.QUEUED);
        when(e.getTaskType()).thenReturn(AsyncTaskType.CHAT_MESSAGE);
        when(asyncTaskRepository.findById(taskId)).thenReturn(Optional.of(e)).thenReturn(Optional.of(e));

        invokeRunQueuedTask(runner, taskId);

        verify(mutation).markRunning(taskId);
        verify(chatHandler).run(e, mutation);
    }

    private static void invokeRunQueuedTask(AsyncLabTaskRunner runner, UUID taskId) throws Exception {
        Method m = AsyncLabTaskRunner.class.getDeclaredMethod("runQueuedTask", UUID.class);
        m.setAccessible(true);
        m.invoke(runner, taskId);
    }

}
