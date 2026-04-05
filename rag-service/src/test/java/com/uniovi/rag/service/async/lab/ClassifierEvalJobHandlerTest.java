package com.uniovi.rag.service.async.lab;

import com.uniovi.rag.application.port.ClassifierLabPort;
import com.uniovi.rag.domain.AsyncTaskType;
import com.uniovi.rag.infrastructure.persistence.jpa.AsyncTaskEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.UserEntity;
import com.uniovi.rag.service.async.AsyncTaskMutationService;
import com.uniovi.rag.service.classifier.ClassifierModelRegistryService;
import com.uniovi.rag.service.evaluation.EvaluationCanonicalPersistenceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClassifierEvalJobHandlerTest {

    @Mock
    private ClassifierLabPort classifierLab;

    @Mock
    private ClassifierModelRegistryService classifierModelRegistryService;

    @Mock
    private EvaluationCanonicalPersistenceService canonicalPersistence;

    @Mock
    private AsyncTaskMutationService mutation;

    @Test
    void taskType_isClassifierEval() {
        ClassifierEvalJobHandler h =
                new ClassifierEvalJobHandler(classifierLab, classifierModelRegistryService, canonicalPersistence);
        assertThat(h.taskType()).isEqualTo(AsyncTaskType.CLASSIFIER_EVAL);
    }

    @Test
    void run_marksFailed_whenPayloadNull() {
        UUID taskId = UUID.randomUUID();
        AsyncTaskEntity task = task(taskId, null);
        ClassifierEvalJobHandler h =
                new ClassifierEvalJobHandler(classifierLab, classifierModelRegistryService, canonicalPersistence);

        h.run(task, mutation);

        verify(mutation).markFailed(taskId, "Missing payload");
        verifyNoInteractions(classifierLab);
    }

    @Test
    void run_callsLabWithNullBytes_whenEvalFileMissing() {
        UUID taskId = UUID.randomUUID();
        UUID uid = UUID.randomUUID();
        Map<String, Object> payload =
                Map.of(
                        LabJobPayloadKeys.MODEL_ID,
                        "mid",
                        LabJobPayloadKeys.EVAL_PATH,
                        "/nonexistent/path/eval.xlsx",
                        LabJobPayloadKeys.INCLUDE_IMAGES,
                        Boolean.FALSE);
        when(classifierLab.evaluateBytes(eq("mid"), eq(false), isNull(), eq("eval.xlsx")))
                .thenReturn(Map.of("modelId", "mid", "accuracy", 0.9));
        AsyncTaskEntity task = task(taskId, payload, user(uid));

        new ClassifierEvalJobHandler(classifierLab, classifierModelRegistryService, canonicalPersistence)
                .run(task, mutation);

        verify(mutation).markSucceeded(taskId, Map.of("modelId", "mid", "accuracy", 0.9));
        verify(classifierModelRegistryService).enrichAfterEval(eq(uid), eq("mid"), any());
    }

    @Test
    void run_readsEvalFile_andUsesCustomFilename(@TempDir Path dir) throws Exception {
        UUID taskId = UUID.randomUUID();
        Path evalFile = dir.resolve("custom.tsv");
        Files.writeString(evalFile, "x");
        Map<String, Object> payload =
                Map.of(
                        LabJobPayloadKeys.EVAL_PATH,
                        evalFile.toString(),
                        LabJobPayloadKeys.EVAL_FILENAME,
                        "custom.tsv");
        when(classifierLab.evaluateBytes(isNull(), eq(true), any(), eq("custom.tsv")))
                .thenReturn(Map.of("modelId", "default"));
        AsyncTaskEntity task = task(taskId, payload, user(UUID.randomUUID()));

        new ClassifierEvalJobHandler(classifierLab, classifierModelRegistryService, canonicalPersistence)
                .run(task, mutation);

        verify(mutation).markSucceeded(eq(taskId), any());
    }

    @Test
    void run_marksFailed_whenEvalPathIsNotAReadableFile(@TempDir Path dir) throws Exception {
        UUID taskId = UUID.randomUUID();
        Path notAFile = dir.resolve("is_directory");
        Files.createDirectory(notAFile);
        Map<String, Object> payload = Map.of(LabJobPayloadKeys.EVAL_PATH, notAFile.toString());
        AsyncTaskEntity task = task(taskId, payload);

        new ClassifierEvalJobHandler(classifierLab, classifierModelRegistryService, canonicalPersistence)
                .run(task, mutation);

        verify(mutation).markFailed(eq(taskId), org.mockito.ArgumentMatchers.startsWith("Could not read eval file"));
        verifyNoInteractions(classifierLab);
    }

    @Test
    void run_marksRunFailed_whenPostSuccessPersistenceThrows(@TempDir Path dir) throws Exception {
        UUID taskId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        Path evalFile = dir.resolve("e.xlsx");
        Files.writeString(evalFile, "z");
        Map<String, Object> payload =
                Map.of(
                        LabJobPayloadKeys.EVAL_PATH,
                        evalFile.toString(),
                        LabJobPayloadKeys.EVALUATION_RUN_ID,
                        runId.toString());
        when(classifierLab.evaluateBytes(isNull(), eq(true), any(), eq("eval.xlsx")))
                .thenReturn(Map.of("ok", true));
        org.mockito.Mockito.doThrow(new RuntimeException("persist"))
                .when(canonicalPersistence)
                .persistClassifierMetrics(eq(runId), any());

        AsyncTaskEntity task = task(taskId, payload);
        ClassifierEvalJobHandler h =
                new ClassifierEvalJobHandler(classifierLab, classifierModelRegistryService, canonicalPersistence);

        assertThatThrownBy(() -> h.run(task, mutation)).isInstanceOf(RuntimeException.class).hasMessage("persist");

        verify(canonicalPersistence).markRunFailed(runId, "persist");
    }

    private static UserEntity user(UUID id) {
        UserEntity u = org.mockito.Mockito.mock(UserEntity.class);
        when(u.getId()).thenReturn(id);
        return u;
    }

    private static AsyncTaskEntity task(UUID id, Map<String, Object> payload) {
        AsyncTaskEntity t = org.mockito.Mockito.mock(AsyncTaskEntity.class);
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
