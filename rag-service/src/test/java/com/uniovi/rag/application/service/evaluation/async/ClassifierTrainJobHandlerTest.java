package com.uniovi.rag.application.service.evaluation.async;

import com.uniovi.rag.application.port.ClassifierLabPort;
import com.uniovi.rag.application.port.ClassifierTrainBytesCommand;
import com.uniovi.rag.domain.AsyncTaskType;
import com.uniovi.rag.infrastructure.persistence.jpa.AsyncTaskEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.UserEntity;
import com.uniovi.rag.application.service.async.AsyncTaskMutationService;
import com.uniovi.rag.application.service.classifier.ClassifierModelRegistryService;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClassifierTrainJobHandlerTest {

    @Mock
    private ClassifierLabPort classifierLab;

    @Mock
    private ClassifierModelRegistryService classifierModelRegistryService;

    @Mock
    private AsyncTaskMutationService mutation;

    @Test
    void taskType_isClassifierTrain() {
        ClassifierTrainJobHandler h = new ClassifierTrainJobHandler(classifierLab, classifierModelRegistryService);
        assertThat(h.taskType()).isEqualTo(AsyncTaskType.CLASSIFIER_TRAIN);
    }

    @Test
    void run_marksFailed_whenPayloadNull() {
        UUID taskId = UUID.randomUUID();
        AsyncTaskEntity task = task(taskId, null);

        new ClassifierTrainJobHandler(classifierLab, classifierModelRegistryService).run(task, mutation);

        verify(mutation).markFailed(taskId, "Missing payload");
        verifyNoInteractions(classifierLab);
    }

    @Test
    void run_marksFailed_whenTrainFileMissing() {
        UUID taskId = UUID.randomUUID();
        Map<String, Object> payload = Map.of(LabJobPayloadKeys.TRAIN_PATH, "/no/such/train.zip");
        AsyncTaskEntity task = task(taskId, payload);

        new ClassifierTrainJobHandler(classifierLab, classifierModelRegistryService).run(task, mutation);

        verify(mutation).markFailed(eq(taskId), ArgumentMatchers.startsWith("Could not read training file"));
    }

    @Test
    void run_usesDefaultEpochsAndBatchSize_whenNotNumbers(@TempDir Path dir) throws Exception {
        UUID taskId = UUID.randomUUID();
        UUID uid = UUID.randomUUID();
        Path train = dir.resolve("data.zip");
        Files.writeString(train, "bin");
        Map<String, Object> payload =
                Map.of(
                        LabJobPayloadKeys.TRAIN_PATH,
                        train.toString(),
                        LabJobPayloadKeys.MODEL_NAME,
                        "m1",
                        LabJobPayloadKeys.LABELS_JSON,
                        "null");
        Map<String, Object> labOut = Map.of("status", "ok");
        when(classifierLab.trainBytes(
                        argThat(
                                (ClassifierTrainBytesCommand c) ->
                                        "data.zip".equals(c.datasetFilename())
                                                && "m1".equals(c.modelName())
                                                && c.labelsJson() == null
                                                && c.labelsFileContent() == null
                                                && c.labelsFilename() == null
                                                && c.epochs() == 50
                                                && c.batchSize() == 8
                                                && uid.toString().equals(c.trainArtifactOwnerId()))))
                .thenReturn(labOut);
        AsyncTaskEntity task = task(taskId, payload, user(uid));

        new ClassifierTrainJobHandler(classifierLab, classifierModelRegistryService).run(task, mutation);

        verify(mutation).markSucceeded(taskId, labOut);
        verify(classifierModelRegistryService)
                .registerAfterSuccessfulTrain(eq(uid), eq(taskId), eq("m1"), eq(labOut), eq(50), eq(8));
    }

    @Test
    void run_readsOptionalLabelsFile(@TempDir Path dir) throws Exception {
        UUID taskId = UUID.randomUUID();
        Path train = dir.resolve("t.zip");
        Path labels = dir.resolve("l.json");
        Files.writeString(train, "a");
        Files.writeString(labels, "{}");
        Map<String, Object> payload =
                Map.of(
                        LabJobPayloadKeys.TRAIN_PATH,
                        train.toString(),
                        LabJobPayloadKeys.LABELS_PATH,
                        labels.toString(),
                        LabJobPayloadKeys.EPOCHS,
                        2,
                        LabJobPayloadKeys.BATCH_SIZE,
                        4);
        UUID uid = UUID.randomUUID();
        when(classifierLab.trainBytes(
                        argThat(
                                (ClassifierTrainBytesCommand c) ->
                                        "t.zip".equals(c.datasetFilename())
                                                && "model".equals(c.modelName())
                                                && c.labelsJson() == null
                                                && "{}".equals(new String(c.labelsFileContent(), StandardCharsets.UTF_8))
                                                && "l.json".equals(c.labelsFilename())
                                                && c.epochs() == 2
                                                && c.batchSize() == 4
                                                && uid.toString().equals(c.trainArtifactOwnerId()))))
                .thenReturn(Map.of());
        AsyncTaskEntity task = task(taskId, payload, user(uid));

        new ClassifierTrainJobHandler(classifierLab, classifierModelRegistryService).run(task, mutation);

        verify(classifierLab)
                .trainBytes(
                        argThat(
                                (ClassifierTrainBytesCommand c) ->
                                        "t.zip".equals(c.datasetFilename())
                                                && "model".equals(c.modelName())
                                                && c.labelsJson() == null
                                                && "{}".equals(new String(c.labelsFileContent(), StandardCharsets.UTF_8))
                                                && "l.json".equals(c.labelsFilename())
                                                && c.epochs() == 2
                                                && c.batchSize() == 4
                                                && uid.toString().equals(c.trainArtifactOwnerId())));
    }

    @Test
    void run_marksFailed_whenLabelsPresentButUnreadable(@TempDir Path dir) throws Exception {
        UUID taskId = UUID.randomUUID();
        Path train = dir.resolve("t.zip");
        Path labels = dir.resolve("labels_dir");
        Files.writeString(train, "a");
        Files.createDirectory(labels);
        Map<String, Object> payload =
                Map.of(LabJobPayloadKeys.TRAIN_PATH, train.toString(), LabJobPayloadKeys.LABELS_PATH, labels.toString());
        AsyncTaskEntity task = task(taskId, payload);

        new ClassifierTrainJobHandler(classifierLab, classifierModelRegistryService).run(task, mutation);

        verify(mutation).markFailed(eq(taskId), ArgumentMatchers.startsWith("Could not read labels file"));
    }

    private static UserEntity user(UUID id) {
        UserEntity u = Mockito.mock(UserEntity.class);
        when(u.getId()).thenReturn(id);
        return u;
    }

    private static AsyncTaskEntity task(UUID id, Map<String, Object> payload) {
        AsyncTaskEntity t = Mockito.mock(AsyncTaskEntity.class);
        when(t.getId()).thenReturn(id);
        when(t.getRequestPayload()).thenReturn(payload);
        return t;
    }

    private static AsyncTaskEntity task(UUID id, Map<String, Object> payload, UserEntity user) {
        AsyncTaskEntity t = task(id, payload);
        when(t.getUser()).thenReturn(user);
        return t;
    }
}
