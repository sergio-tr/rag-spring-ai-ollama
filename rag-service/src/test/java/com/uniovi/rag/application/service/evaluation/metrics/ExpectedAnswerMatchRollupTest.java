package com.uniovi.rag.application.service.evaluation.metrics;

import com.uniovi.rag.application.service.evaluation.BenchmarkResultRowKeys;
import com.uniovi.rag.application.service.evaluation.metrics.matching.ExpectedAnswerMatchResult;
import com.uniovi.rag.application.service.evaluation.metrics.matching.ExpectedAnswerMatchType;
import com.uniovi.rag.domain.evaluation.BenchmarkItemOutcome;
import com.uniovi.rag.domain.evaluation.BenchmarkKind;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationResultEntity;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExpectedAnswerMatchRollupTest {

    @Test
    void rollup_exposesCalibratedRates() {
        EvaluationResultEntity matched =
                calibratedRow(Answerability.ANSWERABLE, true, ExpectedAnswerMatchType.NUMERIC_VALUE_MATCH);
        EvaluationResultEntity noMatch = calibratedRow(Answerability.ANSWERABLE, false, ExpectedAnswerMatchType.NO_MATCH);
        EvaluationResultEntity negative =
                calibratedRow(Answerability.UNANSWERABLE, true, ExpectedAnswerMatchType.NEGATIVE_EQUIVALENCE);
        EvaluationResultEntity unsafe =
                calibratedRow(Answerability.UNANSWERABLE, false, ExpectedAnswerMatchType.UNSAFE_TO_JUDGE);

        Map<String, Object> roll =
                BenchmarkMvpRollupCalculator.build(List.of(matched, noMatch, negative, unsafe), null);
        @SuppressWarnings("unchecked")
        Map<String, Object> macro = (Map<String, Object>) roll.get("globalMacro");
        @SuppressWarnings("unchecked")
        Map<String, Object> onExecuted = (Map<String, Object>) macro.get("onExecuted");

        assertThat(onExecuted.get("calibratedExpectedAnswerMatchRate")).isEqualTo(0.5);
        assertThat(onExecuted.get("calibratedMatchRateAnswerable")).isEqualTo(0.5);
        assertThat(onExecuted.get("calibratedMatchRateUnanswerable")).isEqualTo(0.5);
        assertThat(onExecuted.get("calibratedCorrectNegativeRate")).isEqualTo(0.5);
        assertThat(onExecuted.get("calibratedNoMatchRate")).isEqualTo(0.25);
        assertThat(onExecuted.get("unsafeToJudgeRate")).isEqualTo(0.25);
    }

    @Test
    void byFinalAnswerSource_groupsCalibratedRows() {
        EvaluationResultEntity toolFinal =
                calibratedRow(Answerability.ANSWERABLE, true, ExpectedAnswerMatchType.RAW_CONTAINS);
        toolFinal.getMetricsPayload().put("finalAnswerSource", "TOOL_FINAL");

        Map<String, Object> roll = BenchmarkMvpRollupCalculator.build(List.of(toolFinal), null);
        @SuppressWarnings("unchecked")
        Map<String, Object> bySource = (Map<String, Object>) roll.get("byFinalAnswerSource");
        assertThat(bySource).containsKey("TOOL_FINAL");
    }

    private static EvaluationResultEntity calibratedRow(
            Answerability answerability, boolean matched, ExpectedAnswerMatchType type) {
        EvaluationResultEntity entity = new EvaluationResultEntity();
        entity.setBenchmarkKind(BenchmarkKind.RAG_PRESET_END_TO_END.name());
        entity.setExpectedAnswer("expected answer text");
        entity.setActualAnswer("actual answer text");
        Map<String, Object> mp = new LinkedHashMap<>();
        mp.put(BenchmarkResultRowKeys.ITEM_OUTCOME, BenchmarkItemOutcome.EXECUTED.name());
        mp.put(BenchmarkResultRowKeys.PRESET_CODE, "P3");
        mp.put("analysisVersion", RagPresetAnalysisMetrics.ANALYSIS_VERSION);
        mp.put(DatasetMetricContract.KEY_ANSWERABILITY, answerability.name());
        mp.put("finalScore", matched ? 1.0 : 0.0);
        mp.put("scoreFinal", matched ? 1.0 : 0.0);
        mp.put("retrievalCoverageStatus", CoverageStatus.HAS_CONTEXT.name());
        mp.put("sourceCoverageStatus", CoverageStatus.HAS_CONTEXT.name());
        mp.put("queryTypeMatch", RagPresetAnalysisMetrics.QueryTypeMatch.MATCH.name());
        mp.put("workflowName", "ChunkDenseWorkflow");
        mp.put(ExpectedAnswerMatchResult.KEY_MATCHED, matched);
        mp.put(ExpectedAnswerMatchResult.KEY_MATCH_TYPE, type.name());
        mp.put(ExpectedAnswerMatchResult.KEY_MATCH_CONFIDENCE, "HIGH");
        mp.put(ExpectedAnswerMatchResult.KEY_MATCH_REASON, "test");
        mp.put(ExpectedAnswerMatchResult.KEY_MATCH_VERSION, "1");
        mp.put(ExpectedAnswerMatchResult.KEY_CONTAINED_RAW, false);
        entity.setMetricsPayload(mp);
        return entity;
    }
}
