package com.uniovi.rag.application.service.classifier;

import com.uniovi.rag.domain.ClassifierModelStatus;
import com.uniovi.rag.application.port.ClassifierLabPort;
import com.uniovi.rag.infrastructure.persistence.ClassifierModelRepository;
import com.uniovi.rag.infrastructure.persistence.UserRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.ClassifierModelEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.ClassifierModelEntityFactory;
import com.uniovi.rag.infrastructure.persistence.jpa.UserEntity;
import com.uniovi.rag.interfaces.rest.dto.ClassifierModelResponseDto;
import com.uniovi.rag.application.service.config.UserProjectConfigurationService;
import com.uniovi.rag.application.service.project.ProjectAccessService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Persists classifier train results in {@code classifier_model}, optional eval metrics enrichment,
 * and explicit per-project activation (writes {@code classifierModelId} into project RAG config).
 */
@Service
public class ClassifierModelRegistryService {

    public static final String HP_SOURCE_TASK_ID = "sourceTaskId";
    /** Synthetic catalog row for the built-in classifier tag (see {@link #classifierSystemInferenceTag}). */
    public static final String HP_SYSTEM_CATALOG = "systemCatalog";
    public static final String HP_OWNER_ID = "ownerId";
    public static final String HP_EPOCHS = "epochs";
    public static final String HP_BATCH_SIZE = "batchSize";

    private static final Logger log = LoggerFactory.getLogger(ClassifierModelRegistryService.class);

    private final ClassifierModelRepository classifierModelRepository;
    private final UserRepository userRepository;
    private final ProjectAccessService projectAccessService;
    private final UserProjectConfigurationService userProjectConfigurationService;
    private final ClassifierLabPort classifierLabPort;
    /** Matches classifier-service default tag ({@code rag.classifier.model-id}) and {@code GET /models} first entry id. */
    private final String classifierSystemInferenceTag;

    public ClassifierModelRegistryService(
            ClassifierModelRepository classifierModelRepository,
            UserRepository userRepository,
            ProjectAccessService projectAccessService,
            UserProjectConfigurationService userProjectConfigurationService,
            ClassifierLabPort classifierLabPort,
            @Value("${rag.classifier.model-id:default}") String classifierSystemInferenceTag) {
        this.classifierModelRepository = classifierModelRepository;
        this.userRepository = userRepository;
        this.projectAccessService = projectAccessService;
        this.userProjectConfigurationService = userProjectConfigurationService;
        this.classifierLabPort = classifierLabPort;
        this.classifierSystemInferenceTag =
                classifierSystemInferenceTag == null || classifierSystemInferenceTag.isBlank()
                        ? "default"
                        : classifierSystemInferenceTag.trim();
    }

    /**
     * Called after a successful async train job. Idempotent per {@code taskId} stored in {@code hyperparams.sourceTaskId}.
     */
    @Transactional
    public void registerAfterSuccessfulTrain(
            UUID userId,
            UUID taskId,
            String requestedModelName,
            Map<String, Object> trainResult,
            int epochs,
            int batchSize) {
        String taskKey = taskId.toString();
        Optional<ClassifierModelEntity> existing =
                classifierModelRepository.findByOwnerIdAndSourceTaskId(userId, taskKey);
        if (existing.isPresent()) {
            log.debug("classifier_model already registered for task {}", taskId);
            return;
        }
        String inferenceTag = extractInferenceTag(trainResult);
        if (inferenceTag == null || inferenceTag.isBlank()) {
            log.warn("Train result missing modelId; skipping classifier_model row for task {}", taskId);
            return;
        }
        UserEntity owner = userRepository.findById(userId).orElse(null);
        if (owner == null) {
            log.warn("User {} not found; skipping classifier_model persist", userId);
            return;
        }
        String displayName = extractDisplayName(trainResult, requestedModelName);
        Map<String, Object> hp = new LinkedHashMap<>();
        hp.put(HP_SOURCE_TASK_ID, taskKey);
        hp.put(HP_OWNER_ID, userId.toString());
        hp.put(HP_EPOCHS, epochs);
        hp.put(HP_BATCH_SIZE, batchSize);
        Object metrics = trainResult.get("metrics");
        if (metrics instanceof Map<?, ?> m) {
            hp.put("trainingMetrics", m);
        }
        ClassifierModelEntity e = ClassifierModelEntityFactory.newReadyTrainingArtifact(
                        owner, displayName, inferenceTag, hp, Instant.now());
        classifierModelRepository.save(e);
        log.info(
                "Registered classifier_model id={} owner={} inferenceTag={} taskId={}",
                e.getId(),
                userId,
                inferenceTag,
                taskId);
    }

    /**
     * After eval job: update accuracy / f1 when the evaluated model tag matches a registered row.
     */
    @Transactional
    public void enrichAfterEval(UUID userId, String evaluatedModelTag, Map<String, Object> evalResult) {
        if (evaluatedModelTag == null || evaluatedModelTag.isBlank()) {
            evaluatedModelTag = classifierSystemInferenceTag;
        }
        Optional<ClassifierModelEntity> row =
                classifierModelRepository.findByOwner_IdAndArtifactPath(userId, evaluatedModelTag);
        if (row.isEmpty()) {
            return;
        }
        Double acc = extractAccuracy(evalResult);
        Double f1 = extractF1Macro(evalResult);
        ClassifierModelEntity e = row.get();
        if (acc != null) {
            e.setAccuracy(acc);
        }
        if (f1 != null) {
            e.setF1Macro(f1);
        }
        classifierModelRepository.save(e);
        log.info("Enriched classifier_model id={} with eval metrics accuracy={} f1={}", e.getId(), acc, f1);
    }

    @Transactional(readOnly = true)
    public List<ClassifierModelResponseDto> listForUser(UUID userId) {
        List<ClassifierModelEntity> list = classifierModelRepository.findByOwner_IdOrderByTrainedAtDesc(userId);
        List<ClassifierModelResponseDto> out = new ArrayList<>(list.size());
        for (ClassifierModelEntity e : list) {
            out.add(toDto(e));
        }
        return out;
    }

    /**
     * Ensures the caller sees a DB-backed row for the shared system classifier tag only (default model).
     * Disk-trained tags under the shared {@code MODELS_DIR} are never materialized as rows for arbitrary users —
     * those appear only via {@link #registerAfterSuccessfulTrain(UUID, UUID, String, Map, int, int)} for the training owner.
     */
    @Transactional
    public List<ClassifierModelResponseDto> listForUserWithSync(UUID userId) {
        if (classifierLabPort != null && classifierLabPort.isConfigured()) {
            try {
                List<Map<String, Object>> external = classifierLabPort.listModels();
                ensureSystemDefaultCatalogRow(userId, external);
            } catch (Exception e) {
                log.warn("Could not sync classifier models from classifier-service: {}", e.getMessage());
            }
        }
        return listForUser(userId);
    }

    @Transactional
    void ensureSystemDefaultCatalogRow(UUID userId, List<Map<String, Object>> externalModels) {
        if (externalModels == null || externalModels.isEmpty()) {
            return;
        }
        UserEntity owner = userRepository.findById(userId).orElse(null);
        if (owner == null) {
            return;
        }
        for (Map<String, Object> row : externalModels) {
            if (row == null) {
                continue;
            }
            String inferenceTag = row.get("id") != null ? row.get("id").toString().trim() : null;
            if (inferenceTag == null || inferenceTag.isBlank()) {
                continue;
            }
            if (!classifierSystemInferenceTag.equals(inferenceTag)) {
                continue;
            }
            Optional<ClassifierModelEntity> existing =
                    classifierModelRepository.findByOwner_IdAndArtifactPath(userId, inferenceTag);
            if (existing.isPresent()) {
                return;
            }
            String name = row.get("name") != null ? row.get("name").toString() : inferenceTag;
            Instant trainedAt = parseInstantOrNull(row.get("createdAt"));
            if (trainedAt == null) {
                trainedAt = Instant.now();
            }
            Map<String, Object> hp = new LinkedHashMap<>();
            hp.put(HP_SYSTEM_CATALOG, true);
            hp.put("external", true);
            hp.put("source", "classifier-service");
            if (row.get("metrics") instanceof Map<?, ?> mm) {
                hp.put("externalMetrics", mm);
            }
            ClassifierModelEntity e =
                    ClassifierModelEntityFactory.newReadyTrainingArtifact(owner, name, inferenceTag, hp, trainedAt);
            classifierModelRepository.save(e);
            return;
        }
    }

    /**
     * Marks the row as the active artifact for the user and merges {@code classifierModelId} into project RAG JSON
     * (inference tag string — same value used by classifier-service {@code /classify}).
     */
    @Transactional
    public ClassifierModelResponseDto activateForProject(UUID userId, UUID projectId, UUID modelRowId) {
        projectAccessService.requireOwnedProject(userId, projectId);
        ClassifierModelEntity model =
                classifierModelRepository
                        .findById(modelRowId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Classifier model not found"));
        if (model.getOwner() == null || !userId.equals(model.getOwner().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your classifier model");
        }
        if (!isActivatableClassifierRow(model)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Classifier model is not eligible for activation (only user-trained rows, "
                            + "or the shared system tag '"
                            + classifierSystemInferenceTag
                            + "', are allowed)");
        }
        if (model.getStatus() != ClassifierModelStatus.READY || model.getArtifactPath() == null || model.getArtifactPath().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Model is not ready for activation");
        }
        String inferenceTag = model.getArtifactPath();

        List<ClassifierModelEntity> owned = classifierModelRepository.findByOwner_IdOrderByTrainedAtDesc(userId);
        for (ClassifierModelEntity e : owned) {
            if (e.isActive() && !e.getId().equals(modelRowId)) {
                e.setActive(false);
                classifierModelRepository.save(e);
            }
        }
        model.setActive(true);
        classifierModelRepository.save(model);

        userProjectConfigurationService.mergeProjectConfig(
                userId, projectId, Map.of("classifierModelId", inferenceTag));
        log.info(
                "Activated classifier model row {} for project {} user {} inferenceTag {}",
                modelRowId,
                projectId,
                userId,
                inferenceTag);
        return toDto(model);
    }

    /**
     * Blocks legacy rows that were incorrectly materialized from shared disk for this user (external only, no task id)
     * except the configured system inference tag.
     */
    private boolean isActivatableClassifierRow(ClassifierModelEntity model) {
        String tag = model.getArtifactPath() != null ? model.getArtifactPath().trim() : "";
        Map<String, Object> hp = model.getHyperparams();
        if (hp != null && hp.get(HP_SOURCE_TASK_ID) != null) {
            return true;
        }
        if (hp != null && Boolean.TRUE.equals(hp.get(HP_SYSTEM_CATALOG))) {
            return true;
        }
        return classifierSystemInferenceTag.equals(tag);
    }

    private static ClassifierModelResponseDto toDto(ClassifierModelEntity e) {
        return new ClassifierModelResponseDto(
                e.getId(),
                e.getName(),
                e.getArtifactPath(),
                e.getStatus().name(),
                e.getTrainedAt(),
                e.getAccuracy(),
                e.getF1Macro(),
                e.isActive(),
                e.getHyperparams());
    }

    static String extractInferenceTag(Map<String, Object> trainResult) {
        if (trainResult == null) {
            return null;
        }
        Object o = trainResult.get("modelId");
        if (o == null) {
            o = trainResult.get("model_id");
        }
        return o != null ? o.toString() : null;
    }

    private static String extractDisplayName(Map<String, Object> trainResult, String fallback) {
        if (trainResult != null) {
            Object n = trainResult.get("name");
            if (n != null && !n.toString().isBlank()) {
                return n.toString();
            }
        }
        return fallback != null ? fallback : "model";
    }

    private static Double extractAccuracy(Map<String, Object> evalResult) {
        if (evalResult == null) {
            return null;
        }
        Object metrics = evalResult.get("metrics");
        if (metrics instanceof Map<?, ?> mm) {
            Object cr = mm.get("classificationReport");
            if (cr instanceof Map<?, ?> crm) {
                Object a = crm.get("accuracy");
                if (a instanceof Number n) {
                    return n.doubleValue();
                }
            }
            Object direct = mm.get("accuracy");
            if (direct instanceof Number n) {
                return n.doubleValue();
            }
        }
        return null;
    }

    private static Double extractF1Macro(Map<String, Object> evalResult) {
        if (evalResult == null) {
            return null;
        }
        Object metrics = evalResult.get("metrics");
        if (metrics instanceof Map<?, ?> mm) {
            Object cr = mm.get("classificationReport");
            if (cr instanceof Map<?, ?> crm) {
                Object ma = crm.get("macro avg");
                if (ma instanceof Map<?, ?> mam) {
                    Object f1 = mam.get("f1-score");
                    if (f1 instanceof Number n) {
                        return n.doubleValue();
                    }
                }
            }
        }
        return null;
    }

    private static Instant parseInstantOrNull(Object raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.toString().trim();
        if (s.isBlank() || "null".equalsIgnoreCase(s)) {
            return null;
        }
        try {
            return Instant.parse(s);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }
}
