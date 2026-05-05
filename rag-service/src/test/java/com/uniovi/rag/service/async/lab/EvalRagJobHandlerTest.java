package com.uniovi.rag.service.async.lab;

import com.uniovi.rag.application.service.evaluation.ExperimentalDatasetResolver;
import com.uniovi.rag.application.service.evaluation.TypedBenchmarkDataset;
import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.configuration.RagImplementationProperties;
import com.uniovi.rag.domain.AsyncTaskType;
import com.uniovi.rag.domain.evaluation.BenchmarkKind;
import com.uniovi.rag.domain.evaluation.workbook.RagPresetQuestion;
import com.uniovi.rag.infrastructure.persistence.EvaluationRunRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.AsyncTaskEntity;
import com.uniovi.rag.service.async.AsyncTaskMutationService;
import com.uniovi.rag.service.evaluation.EvaluationCanonicalPersistenceService;
import com.uniovi.rag.service.evaluation.preset.TypedRagPresetBenchmarkOrchestrator;
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
class EvalRagJobHandlerTest {

    @Mock
    private RagFeatureConfiguration featureConfiguration;

    @Mock
    private RagImplementationProperties implementationProperties;

    @Mock
    private EvaluationCanonicalPersistenceService canonicalPersistence;

    @Mock
    private ExperimentalDatasetResolver experimentalDatasetResolver;
    @Mock
    private EvaluationRunRepository evaluationRunRepository;

    @Mock
    private TypedRagPresetBenchmarkOrchestrator typedRagPresetBenchmarkOrchestrator;

    @Mock
    private AsyncTaskMutationService mutation;

    private EvalRagJobHandler handler() {
        return new EvalRagJobHandler(
                featureConfiguration,
                implementationProperties,
                canonicalPersistence,
                evaluationRunRepository,
                experimentalDatasetResolver,
                typedRagPresetBenchmarkOrchestrator);
    }

    @Test
    void taskType_isEvalRag() {
        assertThat(handler().taskType()).isEqualTo(AsyncTaskType.EVAL_RAG);
    }

    @Test
    void run_withRunId_usesTypedRag_neverLegacyEvaluate() {
        UUID taskId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        RagPresetQuestion q =
                new RagPresetQuestion(
                        "rp1",
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
        when(experimentalDatasetResolver.resolve(runId))
                .thenReturn(new TypedBenchmarkDataset.RagPresetQuestions(List.of(q), List.of()));
        Map<String, Object> eval = Map.of("k", "v");
        when(typedRagPresetBenchmarkOrchestrator.runPresetBenchmark(
                        eq(runId),
                        ArgumentMatchers.any(TypedBenchmarkDataset.RagPresetQuestions.class),
                        eq(featureConfiguration),
                        eq(implementationProperties),
                        ArgumentMatchers.anySet(),
                        ArgumentMatchers.any()))
                .thenReturn(eval);
        AsyncTaskEntity task = task(taskId, Map.of(LabJobPayloadKeys.EVALUATION_RUN_ID, runId.toString()));

        handler().run(task, mutation);

        Mockito.verify(typedRagPresetBenchmarkOrchestrator, Mockito.times(1))
                .runPresetBenchmark(
                        eq(runId),
                        ArgumentMatchers.any(TypedBenchmarkDataset.RagPresetQuestions.class),
                        eq(featureConfiguration),
                        eq(implementationProperties),
                        ArgumentMatchers.anySet(),
                        ArgumentMatchers.any());
        verify(canonicalPersistence).persistLlmJudgeFromEvaluationMap(runId, eval, BenchmarkKind.RAG_PRESET_END_TO_END);
        verify(mutation).markSucceeded(taskId, eval);
    }

    @Test
    void run_withoutRunId_throws() {
        UUID taskId = UUID.randomUUID();
        AsyncTaskEntity task = task(taskId, Map.of());

        assertThatThrownBy(() -> handler().run(task, mutation)).isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(canonicalPersistence);
        verifyNoInteractions(experimentalDatasetResolver);
    }

    @Test
    void run_marksRunFailed_thenRethrows_whenEvaluationThrows() {
        UUID taskId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        RagPresetQuestion q =
                new RagPresetQuestion(
                        "rp1",
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
        when(experimentalDatasetResolver.resolve(runId))
                .thenReturn(new TypedBenchmarkDataset.RagPresetQuestions(List.of(q), List.of()));
        when(typedRagPresetBenchmarkOrchestrator.runPresetBenchmark(
                        eq(runId),
                        ArgumentMatchers.any(),
                        eq(featureConfiguration),
                        eq(implementationProperties),
                        ArgumentMatchers.anySet(),
                        ArgumentMatchers.any()))
                .thenThrow(new RuntimeException("boom"));
        AsyncTaskEntity task = task(taskId, Map.of(LabJobPayloadKeys.EVALUATION_RUN_ID, runId.toString()));

        assertThatThrownBy(() -> handler().run(task, mutation))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("boom");

        verify(canonicalPersistence).markRunFailed(runId, "boom");
    }

    private static AsyncTaskEntity task(UUID id, Map<String, Object> payload) {
        AsyncTaskEntity t = Mockito.mock(AsyncTaskEntity.class);
        when(t.getId()).thenReturn(id);
        when(t.getRequestPayload()).thenReturn(payload);
        return t;
    }
}
