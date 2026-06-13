package com.uniovi.rag.application.service.evaluation.metrics;

import com.uniovi.rag.domain.evaluation.workbook.RagExperimentalPresetCode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RagPresetAnalysisMetricsTest {

    @Test
    void unanswerableCorrectAbstentionScoresOne() {
        Map<String, Object> mp = new LinkedHashMap<>();
        mp.put(DatasetMetricContract.KEY_ANSWERABILITY, Answerability.UNANSWERABLE.name());
        mp.put("abstentionTriggered", true);

        Map<String, Object> out =
                RagPresetAnalysisMetrics.compute(mp, "n/a", "Insufficient evidence.", RagExperimentalPresetCode.P3);

        assertThat(out.get("finalScore")).isEqualTo(1.0);
        assertThat(out.get("correctAbstention")).isEqualTo(true);
        assertThat(out.get("wrongAbstention")).isEqualTo(false);
    }

    @Test
    void answerableWrongAbstentionScoresZero() {
        Map<String, Object> mp = new LinkedHashMap<>();
        mp.put(DatasetMetricContract.KEY_ANSWERABILITY, Answerability.ANSWERABLE.name());
        mp.put("abstentionTriggered", true);

        Map<String, Object> out =
                RagPresetAnalysisMetrics.compute(mp, "Paris", "Insufficient evidence.", RagExperimentalPresetCode.P3);

        assertThat(out.get("finalScore")).isEqualTo(0.0);
        assertThat(out.get("wrongAbstention")).isEqualTo(true);
    }

    @Test
    void missingAnswerability_staysUnknown() {
        Map<String, Object> out =
                RagPresetAnalysisMetrics.compute(Map.of(), "Paris", "Paris", RagExperimentalPresetCode.P0);
        assertThat(out.get(DatasetMetricContract.KEY_ANSWERABILITY)).isEqualTo(Answerability.UNKNOWN.name());
    }

    @Test
    void retrievalQualityUnavailableWithoutGoldLabels() {
        Map<String, Object> mp = new LinkedHashMap<>();
        mp.put("sourceCount", 2);
        mp.put("retrieved_chunk_ids", List.of("uuid-1"));

        Map<String, Object> out =
                RagPresetAnalysisMetrics.compute(mp, "x", "y", RagExperimentalPresetCode.P3);

        assertThat(out.get("retrievalQualityStatus")).isEqualTo(RetrievalQualityStatus.NOT_AVAILABLE.name());
        assertThat(out.get("retrievalCoverageStatus")).isEqualTo(CoverageStatus.HAS_CONTEXT.name());
        assertThat(out.get("recallAt1")).isNull();
    }

    @Test
    void compute_backfillsQueryTypeExpectedFromLegacyRowField() {
        Map<String, Object> mp = new LinkedHashMap<>(Map.of("query_type", "COUNT_DOCUMENTS"));
        Map<String, Object> out =
                RagPresetAnalysisMetrics.compute(mp, "3", "There are 3 documents.", RagExperimentalPresetCode.P3);
        assertThat(out.get("queryTypeExpected")).isEqualTo("COUNT_DOCUMENTS");
        assertThat(out.get("structuredScore")).isEqualTo(1.0);
    }
}
