package com.uniovi.rag.application.service.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniovi.rag.domain.evaluation.BenchmarkItemOutcome;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationResultEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class RagBenchmarkOutcomeTallyTest {

    @Test
    void classifyCompletion_okWhenExecutedPresent() {
        var tally = RagBenchmarkOutcomeTally.fromResultRows(items(60, BenchmarkItemOutcome.EXECUTED), 60);
        assertThat(tally.classifyCompletion()).isEqualTo(RagBenchmarkOutcomeTally.CLASSIFICATION_COMPLETED_OK);
        assertThat(tally.accountedItems()).isEqualTo(60);
    }

    @Test
    void classifyCompletion_withFailures() {
        ArrayList<EvaluationResultEntity> rows = new ArrayList<>(items(50, BenchmarkItemOutcome.EXECUTED));
        rows.addAll(items(10, BenchmarkItemOutcome.FAILED));
        var tally = RagBenchmarkOutcomeTally.fromResultRows(rows, 60);
        assertThat(tally.classifyCompletion())
                .isEqualTo(RagBenchmarkOutcomeTally.CLASSIFICATION_COMPLETED_WITH_FAILURES);
    }

    @Test
    void classifyCompletion_allSkipped_noExecuted() {
        var tally = RagBenchmarkOutcomeTally.fromResultRows(items(60, BenchmarkItemOutcome.SKIPPED), 60);
        assertThat(tally.classifyCompletion())
                .isEqualTo(RagBenchmarkOutcomeTally.CLASSIFICATION_COMPLETED_WITH_NO_EXECUTED_ITEMS);
        assertThat(tally.executed()).isZero();
    }

    @Test
    void classifyCompletion_executedWithOnlyNotSupported() {
        ArrayList<EvaluationResultEntity> rows = new ArrayList<>(items(50, BenchmarkItemOutcome.EXECUTED));
        rows.addAll(items(10, BenchmarkItemOutcome.NOT_SUPPORTED));
        var tally = RagBenchmarkOutcomeTally.fromResultRows(rows, 60);
        assertThat(tally.classifyCompletion())
                .isEqualTo(RagBenchmarkOutcomeTally.CLASSIFICATION_COMPLETED_WITH_UNSUPPORTED);
    }

    @Test
    void detectSkippedWithoutReason() {
        EvaluationResultEntity e = new EvaluationResultEntity();
        e.setMetricsPayload(
                Map.of(BenchmarkResultRowKeys.ITEM_OUTCOME, BenchmarkItemOutcome.SKIPPED.name()));
        var tally = RagBenchmarkOutcomeTally.fromResultRows(List.of(e), 1);
        assertThat(tally.skippedMissingReason()).isTrue();
    }

    private static List<EvaluationResultEntity> items(int count, BenchmarkItemOutcome outcome) {
        return IntStream.range(0, count)
                .mapToObj(
                        i -> {
                            EvaluationResultEntity e = new EvaluationResultEntity();
                            e.setMetricsPayload(
                                    Map.of(
                                            BenchmarkResultRowKeys.ITEM_OUTCOME,
                                            outcome.name(),
                                            "skippedReasonCode",
                                            "CORPUS_EMPTY"));
                            return e;
                        })
                .toList();
    }
}
