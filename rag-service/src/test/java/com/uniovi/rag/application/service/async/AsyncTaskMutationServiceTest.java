package com.uniovi.rag.application.service.async;

import com.uniovi.rag.application.service.evaluation.LabJobEventService;
import com.uniovi.rag.application.service.evaluation.LabJobProgressTracker;
import com.uniovi.rag.domain.AsyncTaskStatus;
import com.uniovi.rag.domain.AsyncTaskType;
import com.uniovi.rag.infrastructure.persistence.jpa.AsyncTaskEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.UserEntity;
import com.uniovi.rag.infrastructure.persistence.AsyncTaskRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AsyncTaskMutationServiceTest {

    @Mock
    private AsyncTaskRepository asyncTaskRepository;

    @Mock
    private LabJobEventService labJobEventService;

    @Mock
    private LabJobProgressTracker labJobProgressTracker;

    @InjectMocks
    private AsyncTaskMutationService mutationService;

    private static AsyncTaskEntity queuedEntity() {
        UserEntity user = mock(UserEntity.class);
        return AsyncTaskEntity.queued(user, AsyncTaskType.EVAL_LLM, Map.of(), Instant.now());
    }

    private static void assignId(AsyncTaskEntity e, UUID id) {
        ReflectionTestUtils.setField(e, "id", id);
    }

    @Test
    void markRunning_setsRunningAndProgress() {
        UUID id = UUID.randomUUID();
        AsyncTaskEntity e = queuedEntity();
        assignId(e, id);
        when(asyncTaskRepository.findById(id)).thenReturn(Optional.of(e));

        mutationService.markRunning(id);

        assertThat(e.getStatus()).isEqualTo(AsyncTaskStatus.RUNNING);
        assertThat(e.getStartedAt()).isNotNull();
        assertThat(e.getProgressText()).contains("Running");
        verify(asyncTaskRepository).save(e);
    }

    @Test
    void markSucceeded_persistsResult() {
        UUID id = UUID.randomUUID();
        AsyncTaskEntity e = queuedEntity();
        assignId(e, id);
        when(asyncTaskRepository.findById(id)).thenReturn(Optional.of(e));
        Map<String, Object> result = Map.of("ok", true);

        mutationService.markSucceeded(id, result);

        assertThat(e.getStatus()).isEqualTo(AsyncTaskStatus.SUCCEEDED);
        assertThat(e.getResultJson()).isEqualTo(result);
        assertThat(e.getCompletedAt()).isNotNull();
        verify(asyncTaskRepository).save(e);
    }

    @Test
    void markFailed_persistsError() {
        UUID id = UUID.randomUUID();
        AsyncTaskEntity e = queuedEntity();
        assignId(e, id);
        when(asyncTaskRepository.findById(id)).thenReturn(Optional.of(e));

        mutationService.markFailed(id, "boom");

        assertThat(e.getStatus()).isEqualTo(AsyncTaskStatus.FAILED);
        assertThat(e.getErrorMessage()).isEqualTo("boom");
        assertThat(e.getResultJson()).containsEntry("phase", "failed");
        verify(asyncTaskRepository).save(e);
    }

    @Test
    void markFailed_stripsHtmlProxyBodies() {
        UUID id = UUID.randomUUID();
        AsyncTaskEntity e = queuedEntity();
        assignId(e, id);
        when(asyncTaskRepository.findById(id)).thenReturn(Optional.of(e));

        mutationService.markFailed(id, "<html><body>502</body></html>");

        assertThat(e.getErrorMessage()).isEqualTo("Job failed");
        assertThat(e.getResultJson()).containsEntry("phase", "failed");
    }

    @Test
    void markFailed_withFailureCode_recordsCodeInResultJson() {
        UUID id = UUID.randomUUID();
        AsyncTaskEntity e = queuedEntity();
        assignId(e, id);
        when(asyncTaskRepository.findById(id)).thenReturn(Optional.of(e));

        mutationService.markFailed(id, "x", "CHAT_DOCUMENT_SCOPE_EMPTY");

        assertThat(e.getResultJson()).containsEntry("failureCode", "CHAT_DOCUMENT_SCOPE_EMPTY");
        assertThat(e.getResultJson()).containsEntry("phase", "failed");
    }

    @Test
    void markFailed_truncatesVeryLongProgress() {
        UUID id = UUID.randomUUID();
        AsyncTaskEntity e = queuedEntity();
        assignId(e, id);
        when(asyncTaskRepository.findById(id)).thenReturn(Optional.of(e));
        String big = "x".repeat(20_000);

        mutationService.markFailed(id, big);

        assertThat(e.getProgressText()).hasSizeLessThanOrEqualTo(12_000);
    }

    @Test
    void requestCancellation_setsCancellingAndProgress() {
        UUID id = UUID.randomUUID();
        AsyncTaskEntity e = queuedEntity();
        assignId(e, id);
        when(asyncTaskRepository.findById(id)).thenReturn(Optional.of(e));

        mutationService.requestCancellation(id, "user stop");

        assertThat(e.getStatus()).isEqualTo(AsyncTaskStatus.CANCELLING);
        assertThat(e.getErrorMessage()).isEqualTo("user stop");
        assertThat(e.getProgressText()).contains("user stop");
        verify(asyncTaskRepository).save(e);
    }

    @Test
    void requestCancellation_terminal_isNoop() {
        UUID id = UUID.randomUUID();
        AsyncTaskEntity e = queuedEntity();
        assignId(e, id);
        e.setStatus(AsyncTaskStatus.SUCCEEDED);
        when(asyncTaskRepository.findById(id)).thenReturn(Optional.of(e));

        mutationService.requestCancellation(id, "late");

        assertThat(e.getStatus()).isEqualTo(AsyncTaskStatus.SUCCEEDED);
        verify(asyncTaskRepository).findById(id);
    }

    @Test
    void markCancelled_setsTerminalCancelled() {
        UUID id = UUID.randomUUID();
        AsyncTaskEntity e = queuedEntity();
        assignId(e, id);
        e.setStatus(AsyncTaskStatus.CANCELLING);
        when(asyncTaskRepository.findById(id)).thenReturn(Optional.of(e));

        mutationService.markCancelled(id, "stopped");

        assertThat(e.getStatus()).isEqualTo(AsyncTaskStatus.CANCELLED);
        assertThat(e.getCompletedAt()).isNotNull();
        assertThat(e.getProgressText()).contains("Cancelled");
        verify(asyncTaskRepository).save(e);
    }

    @Test
    void appendProgressLine_appendsText() {
        UUID id = UUID.randomUUID();
        AsyncTaskEntity e = queuedEntity();
        assignId(e, id);
        e.setProgressText("a");
        when(asyncTaskRepository.findById(id)).thenReturn(Optional.of(e));

        mutationService.appendProgressLine(id, "b");

        ArgumentCaptor<AsyncTaskEntity> cap = ArgumentCaptor.forClass(AsyncTaskEntity.class);
        verify(asyncTaskRepository).save(cap.capture());
        assertThat(cap.getValue().getProgressText()).contains("a").contains("b");
    }
}
