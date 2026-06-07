package com.uniovi.rag.application.service.evaluation.async;

import com.uniovi.rag.application.port.ClassifierLabPort;
import com.uniovi.rag.domain.AsyncTaskType;
import com.uniovi.rag.infrastructure.persistence.jpa.AsyncTaskEntity;
import com.uniovi.rag.application.service.async.AsyncTaskMutationService;
import com.uniovi.rag.application.service.classifier.ClassifierModelRegistryService;
import com.uniovi.rag.application.service.evaluation.EvaluationCanonicalPersistenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

@Component
class ClassifierEvalJobHandler implements LabJobHandler {

    private static final Logger log = LoggerFactory.getLogger(ClassifierEvalJobHandler.class);

    private final ClassifierLabPort classifierLab;
    private final ClassifierModelRegistryService classifierModelRegistryService;
    private final EvaluationCanonicalPersistenceService canonicalPersistence;

    ClassifierEvalJobHandler(
            ClassifierLabPort classifierLab,
            ClassifierModelRegistryService classifierModelRegistryService,
            EvaluationCanonicalPersistenceService canonicalPersistence) {
        this.classifierLab = classifierLab;
        this.classifierModelRegistryService = classifierModelRegistryService;
        this.canonicalPersistence = canonicalPersistence;
    }

    @Override
    public AsyncTaskType taskType() {
        return AsyncTaskType.CLASSIFIER_EVAL;
    }

    @Override
    public void run(AsyncTaskEntity task, AsyncTaskMutationService mutation) {
        UUID taskId = task.getId();
        UUID evaluationRunId = LabJobPayloads.evaluationRunId(task.getRequestPayload());
        Map<String, Object> payload = task.getRequestPayload();
        if (payload == null) {
            mutation.markFailed(taskId, "Missing payload");
            return;
        }
        String modelId =
                payload.get(LabJobPayloadKeys.MODEL_ID) != null
                        ? String.valueOf(payload.get(LabJobPayloadKeys.MODEL_ID))
                        : null;
        boolean includeImages = true;
        if (payload.get(LabJobPayloadKeys.INCLUDE_IMAGES) instanceof Boolean b) {
            includeImages = b;
        }
        Path evalPath =
                payload.get(LabJobPayloadKeys.EVAL_PATH) != null
                        ? Path.of(String.valueOf(payload.get(LabJobPayloadKeys.EVAL_PATH)))
                        : null;
        String evalFilename =
                payload.get(LabJobPayloadKeys.EVAL_FILENAME) != null
                        ? String.valueOf(payload.get(LabJobPayloadKeys.EVAL_FILENAME))
                        : "eval.xlsx";
        try {
            byte[] evalBytes = null;
            if (evalPath != null && Files.exists(evalPath)) {
                try {
                    evalBytes = Files.readAllBytes(evalPath);
                } catch (IOException e) {
                    mutation.markFailed(taskId, "Could not read eval file: " + e.getMessage());
                    return;
                }
            }
            mutation.appendProgressLine(taskId, "Calling classifier-service /evaluate…");
            Map<String, Object> res =
                    classifierLab.evaluateBytes(modelId, includeImages, evalBytes, evalFilename);
            try {
                if (evaluationRunId != null) {
                    canonicalPersistence.persistClassifierMetrics(evaluationRunId, res);
                }
                mutation.markSucceeded(taskId, res);
            } catch (RuntimeException e) {
                if (evaluationRunId != null) {
                    canonicalPersistence.markRunFailed(evaluationRunId, e.getMessage());
                }
                throw e;
            }
            try {
                String tag =
                        modelId != null && !modelId.isBlank()
                                ? modelId
                                : String.valueOf(res.getOrDefault("modelId", "default"));
                classifierModelRegistryService.enrichAfterEval(task.getUser().getId(), tag, res);
            } catch (Exception ex) {
                log.warn("Could not enrich classifier_model with eval metrics: {}", ex.getMessage());
            }
        } finally {
            if (evalPath != null) {
                try {
                    Files.deleteIfExists(evalPath);
                } catch (IOException ignored) {
                    log.debug("Could not delete temp eval file {}", evalPath);
                }
            }
        }
    }
}
