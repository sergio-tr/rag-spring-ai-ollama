package com.uniovi.rag.application.service.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uniovi.rag.domain.AsyncTaskStatus;
import com.uniovi.rag.domain.AsyncTaskType;
import com.uniovi.rag.infrastructure.persistence.AsyncTaskRepository;
import com.uniovi.rag.infrastructure.persistence.EvaluationRunRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.AsyncTaskEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationRunEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.UserEntity;
import com.uniovi.rag.application.service.async.AsyncTaskMutationService;
import com.uniovi.rag.configuration.RagApiPathProperties;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mockito;

@ExtendWith(MockitoExtension.class)
class LabJobLifecycleServiceTest {

    @Mock
    private EvaluationRunRepository evaluationRunRepository;

    @Mock
    private AsyncTaskRepository asyncTaskRepository;

    @Mock
    private AsyncTaskMutationService asyncTaskMutationService;

    @Mock
    private EvaluationCanonicalPersistenceService evaluationCanonicalPersistenceService;

    @Mock
    private RagApiPathProperties apiPathProperties;

    @InjectMocks
    private LabJobLifecycleService service;

    @Test
    void listActiveJobs_excludesTerminalAndStale() {
        Mockito.lenient().when(apiPathProperties.getProductBasePath()).thenReturn("/api/v5");
        UUID userId = UUID.randomUUID();
        EvaluationRunEntity fresh = runWithStatus(userId, AsyncTaskStatus.RUNNING, Instant.now().minusSeconds(60));
        EvaluationRunEntity stale = runWithStatus(userId, AsyncTaskStatus.RUNNING, Instant.now().minusSeconds(86_400));
        EvaluationRunEntity done = runWithStatus(userId, AsyncTaskStatus.SUCCEEDED, Instant.now());

        when(evaluationRunRepository.findActiveRunsByUser(
                        userId, List.of(AsyncTaskStatus.QUEUED, AsyncTaskStatus.RUNNING, AsyncTaskStatus.CANCELLING)))
                .thenReturn(List.of(fresh, stale, done));

        var active = service.listActiveJobs(userId);

        assertThat(active).hasSize(1);
        assertThat(active.getFirst().jobId()).isEqualTo(fresh.getAsyncTask().getId());
    }

    @Test
    void cancelEvaluationJob_whenCancelling_forcesTerminalCancelled() {
        UUID userId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        UserEntity user = Mockito.mock(UserEntity.class);
        lenient().when(user.getId()).thenReturn(userId);
        AsyncTaskEntity task = Mockito.spy(
                AsyncTaskEntity.queued(user, AsyncTaskType.EVAL_LLM, Map.of(), Instant.now()));
        lenient().when(task.getId()).thenReturn(taskId);
        task.setStatus(AsyncTaskStatus.CANCELLING);
        EvaluationRunEntity run = new EvaluationRunEntity();
        run.setId(runId);
        run.setAsyncTask(task);
        run.setUser(user);
        run.setBenchmarkKind("LLM_JUDGE_QA");

        when(evaluationRunRepository.findByAsyncTask_Id(taskId)).thenReturn(Optional.of(run));
        when(asyncTaskRepository.findById(taskId)).thenReturn(Optional.of(task));

        service.cancelEvaluationJob(userId, taskId);

        verify(asyncTaskMutationService).markCancelled(taskId, "Cancellation requested by user");
        verify(evaluationCanonicalPersistenceService, Mockito.never()).markRunCancelled(Mockito.any(), Mockito.any());
        verify(asyncTaskMutationService, Mockito.never()).requestCancellation(Mockito.any(), Mockito.any());
    }

    private static EvaluationRunEntity runWithStatus(UUID userId, AsyncTaskStatus status, Instant updatedAt) {
        UserEntity user = Mockito.mock(UserEntity.class);
        lenient().when(user.getId()).thenReturn(userId);
        AsyncTaskEntity task =
                AsyncTaskEntity.queued(user, AsyncTaskType.EVAL_RAG, Map.of(), updatedAt);
        task.setStatus(status);
        task.setUpdatedAt(updatedAt);
        EvaluationRunEntity run = new EvaluationRunEntity();
        run.setAsyncTask(task);
        run.setUser(user);
        run.setBenchmarkKind("RAG_PRESET_END_TO_END");
        return run;
    }
}
