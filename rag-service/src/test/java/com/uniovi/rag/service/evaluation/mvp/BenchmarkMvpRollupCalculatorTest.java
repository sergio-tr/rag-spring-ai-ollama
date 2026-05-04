package com.uniovi.rag.service.evaluation.mvp;

import com.uniovi.rag.application.service.evaluation.BenchmarkResultRowKeys;
import com.uniovi.rag.domain.evaluation.BenchmarkItemOutcome;
import com.uniovi.rag.domain.evaluation.BenchmarkKind;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationResultEntity;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BenchmarkMvpRollupCalculatorTest {

    @Test
    void globalMacro_separatesOutcomeCounts_fromExecutedMeans() {
        EvaluationResultEntity embExec = embeddingRow(BenchmarkItemOutcome.EXECUTED, 1.0, "FACTOID", "EASY");
        EvaluationResultEntity embNs =
                ragLikeRow(BenchmarkItemOutcome.NOT_SUPPORTED, "FACTOID", "EASY", "PRESET_X");

        Map<String, Object> roll = BenchmarkMvpRollupCalculator.build(List.of(embExec, embNs), null);
        @SuppressWarnings("unchecked")
        Map<String, Object> macro = (Map<String, Object>) roll.get("globalMacro");
        @SuppressWarnings("unchecked")
        Map<String, Long> oc = (Map<String, Long>) macro.get("outcomeCounts");
        assertThat(oc.get(BenchmarkItemOutcome.EXECUTED.name())).isEqualTo(1L);
        assertThat(oc.get(BenchmarkItemOutcome.NOT_SUPPORTED.name())).isEqualTo(1L);

        @SuppressWarnings("unchecked")
        Map<String, Object> retr = (Map<String, Object>) macro.get("retrievalOnExecutedWhereApplicable");
        assertThat(retr.get("n")).isEqualTo(1);
        assertThat(retr.get("meanRecallAt1")).isEqualTo(1.0);

        @SuppressWarnings("unchecked")
        Map<String, Long> unsup = (Map<String, Long>) macro.get("unsupportedReasons");
        assertThat(unsup.get("PRESET_X")).isEqualTo(1L);
    }

    private static EvaluationResultEntity embeddingRow(
            BenchmarkItemOutcome outcome, double r1, String queryType, String difficulty) {
        EvaluationResultEntity e = new EvaluationResultEntity();
        e.setBenchmarkKind(BenchmarkKind.EMBEDDING_RETRIEVAL.name());
        e.setQueryType(queryType);
        e.setExpectedAnswer("gold");
        e.setActualAnswer("x");
        e.setLatencyMs(1L);
        Map<String, Object> mp = new LinkedHashMap<>();
        mp.put(BenchmarkResultRowKeys.ITEM_OUTCOME, outcome.name());
        mp.put(BenchmarkResultRowKeys.DIFFICULTY, difficulty);
        mp.put("recall_at_1", r1);
        mp.put("recall_at_3", r1);
        mp.put("recall_at_5", r1);
        mp.put("mrr", r1);
        mp.put("retrieved_count", 3);
        mp.put("gold_found", r1 > 0);
        e.setMetricsPayload(mp);
        return e;
    }

    private static EvaluationResultEntity ragLikeRow(
            BenchmarkItemOutcome outcome, String queryType, String difficulty, String errorCode) {
        EvaluationResultEntity e = new EvaluationResultEntity();
        e.setBenchmarkKind(BenchmarkKind.RAG_PRESET_END_TO_END.name());
        e.setQueryType(queryType);
        e.setExpectedAnswer("a");
        e.setActualAnswer("");
        Map<String, Object> mp = new LinkedHashMap<>();
        mp.put(BenchmarkResultRowKeys.ITEM_OUTCOME, outcome.name());
        mp.put(BenchmarkResultRowKeys.DIFFICULTY, difficulty);
        mp.put(BenchmarkResultRowKeys.ERROR_CODE, errorCode);
        e.setMetricsPayload(mp);
        return e;
    }
}
