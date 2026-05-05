package com.uniovi.rag.service.evaluation.preset;

import com.uniovi.rag.application.service.evaluation.BenchmarkResultRowKeys;
import com.uniovi.rag.application.service.evaluation.TypedBenchmarkDataset;
import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.configuration.RagImplementationProperties;
import com.uniovi.rag.domain.evaluation.BenchmarkItemOutcome;
import com.uniovi.rag.domain.evaluation.snapshot.EmbeddingExperimentalSnapshot;
import com.uniovi.rag.domain.evaluation.snapshot.LlmExperimentalSnapshot;
import com.uniovi.rag.domain.evaluation.workbook.RagExperimentalPresetCode;
import com.uniovi.rag.domain.evaluation.workbook.RagPresetDefinition;
import com.uniovi.rag.domain.evaluation.workbook.RagPresetQuestion;
import com.uniovi.rag.infrastructure.persistence.EvaluationRunRepository;
import com.uniovi.rag.service.evaluation.EvaluationService;
import com.uniovi.rag.service.evaluation.baseline.ExperimentalSnapshotFactory;
import java.util.ArrayList;
import java.util.HashMap;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TypedRagPresetBenchmarkOrchestratorTest {

    @Mock private EvaluationService evaluationService;
    @Mock private EvaluationRunRepository evaluationRunRepository;
    @Mock private ExperimentalSnapshotFactory experimentalSnapshotFactory;

    private static LlmExperimentalSnapshot llmSnap() {
        return new LlmExperimentalSnapshot(
                "lm", 0.2, 0.9, 5, null, 1.05, 8192, 512, null, 42, List.of(), null, false, List.of());
    }

    private static EmbeddingExperimentalSnapshot embSnap() {
        return new EmbeddingExperimentalSnapshot("emb", null, null, null, null, null, null, List.of());
    }

    private TypedRagPresetBenchmarkOrchestrator orchestrator() {
        return new TypedRagPresetBenchmarkOrchestrator(
                evaluationService, evaluationRunRepository, experimentalSnapshotFactory);
    }

    @Test
    void empty_catalog_single_evaluate_call_no_preset_codes_on_arbitrary_rows() {
        UUID runId = UUID.randomUUID();
        when(evaluationRunRepository.findById(runId)).thenReturn(Optional.empty());
        when(experimentalSnapshotFactory.buildLlmSnapshot(null)).thenReturn(llmSnap());
        when(experimentalSnapshotFactory.buildEmbeddingSnapshot(null)).thenReturn(embSnap());

        RagPresetQuestion q = sampleQuestion();
        Map<String, Object> eval = new HashMap<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Object> row = new HashMap<>();
        row.put("question", q.question());
        rows.add(row);
        eval.put("results", rows);
        eval.put("evaluation_summary", Map.of());
        when(evaluationService.evaluateWithConfigurationForRagPresetQuestions(
                        ArgumentMatchers.any(),
                        ArgumentMatchers.any(),
                        ArgumentMatchers.eq(List.of(q)),
                        ArgumentMatchers.any()))
                .thenReturn(eval);

        Map<String, Object> out =
                orchestrator()
                        .runPresetBenchmark(
                                runId,
                                new TypedBenchmarkDataset.RagPresetQuestions(List.of(q), List.of()),
                                new RagFeatureConfiguration(),
                                new RagImplementationProperties(),
                                null,
                                null);

        assertThat(out.get("results")).isSameAs(rows);
        assertThat(row.get(BenchmarkResultRowKeys.PRESET_CODE)).isNull();
        assertThat(row.get(BenchmarkResultRowKeys.LLM_MODEL_ID)).isEqualTo("lm");
        assertThat(row.get(BenchmarkResultRowKeys.EMBEDDING_MODEL_ID)).isEqualTo("emb");
        verify(evaluationRunRepository).findById(runId);
    }

    @Test
    void catalog_with_p11_emits_not_supported_without_evaluation_service_for_blocked_preset() {
        when(experimentalSnapshotFactory.buildLlmSnapshot(null)).thenReturn(llmSnap());
        when(experimentalSnapshotFactory.buildEmbeddingSnapshot(null)).thenReturn(embSnap());

        RagPresetQuestion q = sampleQuestion();
        RagPresetDefinition p11 =
                new RagPresetDefinition(
                        RagExperimentalPresetCode.P11,
                        "",
                        "",
                        "",
                        "",
                        "",
                        "",
                        "",
                        "",
                        "",
                        "");

        Map<String, Object> out =
                orchestrator()
                        .runPresetBenchmark(
                                null,
                                new TypedBenchmarkDataset.RagPresetQuestions(List.of(q), List.of(p11)),
                                new RagFeatureConfiguration(),
                                new RagImplementationProperties(),
                                null,
                                null);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) out.get("results");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get(BenchmarkResultRowKeys.ITEM_OUTCOME))
                .isEqualTo(BenchmarkItemOutcome.NOT_SUPPORTED.name());
        assertThat(rows.get(0).get(BenchmarkResultRowKeys.ERROR_CODE))
                .isEqualTo("PRESET_CLARIFICATION_BENCHMARK_NOT_SUPPORTED");
        Mockito.verify(evaluationService, Mockito.never())
                .evaluateWithConfigurationForRagPresetQuestions(
                        ArgumentMatchers.any(),
                        ArgumentMatchers.any(),
                        ArgumentMatchers.any(),
                        ArgumentMatchers.any());
        Mockito.verify(evaluationService).summarizeJudgeResults(ArgumentMatchers.anyList());
    }

    private static RagPresetQuestion sampleQuestion() {
        return new RagPresetQuestion(
                "rq1",
                "Q?",
                "A",
                Optional.empty(),
                Optional.empty(),
                "",
                List.of(),
                List.of(),
                "",
                false,
                false,
                false,
                false,
                false,
                "");
    }
}
