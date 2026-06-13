package com.uniovi.rag.application.service.evaluation.metrics;

import com.uniovi.rag.application.service.evaluation.BenchmarkResultRowKeys;
import com.uniovi.rag.application.service.runtime.RuntimeAnswerPrompts;
import com.uniovi.rag.domain.evaluation.BenchmarkItemOutcome;
import com.uniovi.rag.domain.evaluation.workbook.RagExperimentalPresetCode;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationResultEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationRunEntity;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RagPresetAnalysisMetricsScoreTest {

    @Test
    void phraseAbstention_onUnanswerable_scoresCorrectly() {
        Map<String, Object> mp = new LinkedHashMap<>();
        mp.put(DatasetMetricContract.KEY_ANSWERABILITY, Answerability.UNANSWERABLE.name());

        Map<String, Object> out =
                RagPresetAnalysisMetrics.compute(
                        mp,
                        "n/a",
                        RuntimeAnswerPrompts.INSUFFICIENT_DOCUMENT_CONTEXT_MESSAGE_EN,
                        RagExperimentalPresetCode.P3);

        assertThat(out.get("finalScore")).isEqualTo(1.0);
        assertThat(out.get("abstained")).isEqualTo(true);
        assertThat(out.get("abstentionCorrectness")).isEqualTo(AbstentionCorrectness.CORRECT.name());
        assertThat(out.get("abstentionScore")).isEqualTo(1.0);
    }

    @Test
    void missingStructuredGold_marksStructuredUnavailable() {
        Map<String, Object> mp = new LinkedHashMap<>(Map.of("query_type", "COUNT_DOCUMENTS"));
        Map<String, Object> out =
                RagPresetAnalysisMetrics.compute(mp, "", "12", RagExperimentalPresetCode.P3);

        assertThat(out.get("structuredScore")).isEqualTo(BenchmarkMvpSchema.NOT_AVAILABLE);
        assertThat(out.get("structuredScoreStatus")).isEqualTo(StructuredScoreStatus.NOT_AVAILABLE.name());
        assertThat(out.get("scoreUnavailableReason")).isEqualTo("no_scoring_signal");
    }

    @Test
    void ambiguousAnswerability_abstentionCorrectnessUnknown() {
        Map<String, Object> mp = new LinkedHashMap<>();
        mp.put(DatasetMetricContract.KEY_ANSWERABILITY, Answerability.AMBIGUOUS.name());
        mp.put("abstentionTriggered", true);

        Map<String, Object> out =
                RagPresetAnalysisMetrics.compute(mp, "x", "Insufficient evidence.", RagExperimentalPresetCode.P0);

        assertThat(out.get("abstentionCorrectness")).isEqualTo(AbstentionCorrectness.UNKNOWN.name());
        assertThat(out.get("abstentionScore")).isEqualTo(BenchmarkMvpSchema.NOT_AVAILABLE);
    }

    @Test
    void unavailableFinalScore_csvUsesNotAvailable_notZero() {
        Map<String, Object> mp = new LinkedHashMap<>(Map.of("query_type", "COUNT_DOCUMENTS"));
        EvaluationResultEntity item = new EvaluationResultEntity();
        item.setBenchmarkKind("RAG_PRESET_END_TO_END");
        item.setMetricsPayload(mp);
        item.setExpectedAnswer("");
        item.setActualAnswer("12");
        mp.put(BenchmarkResultRowKeys.ITEM_OUTCOME, BenchmarkItemOutcome.EXECUTED.name());
        mp.put(BenchmarkResultRowKeys.PRESET_CODE, "P3");

        Map<String, String> csv = BenchmarkMvpMetricsCalculator.computeMvpFlatCsvRow(item, new EvaluationRunEntity());
        assertThat(csv.get("finalScore")).isEqualTo(BenchmarkMvpSchema.NOT_AVAILABLE);
        assertThat(csv.get("finalScoreAvailable")).isEqualTo("false");
        assertThat(csv.get("finalScoreStatus")).isEqualTo(ScoreExportSupport.STATUS_UNAVAILABLE);
        assertThat(csv.get("scoreUnavailableReason")).isEqualTo("no_scoring_signal");
    }
}
