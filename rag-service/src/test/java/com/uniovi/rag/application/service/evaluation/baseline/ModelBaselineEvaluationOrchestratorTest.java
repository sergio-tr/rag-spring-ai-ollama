package com.uniovi.rag.application.service.evaluation.baseline;

import com.uniovi.rag.application.service.evaluation.BenchmarkResultRowKeys;
import com.uniovi.rag.application.service.evaluation.TypedBenchmarkDataset;
import com.uniovi.rag.domain.evaluation.BenchmarkItemOutcome;
import com.uniovi.rag.domain.evaluation.snapshot.EmbeddingExperimentalSnapshot;
import com.uniovi.rag.domain.evaluation.snapshot.LlmExperimentalSnapshot;
import com.uniovi.rag.domain.evaluation.workbook.LlmReaderQuestion;
import com.uniovi.rag.infrastructure.persistence.EvaluationRunRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationRunEntity;
import com.uniovi.rag.application.result.evaluation.LlmJudgeEvaluationBatchResult;
import com.uniovi.rag.application.result.evaluation.LlmJudgeItemResult;
import com.uniovi.rag.application.service.evaluation.EvaluationService;
import com.uniovi.rag.application.service.evaluation.EvaluationSummaryBuilder;
import com.uniovi.rag.application.service.evaluation.EvaluationTestFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModelBaselineEvaluationOrchestratorTest {

    @Mock
    private EvaluationRunRepository evaluationRunRepository;

    @Mock
    private ExperimentalSnapshotFactory experimentalSnapshotFactory;

    @Mock
    private BaselineRunSnapshotWriter baselineRunSnapshotWriter;

    @Mock
    private ModelBaselineLlmRunner modelBaselineLlmRunner;

    @Mock
    private OllamaModelCatalogClient ollamaModelCatalogClient;

    @Mock
    private EvaluationService evaluationService;

    @Test
    void run_modelsUnavailable_skipsChat_andMarksOutcome() {
        UUID runId = UUID.randomUUID();
        EvaluationRunEntity run = new EvaluationRunEntity();
        run.setId(runId);
        when(evaluationRunRepository.findById(runId)).thenReturn(Optional.of(run));
        LlmExperimentalSnapshot llm =
                new LlmExperimentalSnapshot(
                        "missing:m",
                        0.2,
                        0.9,
                        5,
                        null,
                        1.05,
                        8192,
                        512,
                        null,
                        42,
                        List.of(),
                        null,
                        false,
                        List.of());
        when(experimentalSnapshotFactory.buildLlmSnapshot(run)).thenReturn(llm);
        when(experimentalSnapshotFactory.buildEmbeddingSnapshot(run))
                .thenReturn(new EmbeddingExperimentalSnapshot("e", null, null, null, null, null, "MODEL_DEFAULT", List.of()));
        when(ollamaModelCatalogClient.isModelAvailable("missing:m")).thenReturn(false);
        when(evaluationService.summarizeJudgeResults(any())).thenReturn(EvaluationTestFixtures.emptySummary());

        ModelBaselineEvaluationOrchestrator orch =
                new ModelBaselineEvaluationOrchestrator(
                        evaluationRunRepository,
                        experimentalSnapshotFactory,
                        baselineRunSnapshotWriter,
                        modelBaselineLlmRunner,
                        ollamaModelCatalogClient,
                        evaluationService);

        LlmReaderQuestion q =
                new LlmReaderQuestion(
                        "r1",
                        "Q",
                        "ctx",
                        "gold",
                        Optional.empty(),
                        Optional.empty(),
                        "LLM_READER_ORACLE_CONTEXT",
                        "",
                        "",
                        false,
                        "");

        LlmJudgeEvaluationBatchResult payload =
                orch.runLlmJudgeBaseline(runId, new TypedBenchmarkDataset.LlmQuestions(List.of(q), List.of()), null);

        verify(modelBaselineLlmRunner, never()).generateAnswer(any(), any(), any(), any(), any(), any(), any());
        List<LlmJudgeItemResult> rows = payload.results();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).itemOutcome()).isEqualTo(BenchmarkItemOutcome.MODEL_NOT_AVAILABLE);
        assertThat(rows.get(0).errorCode()).isEqualTo("MODEL_UNAVAILABLE");
        assertThat(rows.get(0).errorMessage()).isEqualTo("MODEL_UNAVAILABLE");
    }

    @Test
    void run_whenAvailable_invokesRunner_andJudge() {
        UUID runId = UUID.randomUUID();
        EvaluationRunEntity run = new EvaluationRunEntity();
        run.setId(runId);
        when(evaluationRunRepository.findById(runId)).thenReturn(Optional.of(run));
        LlmExperimentalSnapshot llm =
                new LlmExperimentalSnapshot(
                        "gemma:4b",
                        0.2,
                        0.9,
                        5,
                        null,
                        1.05,
                        8192,
                        512,
                        null,
                        42,
                        List.of(),
                        null,
                        false,
                        List.of());
        when(experimentalSnapshotFactory.buildLlmSnapshot(run)).thenReturn(llm);
        when(experimentalSnapshotFactory.buildEmbeddingSnapshot(run))
                .thenReturn(new EmbeddingExperimentalSnapshot("e", null, null, null, null, null, "MODEL_DEFAULT", List.of()));
        when(ollamaModelCatalogClient.isModelAvailable("gemma:4b")).thenReturn(true);
        when(modelBaselineLlmRunner.generateAnswer(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn("model says");
        when(evaluationService.judgeQaAnswer(eq("Q"), eq("gold"), eq("model says")))
                .thenReturn("Correctness: 4");
        when(evaluationService.summarizeJudgeResults(any())).thenReturn(EvaluationSummaryBuilder.summarize(List.of()));

        ModelBaselineEvaluationOrchestrator orch =
                new ModelBaselineEvaluationOrchestrator(
                        evaluationRunRepository,
                        experimentalSnapshotFactory,
                        baselineRunSnapshotWriter,
                        modelBaselineLlmRunner,
                        ollamaModelCatalogClient,
                        evaluationService);

        LlmReaderQuestion q =
                new LlmReaderQuestion(
                        "r1",
                        "Q",
                        "ctx",
                        "gold",
                        Optional.empty(),
                        Optional.empty(),
                        "LLM_READER_ORACLE_CONTEXT",
                        "",
                        "",
                        false,
                        "");

        orch.runLlmJudgeBaseline(runId, new TypedBenchmarkDataset.LlmQuestions(List.of(q), List.of()), null);

        @SuppressWarnings("rawtypes")
        ArgumentCaptor<List> cap = ArgumentCaptor.forClass(List.class);
        verify(evaluationService).summarizeJudgeResults(cap.capture());
        assertThat(cap.getValue()).hasSize(1);
        assertThat(((LlmJudgeItemResult) cap.getValue().get(0)).generatedAnswer()).isEqualTo("model says");
    }
}
