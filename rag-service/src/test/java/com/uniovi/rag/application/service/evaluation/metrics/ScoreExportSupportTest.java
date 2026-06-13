package com.uniovi.rag.application.service.evaluation.metrics;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScoreExportSupportTest {

    @Test
    void unavailableScore_exportsNotAvailableForCsv() {
        Map<String, Object> analysis = new LinkedHashMap<>();
        analysis.put("finalScore", 0.0);
        analysis.put(RagPresetAnalysisMetrics.KEY_SCORE_UNAVAILABLE_REASON, "no_scoring_signal");

        assertThat(ScoreExportSupport.isFinalScoreAvailable(analysis)).isFalse();
        assertThat(ScoreExportSupport.finalScoreStatus(analysis)).isEqualTo(ScoreExportSupport.STATUS_UNAVAILABLE);
        assertThat(ScoreExportSupport.formatFinalScoreForCsv(analysis)).isEqualTo(BenchmarkMvpSchema.NOT_AVAILABLE);
    }

    @Test
    void availableScore_exportsNumericForCsv() {
        Map<String, Object> analysis = new LinkedHashMap<>();
        analysis.put("finalScore", 0.85);

        assertThat(ScoreExportSupport.isFinalScoreAvailable(analysis)).isTrue();
        assertThat(ScoreExportSupport.formatFinalScoreForCsv(analysis)).isEqualTo("0.85");
    }
}
