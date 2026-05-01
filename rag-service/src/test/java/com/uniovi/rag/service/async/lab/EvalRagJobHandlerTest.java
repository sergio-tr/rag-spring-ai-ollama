package com.uniovi.rag.service.async.lab;

import com.uniovi.rag.domain.AsyncTaskType;
import com.uniovi.rag.domain.evaluation.BenchmarkKind;
import com.uniovi.rag.infrastructure.persistence.jpa.AsyncTaskEntity;
import com.uniovi.rag.service.async.AsyncTaskMutationService;
import com.uniovi.rag.service.evaluation.EvaluationCanonicalPersistenceService;
import com.uniovi.rag.service.evaluation.EvaluationService;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EvalRagJobHandlerTest {

    @Mock
    private EvaluationService evaluationService;

    @Mock
    private EvaluationCanonicalPersistenceService canonicalPersistence;

    @Mock
    private AsyncTaskMutationService mutation;

    @Test
    void taskType_isEvalRag() {
        EvalRagJobHandler h = new EvalRagJobHandler(evaluationService, canonicalPersistence);
        assertThat(h.taskType()).isEqualTo(AsyncTaskType.EVAL_RAG);
    }

    @Test
    void run_marksSucceeded_andPersists_whenRunIdPresent() {
        UUID taskId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        Map<String, Object> eval = Map.of("k", "v");
        when(evaluationService.evaluate()).thenReturn(eval);
        AsyncTaskEntity task = task(taskId, Map.of(LabJobPayloadKeys.EVALUATION_RUN_ID, runId.toString()));

        new EvalRagJobHandler(evaluationService, canonicalPersistence).run(task, mutation);

        verify(mutation).appendProgressLine(eq(taskId), ArgumentMatchers.contains("RAG"));
        verify(canonicalPersistence)
                .persistLlmJudgeFromEvaluationMap(runId, eval, BenchmarkKind.RAG_PRESET_END_TO_END);
        verify(mutation).markSucceeded(taskId, eval);
    }

    @Test
    void run_marksSucceeded_withoutPersistence_whenRunIdAbsent() {
        UUID taskId = UUID.randomUUID();
        Map<String, Object> eval = Map.of("a", 1);
        when(evaluationService.evaluate()).thenReturn(eval);
        AsyncTaskEntity task = task(taskId, Map.of());

        new EvalRagJobHandler(evaluationService, canonicalPersistence).run(task, mutation);

        verify(mutation).markSucceeded(taskId, eval);
        verifyNoInteractions(canonicalPersistence);
    }

    @Test
    void run_marksRunFailed_thenRethrows_whenEvaluationThrows() {
        UUID taskId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        when(evaluationService.evaluate()).thenThrow(new RuntimeException("boom"));
        AsyncTaskEntity task = task(taskId, Map.of(LabJobPayloadKeys.EVALUATION_RUN_ID, runId.toString()));

        assertThatThrownBy(() -> new EvalRagJobHandler(evaluationService, canonicalPersistence).run(task, mutation))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("boom");

        verify(canonicalPersistence).markRunFailed(runId, "boom");
    }

    @Test
    void run_doesNotMarkRunFailed_whenNoRunIdOnFailure() {
        UUID taskId = UUID.randomUUID();
        when(evaluationService.evaluate()).thenThrow(new RuntimeException("x"));
        AsyncTaskEntity task = task(taskId, Map.of());

        assertThatThrownBy(() -> new EvalRagJobHandler(evaluationService, canonicalPersistence).run(task, mutation))
                .isInstanceOf(RuntimeException.class);

        verifyNoInteractions(canonicalPersistence);
    }

    private static AsyncTaskEntity task(UUID id, Map<String, Object> payload) {
        AsyncTaskEntity t = Mockito.mock(AsyncTaskEntity.class);
        when(t.getId()).thenReturn(id);
        when(t.getRequestPayload()).thenReturn(payload);
        return t;
    }
}
