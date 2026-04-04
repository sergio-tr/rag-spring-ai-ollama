package com.uniovi.rag.service.async.lab;

import com.uniovi.rag.application.port.ClassifierLabPort;
import com.uniovi.rag.domain.AsyncTaskType;
import com.uniovi.rag.infrastructure.persistence.jpa.AsyncTaskEntity;
import com.uniovi.rag.service.async.AsyncTaskMutationService;
import com.uniovi.rag.service.classifier.ClassifierModelRegistryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

@Component
class ClassifierTrainJobHandler implements LabJobHandler {

    private static final Logger log = LoggerFactory.getLogger(ClassifierTrainJobHandler.class);

    private final ClassifierLabPort classifierLab;
    private final ClassifierModelRegistryService classifierModelRegistryService;

    ClassifierTrainJobHandler(ClassifierLabPort classifierLab, ClassifierModelRegistryService classifierModelRegistryService) {
        this.classifierLab = classifierLab;
        this.classifierModelRegistryService = classifierModelRegistryService;
    }

    @Override
    public AsyncTaskType taskType() {
        return AsyncTaskType.CLASSIFIER_TRAIN;
    }

    @Override
    public void run(AsyncTaskEntity task, AsyncTaskMutationService mutation) {
        UUID taskId = task.getId();
        Map<String, Object> payload = task.getRequestPayload();
        if (payload == null) {
            mutation.markFailed(taskId, "Missing payload");
            return;
        }
        Path trainPath = Path.of(String.valueOf(payload.get(LabJobPayloadKeys.TRAIN_PATH)));
        Path labelsPath = payload.get(LabJobPayloadKeys.LABELS_PATH) != null
                ? Path.of(String.valueOf(payload.get(LabJobPayloadKeys.LABELS_PATH)))
                : null;
        try {
            byte[] train;
            try {
                train = Files.readAllBytes(trainPath);
            } catch (IOException e) {
                mutation.markFailed(taskId, "Could not read training file: " + e.getMessage());
                return;
            }
            byte[] labelsBytes = null;
            if (labelsPath != null && Files.exists(labelsPath)) {
                try {
                    labelsBytes = Files.readAllBytes(labelsPath);
                } catch (IOException e) {
                    mutation.markFailed(taskId, "Could not read labels file: " + e.getMessage());
                    return;
                }
            }
            String modelName =
                    payload.get(LabJobPayloadKeys.MODEL_NAME) != null
                            ? String.valueOf(payload.get(LabJobPayloadKeys.MODEL_NAME))
                            : "model";
            String labelsJson =
                    payload.get(LabJobPayloadKeys.LABELS_JSON) != null
                            ? String.valueOf(payload.get(LabJobPayloadKeys.LABELS_JSON))
                            : null;
            if ("null".equals(labelsJson)) {
                labelsJson = null;
            }
            int epochs = payload.get(LabJobPayloadKeys.EPOCHS) instanceof Number n ? n.intValue() : 50;
            int batchSize = payload.get(LabJobPayloadKeys.BATCH_SIZE) instanceof Number n ? n.intValue() : 8;
            mutation.appendProgressLine(taskId, "Calling classifier-service /train…");
            Map<String, Object> res =
                    classifierLab.trainBytes(
                            train,
                            trainPath.getFileName().toString(),
                            modelName,
                            labelsJson,
                            labelsBytes,
                            labelsBytes != null ? labelsPath.getFileName().toString() : null,
                            epochs,
                            batchSize);
            mutation.markSucceeded(taskId, res);
            try {
                classifierModelRegistryService.registerAfterSuccessfulTrain(
                        task.getUser().getId(), taskId, modelName, res, epochs, batchSize);
            } catch (Exception ex) {
                log.warn("Could not persist classifier_model row: {}", ex.getMessage());
            }
        } finally {
            try {
                Files.deleteIfExists(trainPath);
            } catch (IOException ignored) {
                log.debug("Could not delete temp train file {}", trainPath);
            }
            if (labelsPath != null) {
                try {
                    Files.deleteIfExists(labelsPath);
                } catch (IOException ignored) {
                    log.debug("Could not delete temp labels file {}", labelsPath);
                }
            }
        }
    }
}
