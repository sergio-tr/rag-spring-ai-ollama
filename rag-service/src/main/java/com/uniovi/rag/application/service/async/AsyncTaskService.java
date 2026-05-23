package com.uniovi.rag.application.service.async;

import com.uniovi.rag.interfaces.rest.dto.AsyncTaskStatusDto;
import com.uniovi.rag.domain.AsyncTaskType;
import com.uniovi.rag.infrastructure.persistence.jpa.AsyncTaskEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.ProjectEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.UserEntity;
import com.uniovi.rag.infrastructure.persistence.AsyncTaskRepository;
import com.uniovi.rag.infrastructure.persistence.UserRepository;
import com.uniovi.rag.application.port.AfterCommitTaskScheduler;
import com.uniovi.rag.application.service.evaluation.LabJobEventService;
import com.uniovi.rag.application.service.evaluation.async.LabJobPayloadKeys;
import com.uniovi.rag.domain.LabJobEventType;
import com.uniovi.rag.application.service.project.ProjectAccessService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class AsyncTaskService {

    private final AsyncTaskRepository asyncTaskRepository;
    private final UserRepository userRepository;
    private final ProjectAccessService projectAccessService;
    private final AsyncLabTaskRunner asyncLabTaskRunner;
    private final AfterCommitTaskScheduler afterCommitTaskScheduler;
    private final LabJobEventService labJobEventService;

    public AsyncTaskService(
            AsyncTaskRepository asyncTaskRepository,
            UserRepository userRepository,
            ProjectAccessService projectAccessService,
            AsyncLabTaskRunner asyncLabTaskRunner,
            AfterCommitTaskScheduler afterCommitTaskScheduler,
            LabJobEventService labJobEventService) {
        this.asyncTaskRepository = asyncTaskRepository;
        this.userRepository = userRepository;
        this.projectAccessService = projectAccessService;
        this.asyncLabTaskRunner = asyncLabTaskRunner;
        this.afterCommitTaskScheduler = afterCommitTaskScheduler;
        this.labJobEventService = labJobEventService;
    }

    @Transactional
    public UUID submitEvalLlm(UUID userId) {
        return submitEvalLlm(userId, null);
    }

    @Transactional
    public UUID submitEvalLlm(UUID userId, UUID projectId) {
        return submitEvalLlm(userId, projectId, null);
    }

    @Transactional
    public UUID submitEvalLlm(UUID userId, UUID projectId, UUID evaluationRunId) {
        return enqueue(
                userId,
                projectId,
                AsyncTaskType.EVAL_LLM,
                withEvaluationRunPayload(evaluationRunId));
    }

    @Transactional
    public UUID submitEvalRag(UUID userId) {
        return submitEvalRag(userId, null);
    }

    @Transactional
    public UUID submitEvalRag(UUID userId, UUID projectId) {
        return submitEvalRag(userId, projectId, null);
    }

    @Transactional
    public UUID submitEvalRag(UUID userId, UUID projectId, UUID evaluationRunId) {
        return enqueue(
                userId,
                projectId,
                AsyncTaskType.EVAL_RAG,
                withEvaluationRunPayload(evaluationRunId));
    }

    @Transactional
    public UUID submitEvalEmbeddingRetrieval(UUID userId, UUID projectId, UUID evaluationRunId) {
        return enqueue(
                userId,
                projectId,
                AsyncTaskType.EVAL_EMBEDDING_RETRIEVAL,
                withEvaluationRunPayload(evaluationRunId));
    }

    @Transactional
    public UUID submitClassifierTrain(
            UUID userId,
            MultipartFile file,
            String modelName,
            String labelsJson,
            MultipartFile labelsFile,
            int epochs,
            int batchSize)
            throws IOException {
        return enqueueClassifierTrain(
                new ClassifierTrainSubmission(userId, null, file, modelName, labelsJson, labelsFile, epochs, batchSize));
    }

    @Transactional
    public UUID submitClassifierTrain(
            UUID userId,
            UUID projectId,
            MultipartFile file,
            String modelName,
            String labelsJson,
            MultipartFile labelsFile,
            int epochs,
            int batchSize)
            throws IOException {
        return enqueueClassifierTrain(
                new ClassifierTrainSubmission(
                        userId, projectId, file, modelName, labelsJson, labelsFile, epochs, batchSize));
    }

    private UUID enqueueClassifierTrain(ClassifierTrainSubmission sub) throws IOException {
        Path train = Files.createTempFile("async-train-", ".xlsx");
        Files.write(train, sub.file().getBytes());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(LabJobPayloadKeys.TRAIN_PATH, train.toAbsolutePath().toString());
        payload.put(LabJobPayloadKeys.MODEL_NAME, sub.modelName() != null ? sub.modelName() : "model");
        if (sub.labelsJson() != null && !sub.labelsJson().isBlank()) {
            payload.put(LabJobPayloadKeys.LABELS_JSON, sub.labelsJson());
        }
        payload.put(LabJobPayloadKeys.EPOCHS, sub.epochs());
        payload.put(LabJobPayloadKeys.BATCH_SIZE, sub.batchSize());
        MultipartFile labelsFile = sub.labelsFile();
        if (labelsFile != null && !labelsFile.isEmpty()) {
            String suffix =
                    labelsFile.getOriginalFilename() != null
                                    && labelsFile.getOriginalFilename().contains(".")
                            ? labelsFile
                                    .getOriginalFilename()
                                    .substring(labelsFile.getOriginalFilename().lastIndexOf('.'))
                            : ".txt";
            Path labels = Files.createTempFile("async-labels-", suffix);
            Files.write(labels, labelsFile.getBytes());
            payload.put(LabJobPayloadKeys.LABELS_PATH, labels.toAbsolutePath().toString());
        }
        return enqueue(sub.userId(), sub.projectId(), AsyncTaskType.CLASSIFIER_TRAIN, payload);
    }

    @Transactional
    public UUID submitClassifierEval(
            UUID userId, String modelId, boolean includeImages, MultipartFile datasetFile) throws IOException {
        return submitClassifierEval(userId, null, modelId, includeImages, datasetFile);
    }

    @Transactional
    public UUID submitClassifierEval(
            UUID userId,
            UUID projectId,
            String modelId,
            boolean includeImages,
            MultipartFile datasetFile)
            throws IOException {
        return enqueueClassifierEval(userId, projectId, modelId, includeImages, datasetFile, null);
    }

    @Transactional
    public UUID submitClassifierEval(
            UUID userId,
            UUID projectId,
            String modelId,
            boolean includeImages,
            MultipartFile datasetFile,
            UUID evaluationRunId)
            throws IOException {
        return enqueueClassifierEval(userId, projectId, modelId, includeImages, datasetFile, evaluationRunId);
    }

    private UUID enqueueClassifierEval(
            UUID userId,
            UUID projectId,
            String modelId,
            boolean includeImages,
            MultipartFile datasetFile,
            UUID evaluationRunId)
            throws IOException {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (evaluationRunId != null) {
            payload.put(LabJobPayloadKeys.EVALUATION_RUN_ID, evaluationRunId.toString());
        }
        if (modelId != null && !modelId.isBlank()) {
            payload.put(LabJobPayloadKeys.MODEL_ID, modelId.trim());
        }
        payload.put(LabJobPayloadKeys.INCLUDE_IMAGES, includeImages);
        if (datasetFile != null && !datasetFile.isEmpty()) {
            Path eval = Files.createTempFile("async-eval-", ".xlsx");
            Files.write(eval, datasetFile.getBytes());
            payload.put(LabJobPayloadKeys.EVAL_PATH, eval.toAbsolutePath().toString());
            payload.put(
                    LabJobPayloadKeys.EVAL_FILENAME,
                    datasetFile.getOriginalFilename() != null
                            ? datasetFile.getOriginalFilename()
                            : "eval.xlsx");
        }
        return enqueue(userId, projectId, AsyncTaskType.CLASSIFIER_EVAL, payload);
    }

    @Transactional
    public UUID submitOllamaPull(UUID userId, String model) {
        return submitOllamaPull(userId, null, model);
    }

    @Transactional
    public UUID submitOllamaPull(UUID userId, UUID projectId, String model) {
        Map<String, Object> payload = Map.of(LabJobPayloadKeys.OLLAMA_MODEL, model.trim());
        return enqueue(userId, projectId, AsyncTaskType.OLLAMA_PULL, payload);
    }

    private static Map<String, Object> withEvaluationRunPayload(UUID evaluationRunId) {
        if (evaluationRunId == null) {
            return Map.of();
        }
        return Map.of(LabJobPayloadKeys.EVALUATION_RUN_ID, evaluationRunId.toString());
    }

    private UUID enqueue(UUID userId, UUID projectIdOrNull, AsyncTaskType type, Map<String, Object> payload) {
        UserEntity user = userRepository.findById(userId).orElseThrow();
        ProjectEntity project = null;
        if (projectIdOrNull != null) {
            project = projectAccessService.requireOwnedProject(userId, projectIdOrNull);
        }
        AsyncTaskEntity e = AsyncTaskEntity.queued(user, project, type, payload, Instant.now());
        asyncTaskRepository.save(e);
        UUID taskId = e.getId();
        labJobEventService.recordEvent(taskId, LabJobEventType.ACCEPTED, "Job accepted");
        // Same pattern as ChatMessageApplicationService: @Async runner must see committed QUEUED row.
        afterCommitTaskScheduler.scheduleAfterCommit(() -> asyncLabTaskRunner.execute(taskId));
        return taskId;
    }

    @Transactional
    public UUID submitAccountExport(UUID userId) {
        return enqueue(userId, null, AsyncTaskType.ACCOUNT_EXPORT, Map.of());
    }

    @Transactional
    public UUID submitAccountDeletion(UUID userId) {
        return enqueue(userId, null, AsyncTaskType.ACCOUNT_DELETION, Map.of());
    }

    @Transactional(readOnly = true)
    public AsyncTaskStatusDto getStatus(UUID taskId, UUID userId) {
        AsyncTaskEntity e = asyncTaskRepository
                .findByIdAndUser_Id(taskId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));
        return toDto(e);
    }

    /**
     * Poll only {@link AsyncTaskType#ACCOUNT_EXPORT} / {@link AsyncTaskType#ACCOUNT_DELETION} tasks (not Lab jobs).
     */
    @Transactional(readOnly = true)
    public AsyncTaskStatusDto getAccountJobStatus(UUID taskId, UUID userId) {
        AsyncTaskEntity e = asyncTaskRepository
                .findByIdAndUser_Id(taskId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));
        if (e.getTaskType() != AsyncTaskType.ACCOUNT_EXPORT && e.getTaskType() != AsyncTaskType.ACCOUNT_DELETION) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Not an account job");
        }
        return toDto(e);
    }

    private static AsyncTaskStatusDto toDto(AsyncTaskEntity e) {
        String failureCode = null;
        if (e.getResultJson() != null) {
            Object fc = e.getResultJson().get("failureCode");
            failureCode = fc != null ? fc.toString() : null;
        }
        return new AsyncTaskStatusDto(
                e.getId(),
                e.getTaskType().name(),
                e.getStatus().name(),
                e.getProgressText(),
                e.getResultJson(),
                e.getErrorMessage(),
                e.isTerminal(),
                e.getCreatedAt(),
                e.getUpdatedAt(),
                e.getStartedAt(),
                e.getCompletedAt(),
                failureCode);
    }

    /** Bundles classifier train multipart fields for a single enqueue path (keeps API overloads thin). */
    private record ClassifierTrainSubmission(
            UUID userId,
            UUID projectId,
            MultipartFile file,
            String modelName,
            String labelsJson,
            MultipartFile labelsFile,
            int epochs,
            int batchSize) {}
}
