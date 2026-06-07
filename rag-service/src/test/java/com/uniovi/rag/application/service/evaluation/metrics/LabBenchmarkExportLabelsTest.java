package com.uniovi.rag.application.service.evaluation.metrics;

import com.uniovi.rag.domain.evaluation.BenchmarkKind;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LabBenchmarkExportLabelsTest {

    @Test
    void normalizeGroupKey_replacesLegacyUnknown() {
        assertThat(LabBenchmarkExportLabels.normalizeGroupKey("_UNKNOWN")).isEqualTo(LabBenchmarkExportLabels.MISSING_METADATA);
        assertThat(LabBenchmarkExportLabels.normalizeGroupKey("")).isEqualTo(LabBenchmarkExportLabels.MISSING_METADATA);
        assertThat(LabBenchmarkExportLabels.normalizeGroupKey("llama3")).isEqualTo("llama3");
    }

    @Test
    void comparisonAxis_forLlmAndRag() {
        assertThat(LabBenchmarkExportLabels.comparisonAxis(BenchmarkKind.LLM_JUDGE_QA)).isEqualTo("LLM_MODEL");
        assertThat(LabBenchmarkExportLabels.comparisonAxis(BenchmarkKind.RAG_PRESET_END_TO_END)).isEqualTo("PRESET_CODE");
        assertThat(LabBenchmarkExportLabels.comparisonAxisLabel(BenchmarkKind.LLM_JUDGE_QA)).isEqualTo("LLM model");
    }

    @Test
    void displayGroupValue_usesMissingMetadataLabel() {
        assertThat(LabBenchmarkExportLabels.displayGroupValue("modelId", "_UNKNOWN")).isEqualTo("Missing metadata");
        assertThat(LabBenchmarkExportLabels.displayGroupValue("presetCode", "P2")).isEqualTo("P2");
    }

    @Test
    void metricUnavailableReason_forDirectLlmRetrieval() {
        String reason = LabBenchmarkExportLabels.metricUnavailableReason(
                "recallAt1", BenchmarkKind.LLM_JUDGE_QA, "EXECUTED");
        assertThat(reason).contains("no retrieval");
    }
}
