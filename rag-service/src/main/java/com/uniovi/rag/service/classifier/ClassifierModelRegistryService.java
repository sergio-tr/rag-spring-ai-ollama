package com.uniovi.rag.service.classifier;

import com.uniovi.rag.domain.ClassifierModelStatus;
import com.uniovi.rag.infrastructure.persistence.ClassifierModelRepository;
import com.uniovi.rag.infrastructure.persistence.UserRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.ClassifierModelEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.ClassifierModelEntityFactory;
import com.uniovi.rag.infrastructure.persistence.jpa.UserEntity;
import com.uniovi.rag.interfaces.rest.dto.ClassifierModelResponseDto;
import com.uniovi.rag.service.config.UserProjectConfigurationService;
import com.uniovi.rag.service.project.ProjectAccessService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
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
    public static final String HP_EPOCHS = "epochs";
    public static final String HP_BATCH_SIZE = "batchSize";

    private static final Logger log = LoggerFactory.getLogger(ClassifierModelRegistryService.class);

    private final ClassifierModelRepository classifierModelRepository;
    private final UserRepository userRepository;
    private final ProjectAccessService projectAccessService;
    private final UserProjectConfigurationService userProjectConfigurationService;

    public ClassifierModelRegistryService(
            ClassifierModelRepository classifierModelRepository,
            UserRepository userRepository,
            ProjectAccessService projectAccessService,
            UserProjectConfigurationService userProjectConfigurationService) {
        this.classifierModelRepository = classifierModelRepository;
        this.userRepository = userRepository;
        this.projectAccessService = projectAccessService;
        this.userProjectConfigurationService = userProjectConfigurationService;
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
            evaluatedModelTag = "default";
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
}
