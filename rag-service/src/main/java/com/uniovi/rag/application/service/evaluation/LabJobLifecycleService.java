package com.uniovi.rag.application.service.evaluation;

import com.uniovi.rag.domain.AsyncTaskStatus;
import com.uniovi.rag.infrastructure.persistence.AsyncTaskRepository;
import com.uniovi.rag.infrastructure.persistence.EvaluationRunRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationRunEntity;
import com.uniovi.rag.interfaces.rest.dto.ActiveLabJobDto;
import com.uniovi.rag.application.service.async.AsyncTaskMutationService;
import com.uniovi.rag.application.service.evaluation.EvaluationCanonicalPersistenceService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LabJobLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(LabJobLifecycleService.class);

    private static final List<AsyncTaskStatus> ACTIVE_STATUSES =
            List.of(AsyncTaskStatus.QUEUED, AsyncTaskStatus.RUNNING, AsyncTaskStatus.CANCELLING);

    private final EvaluationRunRepository evaluationRunRepository;
    private final AsyncTaskRepository asyncTaskRepository;
    private final AsyncTaskMutationService asyncTaskMutationService;
    private final EvaluationCanonicalPersistenceService evaluationCanonicalPersistenceService;

    public LabJobLifecycleService(
            EvaluationRunRepository evaluationRunRepository,
            AsyncTaskRepository asyncTaskRepository,
            AsyncTaskMutationService asyncTaskMutationService,
            EvaluationCanonicalPersistenceService evaluationCanonicalPersistenceService) {
        this.evaluationRunRepository = evaluationRunRepository;
        this.asyncTaskRepository = asyncTaskRepository;
        this.asyncTaskMutationService = asyncTaskMutationService;
        this.evaluationCanonicalPersistenceService = evaluationCanonicalPersistenceService;
    }

    @Transactional(readOnly = true)
    public List<ActiveLabJobDto> listActiveJobs(UUID userId) {
        if (userId == null) {
            return List.of();
        }
        List<ActiveLabJobDto> out = evaluationRunRepository.findActiveRunsByUser(userId, ACTIVE_STATUSES).stream()
                .map(this::toDto)
                .toList();
        if (!out.isEmpty()) {
            log.info("lab_job_recovered userId={} activeCount={}", userId, out.size());
        }
        return out;
    }

    @Transactional(readOnly = true)
    public ActiveLabJobDto findFirstActiveJobForScope(UUID userId, UUID projectIdOrNull) {
        if (userId == null) {
            return null;
        }
        List<EvaluationRunEntity> runs =
                evaluationRunRepository.findActiveRunsByUserAndProjectScope(userId, projectIdOrNull, ACTIVE_STATUSES);
        return runs.isEmpty() ? null : toDto(runs.getFirst());
    }

    @Transactional
    public void cancelEvaluationJob(UUID userId, UUID taskId) {
        if (userId == null || taskId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing userId/taskId");
        }
        EvaluationRunEntity run = evaluationRunRepository.findByAsyncTask_Id(taskId).orElse(null);
        if (run == null || run.getUser() == null || run.getUser().getId() == null || !userId.equals(run.getUser().getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found");
        }
        AsyncTaskStatus st = asyncTaskRepository.findById(taskId).map(t -> t.getStatus()).orElse(null);
        if (st == AsyncTaskStatus.SUCCEEDED || st == AsyncTaskStatus.FAILED || st == AsyncTaskStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Job already finished");
        }
        String reason = "Cancellation requested by user";
        log.info(
                "lab_job_cancel_requested taskId={} runId={} benchmarkKind={}",
                taskId,
                run.getId(),
                run.getBenchmarkKind());
        asyncTaskMutationService.requestCancellation(taskId, reason);
    }

    private ActiveLabJobDto toDto(EvaluationRunEntity run) {
        UUID taskId = run.getAsyncTask() != null ? run.getAsyncTask().getId() : null;
        String status = run.getAsyncTask() != null && run.getAsyncTask().getStatus() != null
                ? run.getAsyncTask().getStatus().name()
                : "UNKNOWN";
        String progress = run.getAsyncTask() != null ? run.getAsyncTask().getProgressText() : null;
        Instant startedAt = run.getAsyncTask() != null ? run.getAsyncTask().getStartedAt() : null;
        Instant updatedAt = run.getAsyncTask() != null ? run.getAsyncTask().getUpdatedAt() : null;
        boolean cancellable = run.getAsyncTask() != null && run.getAsyncTask().isTerminal() == false;
        UUID projectId = run.getProject() != null ? run.getProject().getId() : null;
        UUID datasetId = run.getDataset() != null ? run.getDataset().getId() : null;
        return new ActiveLabJobDto(
                taskId,
                run.getBenchmarkKind(),
                run.getId(),
                projectId,
                datasetId,
                status,
                progress,
                startedAt,
                updatedAt,
                taskId != null ? "/lab/jobs/" + taskId : null,
                taskId != null ? "/lab/jobs/" + taskId + "/events" : null,
                cancellable);
    }
}

