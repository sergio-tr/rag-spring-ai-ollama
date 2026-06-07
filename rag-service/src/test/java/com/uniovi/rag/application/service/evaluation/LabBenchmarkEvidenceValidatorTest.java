package com.uniovi.rag.application.service.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.uniovi.rag.domain.evaluation.BenchmarkItemOutcome;
import com.uniovi.rag.infrastructure.persistence.EvaluationResultRepository;
import com.uniovi.rag.infrastructure.persistence.EvaluationRunRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationResultEntity;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LabBenchmarkEvidenceValidatorTest {

    @Mock
    EvaluationResultRepository evaluationResultRepository;

    @Mock
    EvaluationRunRepository evaluationRunRepository;

    @InjectMocks
    LabBenchmarkEvidenceValidator validator;

    @Test
    void validateRun_expected60_executed60_passes() {
        UUID runId = UUID.randomUUID();
        when(evaluationRunRepository.findAggregatesJsonByRunId(runId))
                .thenReturn(Optional.of(Map.of("expectedItemCount", 60)));
        when(evaluationResultRepository.findByRun_IdOrderByEvaluatedAtAsc(runId))
                .thenReturn(items(60, BenchmarkItemOutcome.EXECUTED));
        assertThat(validator.validateRun(runId)).isEmpty();
        assertThat(validator.closureForRun(runId).get("classification"))
                .isEqualTo(RagBenchmarkOutcomeTally.CLASSIFICATION_COMPLETED_OK);
    }

    @Test
    void validateRun_expected60_skipped60_fails() {
        UUID runId = UUID.randomUUID();
        when(evaluationRunRepository.findAggregatesJsonByRunId(runId))
                .thenReturn(Optional.of(Map.of("expectedItemCount", 60)));
        when(evaluationResultRepository.findByRun_IdOrderByEvaluatedAtAsc(runId))
                .thenReturn(
                        items(
                                60,
                                BenchmarkItemOutcome.SKIPPED,
                                Map.of(
                                        BenchmarkResultRowKeys.ITEM_OUTCOME,
                                        BenchmarkItemOutcome.SKIPPED.name(),
                                        "skippedReasonCode",
                                        "CORPUS_EMPTY")));
        var failure = validator.validateRun(runId);
        assertThat(failure).isPresent();
        assertThat(failure.get().failureCode()).isEqualTo(LabBenchmarkEvidenceValidator.FAILURE_CODE_ALL_SKIPPED);
    }

    @Test
    void validateRun_expected60_failed10_executed50_passesWithFailuresClassification() {
        UUID runId = UUID.randomUUID();
        when(evaluationRunRepository.findAggregatesJsonByRunId(runId))
                .thenReturn(Optional.of(Map.of("expectedItemCount", 60)));
        ArrayList<EvaluationResultEntity> rows = new ArrayList<>(items(50, BenchmarkItemOutcome.EXECUTED));
        rows.addAll(items(10, BenchmarkItemOutcome.FAILED));
        when(evaluationResultRepository.findByRun_IdOrderByEvaluatedAtAsc(runId)).thenReturn(rows);
        assertThat(validator.validateRun(runId)).isEmpty();
        assertThat(validator.closureForRun(runId).get("classification"))
                .isEqualTo(RagBenchmarkOutcomeTally.CLASSIFICATION_COMPLETED_WITH_FAILURES);
    }

    @Test
    void validateRun_expected60_allNotSupported_fails() {
        UUID runId = UUID.randomUUID();
        when(evaluationRunRepository.findAggregatesJsonByRunId(runId))
                .thenReturn(Optional.of(Map.of("expectedItemCount", 60)));
        when(evaluationResultRepository.findByRun_IdOrderByEvaluatedAtAsc(runId))
                .thenReturn(
                        items(
                                60,
                                BenchmarkItemOutcome.NOT_SUPPORTED,
                                Map.of(
                                        BenchmarkResultRowKeys.ITEM_OUTCOME,
                                        BenchmarkItemOutcome.NOT_SUPPORTED.name(),
                                        BenchmarkResultRowKeys.ERROR_CODE,
                                        "PRESET_NOT_SUPPORTED",
                                        "humanReason",
                                        "This experimental preset is not supported with the current configuration.")));
        var failure = validator.validateRun(runId);
        assertThat(failure).isPresent();
        assertThat(failure.get().failureCode()).isEqualTo(LabBenchmarkEvidenceValidator.FAILURE_CODE_NO_EXECUTED_ITEMS);
    }

    @Test
    void validateRun_skipped_requiresReason() {
        UUID runId = UUID.randomUUID();
        when(evaluationRunRepository.findAggregatesJsonByRunId(runId))
                .thenReturn(Optional.of(Map.of("expectedItemCount", 2)));
        when(evaluationResultRepository.findByRun_IdOrderByEvaluatedAtAsc(runId))
                .thenReturn(
                        List.of(
                                item(
                                        BenchmarkItemOutcome.SKIPPED,
                                        Map.of(BenchmarkResultRowKeys.ITEM_OUTCOME, "SKIPPED")),
                                item(
                                        BenchmarkItemOutcome.EXECUTED,
                                        Map.of(BenchmarkResultRowKeys.ITEM_OUTCOME, "EXECUTED"))));
        var failure = validator.validateRun(runId);
        assertThat(failure).isPresent();
        assertThat(failure.get().failureCode())
                .isEqualTo(LabBenchmarkEvidenceValidator.FAILURE_CODE_SKIPPED_WITHOUT_REASON);
    }

    @Test
    void validateRun_countMismatch_failsValidation() {
        UUID runId = UUID.randomUUID();
        when(evaluationRunRepository.findAggregatesJsonByRunId(runId))
                .thenReturn(Optional.of(Map.of("expectedItemCount", 60)));
        when(evaluationResultRepository.findByRun_IdOrderByEvaluatedAtAsc(runId))
                .thenReturn(items(59, BenchmarkItemOutcome.EXECUTED));
        var failure = validator.validateRun(runId);
        assertThat(failure).isPresent();
        assertThat(failure.get().failureCode())
                .isEqualTo(LabBenchmarkEvidenceValidator.FAILURE_CODE_FAILED_VALIDATION);
    }

    @Test
    void validateRun_notSupported_requiresReason() {
        UUID runId = UUID.randomUUID();
        when(evaluationRunRepository.findAggregatesJsonByRunId(runId))
                .thenReturn(Optional.of(Map.of("expectedItemCount", 2)));
        when(evaluationResultRepository.findByRun_IdOrderByEvaluatedAtAsc(runId))
                .thenReturn(
                        List.of(
                                item(
                                        BenchmarkItemOutcome.NOT_SUPPORTED,
                                        Map.of(BenchmarkResultRowKeys.ITEM_OUTCOME, "NOT_SUPPORTED")),
                                item(
                                        BenchmarkItemOutcome.EXECUTED,
                                        Map.of(BenchmarkResultRowKeys.ITEM_OUTCOME, "EXECUTED"))));
        var failure = validator.validateRun(runId);
        assertThat(failure).isPresent();
        assertThat(failure.get().failureCode())
                .isEqualTo(LabBenchmarkEvidenceValidator.FAILURE_CODE_NOT_SUPPORTED_WITHOUT_REASON);
    }

    private static List<EvaluationResultEntity> items(int count, BenchmarkItemOutcome outcome) {
        return items(count, outcome, Map.of(BenchmarkResultRowKeys.ITEM_OUTCOME, outcome.name()));
    }

    private static List<EvaluationResultEntity> items(
            int count, BenchmarkItemOutcome outcome, Map<String, Object> extra) {
        return IntStream.range(0, count)
                .mapToObj(i -> item(outcome, extra))
                .toList();
    }

    private static EvaluationResultEntity item(BenchmarkItemOutcome outcome, Map<String, Object> extra) {
        EvaluationResultEntity e = new EvaluationResultEntity();
        Map<String, Object> mp = new LinkedHashMap<>(extra);
        mp.putIfAbsent(BenchmarkResultRowKeys.ITEM_OUTCOME, outcome.name());
        e.setMetricsPayload(mp);
        return e;
    }
}
