package com.uniovi.rag.service.async;

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
        verify(asyncTaskRepository).save(e);
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
