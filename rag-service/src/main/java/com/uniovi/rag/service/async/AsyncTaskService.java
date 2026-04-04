package com.uniovi.rag.service.async;

import com.uniovi.rag.interfaces.rest.dto.AsyncTaskStatusDto;
import com.uniovi.rag.domain.AsyncTaskType;
import com.uniovi.rag.infrastructure.persistence.jpa.AsyncTaskEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.ProjectEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.UserEntity;
import com.uniovi.rag.infrastructure.persistence.AsyncTaskRepository;
import com.uniovi.rag.infrastructure.persistence.UserRepository;
import com.uniovi.rag.service.async.lab.LabJobPayloadKeys;
import com.uniovi.rag.service.project.ProjectAccessService;
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

    public AsyncTaskService(
            AsyncTaskRepository asyncTaskRepository,
            UserRepository userRepository,
            ProjectAccessService projectAccessService,
            AsyncLabTaskRunner asyncLabTaskRunner) {
        this.asyncTaskRepository = asyncTaskRepository;
        this.userRepository = userRepository;
        this.projectAccessService = projectAccessService;
        this.asyncLabTaskRunner = asyncLabTaskRunner;
    }

    @Transactional
    public UUID submitEvalLlm(UUID userId) {
        return submitEvalLlm(userId, null);
    }

    @Transactional
    public UUID submitEvalLlm(UUID userId, UUID projectId) {
        return enqueue(userId, projectId, AsyncTaskType.EVAL_LLM, Map.of());
    }

    @Transactional
    public UUID submitEvalRag(UUID userId) {
        return submitEvalRag(userId, null);
    }

    @Transactional
    public UUID submitEvalRag(UUID userId, UUID projectId) {
        return enqueue(userId, projectId, AsyncTaskType.EVAL_RAG, Map.of());
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
        return submitClassifierTrain(userId, null, file, modelName, labelsJson, labelsFile, epochs, batchSize);
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
        Path train = Files.createTempFile("async-train-", ".xlsx");
        Files.write(train, file.getBytes());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(LabJobPayloadKeys.TRAIN_PATH, train.toAbsolutePath().toString());
        payload.put(LabJobPayloadKeys.MODEL_NAME, modelName != null ? modelName : "model");
        if (labelsJson != null && !labelsJson.isBlank()) {
            payload.put(LabJobPayloadKeys.LABELS_JSON, labelsJson);
        }
        payload.put(LabJobPayloadKeys.EPOCHS, epochs);
        payload.put(LabJobPayloadKeys.BATCH_SIZE, batchSize);
        if (labelsFile != null && !labelsFile.isEmpty()) {
            String suffix =
                    labelsFile.getOriginalFilename() != null
                                    && labelsFile.getOriginalFilename().contains(".")
                            ? labelsFile.getOriginalFilename().substring(labelsFile.getOriginalFilename().lastIndexOf('.'))
                            : ".txt";
            Path labels = Files.createTempFile("async-labels-", suffix);
            Files.write(labels, labelsFile.getBytes());
            payload.put(LabJobPayloadKeys.LABELS_PATH, labels.toAbsolutePath().toString());
        }
        return enqueue(userId, projectId, AsyncTaskType.CLASSIFIER_TRAIN, payload);
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
        Map<String, Object> payload = new LinkedHashMap<>();
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

    private UUID enqueue(UUID userId, UUID projectIdOrNull, AsyncTaskType type, Map<String, Object> payload) {
        UserEntity user = userRepository.findById(userId).orElseThrow();
        ProjectEntity project = null;
        if (projectIdOrNull != null) {
            project = projectAccessService.requireOwnedProject(userId, projectIdOrNull);
        }
        AsyncTaskEntity e = AsyncTaskEntity.queued(user, project, type, payload, Instant.now());
        asyncTaskRepository.save(e);
        asyncLabTaskRunner.execute(e.getId());
        return e.getId();
    }

    @Transactional(readOnly = true)
    public AsyncTaskStatusDto getStatus(UUID taskId, UUID userId) {
        AsyncTaskEntity e = asyncTaskRepository
                .findByIdAndUser_Id(taskId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));
        return toDto(e);
    }

    private static AsyncTaskStatusDto toDto(AsyncTaskEntity e) {
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
                e.getCompletedAt());
    }
}
