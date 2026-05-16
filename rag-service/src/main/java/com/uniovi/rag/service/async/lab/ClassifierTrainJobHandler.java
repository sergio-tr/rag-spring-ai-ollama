package com.uniovi.rag.service.async.lab;

import com.uniovi.rag.application.port.ClassifierLabPort;
import com.uniovi.rag.application.port.ClassifierTrainBytesCommand;
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
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
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
        Path labelsPath =
                payload.get(LabJobPayloadKeys.LABELS_PATH) != null
                        ? Path.of(String.valueOf(payload.get(LabJobPayloadKeys.LABELS_PATH)))
                        : null;
        try {
            Optional<byte[]> trainOpt = readTrainBytes(taskId, trainPath, mutation);
            if (trainOpt.isEmpty()) {
                return;
            }
            byte[] train = trainOpt.get();
            LabelsRead labelsRead = readLabelsBytesWhenPresent(taskId, labelsPath, mutation);
            if (!labelsRead.ok()) {
                return;
            }
            byte[] labelsBytes = labelsRead.bytes();
            TrainPayload parsed = TrainPayload.fromMap(payload);
            mutation.appendProgressLine(taskId, "Calling classifier-service /train…");
            Map<String, Object> res =
                    classifierLab.trainBytes(
                            new ClassifierTrainBytesCommand(
                                    train,
                                    trainPath.getFileName().toString(),
                                    parsed.modelName(),
                                    parsed.labelsJson(),
                                    labelsBytes,
                                    labelsBytes != null ? labelsPath.getFileName().toString() : null,
                                    parsed.epochs(),
                                    parsed.batchSize(),
                                    task.getUser().getId().toString()));
            mutation.markSucceeded(taskId, res);
            registerModelAfterTrain(task, taskId, parsed.modelName(), res, parsed.epochs(), parsed.batchSize());
        } finally {
            deleteQuietly(trainPath, "train");
            if (labelsPath != null) {
                deleteQuietly(labelsPath, "labels");
            }
        }
    }

    private Optional<byte[]> readTrainBytes(UUID taskId, Path trainPath, AsyncTaskMutationService mutation) {
        try {
            return Optional.of(Files.readAllBytes(trainPath));
        } catch (IOException e) {
            mutation.markFailed(taskId, "Could not read training file: " + e.getMessage());
            return Optional.empty();
        }
    }

    private LabelsRead readLabelsBytesWhenPresent(UUID taskId, Path labelsPath, AsyncTaskMutationService mutation) {
        if (labelsPath == null || !Files.exists(labelsPath)) {
            return LabelsRead.ok(null);
        }
        try {
            return LabelsRead.ok(Files.readAllBytes(labelsPath));
        } catch (IOException e) {
            mutation.markFailed(taskId, "Could not read labels file: " + e.getMessage());
            return LabelsRead.failed();
        }
    }

    private record LabelsRead(boolean ok, byte[] bytes) {
        static LabelsRead ok(byte[] b) {
            return new LabelsRead(true, b);
        }

        static LabelsRead failed() {
            return new LabelsRead(false, null);
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof LabelsRead other && ok == other.ok && Arrays.equals(bytes, other.bytes);
        }

        @Override
        public int hashCode() {
            return 31 * Boolean.hashCode(ok) + Arrays.hashCode(bytes);
        }

        @Override
        public String toString() {
            return "LabelsRead[ok="
                    + ok
                    + ", bytesLength="
                    + (bytes == null ? -1 : bytes.length)
                    + "]";
        }
    }

    private void registerModelAfterTrain(
            AsyncTaskEntity task,
            UUID taskId,
            String modelName,
            Map<String, Object> res,
            int epochs,
            int batchSize) {
        try {
            classifierModelRegistryService.registerAfterSuccessfulTrain(
                    task.getUser().getId(), taskId, modelName, res, epochs, batchSize);
        } catch (Exception ex) {
            log.warn("Could not persist classifier_model row: {}", ex.getMessage());
        }
    }

    private static void deleteQuietly(Path path, String kind) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            log.debug("Could not delete temp {} file {}", kind, path);
        }
    }

    private record TrainPayload(String modelName, String labelsJson, int epochs, int batchSize) {
        static TrainPayload fromMap(Map<String, Object> payload) {
            String modelName =
                    payload.get(LabJobPayloadKeys.MODEL_NAME) != null
                            ? String.valueOf(payload.get(LabJobPayloadKeys.MODEL_NAME))
                            : "model";
            String labelsJson =
                    payload.get(LabJobPayloadKeys.LABELS_JSON) != null
                            ? String.valueOf(payload.get(LabJobPayloadKeys.LABELS_JSON))
                            : null;
            String lj = "null".equals(labelsJson) ? null : labelsJson;
            int epochs = payload.get(LabJobPayloadKeys.EPOCHS) instanceof Number n ? n.intValue() : 50;
            int batchSize = payload.get(LabJobPayloadKeys.BATCH_SIZE) instanceof Number n ? n.intValue() : 8;
            return new TrainPayload(modelName, lj, epochs, batchSize);
        }
    }
}
