package com.uniovi.rag.application.service.evaluation.async;

import com.uniovi.rag.application.service.evaluation.ExperimentalDatasetResolver;
import com.uniovi.rag.application.service.evaluation.TypedBenchmarkDataset;
import com.uniovi.rag.domain.AsyncTaskType;
import com.uniovi.rag.domain.evaluation.BenchmarkKind;
import com.uniovi.rag.domain.evaluation.workbook.LlmReaderQuestion;
import com.uniovi.rag.infrastructure.persistence.jpa.AsyncTaskEntity;
import com.uniovi.rag.application.service.async.AsyncTaskCancellationService;
import com.uniovi.rag.application.service.async.AsyncTaskMutationService;
import com.uniovi.rag.application.result.evaluation.LlmJudgeEvaluationBatchResult;
import com.uniovi.rag.application.service.evaluation.EvaluationCanonicalPersistenceService;
import com.uniovi.rag.application.service.evaluation.LabBenchmarkCompletionService;
import com.uniovi.rag.application.service.evaluation.LabCampaignBenchmarkExecutor;
import com.uniovi.rag.application.service.evaluation.LabJobProgressTracker;
import com.uniovi.rag.infrastructure.persistence.EvaluationRunRepository;
import com.uniovi.rag.application.service.evaluation.EvaluationTestFixtures;
import com.uniovi.rag.application.service.evaluation.baseline.ModelBaselineEvaluationOrchestrator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
class EvalLlmJobHandlerTest {

    @Mock
    private EvaluationCanonicalPersistenceService canonicalPersistence;

    @Mock
    private ExperimentalDatasetResolver experimentalDatasetResolver;

    @Mock
    private ModelBaselineEvaluationOrchestrator modelBaselineEvaluationOrchestrator;

    @Mock
    private AsyncTaskMutationService mutation;

    @Mock
    private AsyncTaskCancellationService cancellationService;

    @Mock
    private LabJobProgressTracker labJobProgressTracker;

    @Mock
    private EvaluationRunRepository evaluationRunRepository;

    @Mock
    private LabCampaignBenchmarkExecutor labCampaignBenchmarkExecutor;

    @Mock
    private LabBenchmarkCompletionService labBenchmarkCompletionService;

    private EvalLlmJobHandler handler() {
        return new EvalLlmJobHandler(
                canonicalPersistence,
                experimentalDatasetResolver,
                modelBaselineEvaluationOrchestrator,
                cancellationService,
                labJobProgressTracker,
                evaluationRunRepository,
                labCampaignBenchmarkExecutor,
                labBenchmarkCompletionService);
    }

    @Test
    void taskType_isEvalLlm() {
        assertThat(handler().taskType()).isEqualTo(AsyncTaskType.EVAL_LLM);
    }

    @Test
    void run_withRunId_usesBaselineOrchestrator() {
        UUID taskId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        LlmReaderQuestion q =
                new LlmReaderQuestion(
                        "bench-1",
                        "Q?",
                        "",
                        "A",
                        Optional.empty(),
                        Optional.empty(),
                        "",
                        "",
                        "",
                        false,
                        "");
        TypedBenchmarkDataset.LlmQuestions bundle =
                new TypedBenchmarkDataset.LlmQuestions(List.of(q), List.of());
        when(experimentalDatasetResolver.resolve(runId)).thenReturn(bundle);
        LlmJudgeEvaluationBatchResult eval = EvaluationTestFixtures.emptyLlmBatch();
        when(modelBaselineEvaluationOrchestrator.runLlmJudgeBaseline(
                        eq(runId), eq(bundle), ArgumentMatchers.any(), ArgumentMatchers.any()))
                .thenReturn(eval);
        AsyncTaskEntity task = task(taskId, Map.of(LabJobPayloadKeys.EVALUATION_RUN_ID, runId.toString()));

        handler().run(task, mutation);

        verify(canonicalPersistence).persistLlmJudgeBatch(runId, eval, BenchmarkKind.LLM_JUDGE_QA);
        verify(labBenchmarkCompletionService).completeRun(eq(mutation), eq(taskId), eq(runId), ArgumentMatchers.any());
    }

    @Test
    void run_withoutRunId_throws_and_doesNotTouchPersistence() {
        UUID taskId = UUID.randomUUID();
        AsyncTaskEntity task = task(taskId, Map.of());

        assertThatThrownBy(() -> handler().run(task, mutation)).isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(canonicalPersistence);
        verifyNoInteractions(experimentalDatasetResolver);
        verifyNoInteractions(modelBaselineEvaluationOrchestrator);
    }

    @Test
    void run_marksRunFailed_thenRethrows() {
        UUID taskId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        TypedBenchmarkDataset.LlmQuestions bundle = new TypedBenchmarkDataset.LlmQuestions(List.of(), List.of());
        when(experimentalDatasetResolver.resolve(runId)).thenReturn(bundle);
        when(modelBaselineEvaluationOrchestrator.runLlmJudgeBaseline(eq(runId), eq(bundle), ArgumentMatchers.any(), ArgumentMatchers.any()))
                .thenThrow(new RuntimeException("eval failed"));
        AsyncTaskEntity task = task(taskId, Map.of(LabJobPayloadKeys.EVALUATION_RUN_ID, runId.toString()));

        assertThatThrownBy(() -> handler().run(task, mutation))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("eval failed");

        verify(canonicalPersistence).markRunFailed(runId, "eval failed");
    }

    private static AsyncTaskEntity task(UUID id, Map<String, Object> payload) {
        AsyncTaskEntity t = Mockito.mock(AsyncTaskEntity.class);
        Mockito.lenient().when(t.getId()).thenReturn(id);
        Mockito.lenient().when(t.getRequestPayload()).thenReturn(payload);
        return t;
    }
}
