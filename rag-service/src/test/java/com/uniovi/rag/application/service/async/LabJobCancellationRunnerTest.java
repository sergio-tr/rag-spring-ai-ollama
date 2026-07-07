package com.uniovi.rag.application.service.async;

import com.uniovi.rag.domain.AsyncTaskStatus;
import com.uniovi.rag.domain.AsyncTaskType;
import com.uniovi.rag.infrastructure.persistence.AsyncTaskRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.AsyncTaskEntity;
import com.uniovi.rag.application.service.evaluation.async.LabJobHandler;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LabJobCancellationRunnerTest {

    @Mock
    private AsyncTaskRepository asyncTaskRepository;

    @Mock
    private AsyncTaskMutationService mutation;

    @Mock
    private LabJobHandler evalHandler;

    @BeforeEach
    void defaultHandlerType() {
        lenient().when(evalHandler.taskType()).thenReturn(AsyncTaskType.EVAL_RAG);
    }

    @Test
    void runQueuedTask_cancelledBeforeStart_marksCancelledNotFailed() throws Exception {
        AsyncLabTaskRunner runner =
                new AsyncLabTaskRunner(asyncTaskRepository, mutation, List.of(evalHandler), null, null, null);
        UUID taskId = UUID.randomUUID();
        AsyncTaskEntity e = Mockito.mock(AsyncTaskEntity.class);
        when(e.getStatus()).thenReturn(AsyncTaskStatus.CANCELLING);
        when(asyncTaskRepository.findById(taskId)).thenReturn(Optional.of(e));

        invokeRunQueuedTask(runner, taskId);

        verify(mutation).markCancelled(taskId, "Cancelled before start");
        verify(mutation, never()).markFailed(any(), any());
        verify(evalHandler, never()).run(any(), any());
    }

    @Test
    void runQueuedTask_handlerThrowsCancelled_marksCancelledNotFailed() throws Exception {
        AsyncLabTaskRunner runner =
                new AsyncLabTaskRunner(asyncTaskRepository, mutation, List.of(evalHandler), null, null, null);
        UUID taskId = UUID.randomUUID();
        AsyncTaskEntity e = Mockito.mock(AsyncTaskEntity.class);
        when(e.getStatus()).thenReturn(AsyncTaskStatus.QUEUED);
        when(e.getTaskType()).thenReturn(AsyncTaskType.EVAL_RAG);
        when(asyncTaskRepository.findById(taskId)).thenReturn(Optional.of(e)).thenReturn(Optional.of(e));
        Mockito.doThrow(new LabJobCancelledException("Cancellation requested by user"))
                .when(evalHandler)
                .run(e, mutation);

        invokeRunQueuedTask(runner, taskId);

        verify(mutation).markCancelled(eq(taskId), eq("Cancellation requested by user"));
        verify(mutation, never()).markFailed(any(), any());
    }

    private static void invokeRunQueuedTask(AsyncLabTaskRunner runner, UUID taskId) throws Exception {
        Method m = AsyncLabTaskRunner.class.getDeclaredMethod("runQueuedTask", UUID.class);
        m.setAccessible(true);
        m.invoke(runner, taskId);
    }
}
