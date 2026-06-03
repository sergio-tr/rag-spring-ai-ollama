package com.uniovi.rag.application.service.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uniovi.rag.application.service.async.AsyncTaskMutationService;
import com.uniovi.rag.domain.evaluation.BenchmarkItemOutcome;
import com.uniovi.rag.infrastructure.persistence.EvaluationResultRepository;
import com.uniovi.rag.infrastructure.persistence.EvaluationRunRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationResultEntity;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LabBenchmarkCompletionServiceTest {

    @Mock
    EvaluationResultRepository evaluationResultRepository;

    @Mock
    EvaluationRunRepository evaluationRunRepository;

    @Mock
    AsyncTaskMutationService mutation;

    @InjectMocks
    LabBenchmarkEvidenceValidator evidenceValidator;

    private LabBenchmarkCompletionService service() {
        return new LabBenchmarkCompletionService(evidenceValidator);
    }

    @Test
    void completeRun_rejectsAllSkipped() {
        UUID taskId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        when(evaluationRunRepository.findAggregatesJsonByRunId(runId))
                .thenReturn(Optional.of(Map.of("expectedItemCount", 3)));
        when(evaluationResultRepository.findByRun_IdOrderByEvaluatedAtAsc(runId))
                .thenReturn(skippedItems(3));

        service().completeRun(mutation, taskId, runId, Map.of());

        verify(mutation)
                .markFailed(
                        eq(taskId),
                        any(),
                        eq(LabBenchmarkEvidenceValidator.FAILURE_CODE_ALL_SKIPPED));
        verify(mutation, never()).markSucceeded(eq(taskId), any());
    }

    @Test
    void completeRun_attachesBenchmarkClosureOnSuccess() {
        UUID taskId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        when(evaluationRunRepository.findAggregatesJsonByRunId(runId))
                .thenReturn(Optional.of(Map.of("expectedItemCount", 2)));
        when(evaluationResultRepository.findByRun_IdOrderByEvaluatedAtAsc(runId))
                .thenReturn(executedItems(2));

        service().completeRun(mutation, taskId, runId, Map.of("kind", "RAG"));

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(mutation).markSucceeded(eq(taskId), captor.capture());
        assertThat(captor.getValue().get("benchmarkClosure")).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> closure = (Map<String, Object>) captor.getValue().get("benchmarkClosure");
        assertThat(closure.get("executedItems")).isEqualTo(2L);
        assertThat(closure.get("classification"))
                .isEqualTo(RagBenchmarkOutcomeTally.CLASSIFICATION_COMPLETED_OK);
    }

    private static List<EvaluationResultEntity> executedItems(int count) {
        return IntStream.range(0, count)
                .mapToObj(
                        i -> {
                            EvaluationResultEntity e = new EvaluationResultEntity();
                            e.setMetricsPayload(
                                    Map.of(
                                            BenchmarkResultRowKeys.ITEM_OUTCOME,
                                            BenchmarkItemOutcome.EXECUTED.name()));
                            return e;
                        })
                .toList();
    }

    private static List<EvaluationResultEntity> skippedItems(int count) {
        return IntStream.range(0, count)
                .mapToObj(
                        i -> {
                            EvaluationResultEntity e = new EvaluationResultEntity();
                            e.setMetricsPayload(
                                    Map.of(
                                            BenchmarkResultRowKeys.ITEM_OUTCOME,
                                            BenchmarkItemOutcome.SKIPPED.name(),
                                            "skippedReasonCode",
                                            "CORPUS_EMPTY",
                                            "skippedReason",
                                            "Knowledge base empty"));
                            return e;
                        })
                .toList();
    }
}
