package com.uniovi.rag.application.service.evaluation.metrics;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RagPresetClassifierMetricsTest {

    @Test
    void compute_promotesClassifierReliabilityFields() {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("classifierStatus", "OK");
        metrics.put("classifierConfidence", 0.91);
        metrics.put("classifierModelId", "default");
        metrics.put("classifierLabelSetHash", "hash123");
        metrics.put("predictedQueryType", "COUNT_DOCUMENTS");
        metrics.put(DatasetMetricContract.KEY_QUERY_TYPE_EXPECTED, "COUNT_DOCUMENTS");
        metrics.put(RagPresetClassifierMetrics.KEY_ROUTE_SUPPRESSED_BY_CLASSIFIER, false);
        metrics.put(RagPresetClassifierMetrics.KEY_HEURISTIC_ROUTE_USED, true);

        RagPresetClassifierMetrics.computeAndMerge(metrics);

        assertThat(metrics.get("queryTypePredicted")).isEqualTo("COUNT_DOCUMENTS");
        assertThat(metrics.get("queryTypeMatch")).isEqualTo("MATCH");
        assertThat(metrics.get("queryTypeSource")).isEqualTo(QueryTypeSource.CLASSIFIER.name());
        assertThat(metrics.get("classifierLabelSetHash")).isEqualTo("hash123");
        assertThat(metrics.get("heuristicRouteUsed")).isEqualTo(true);
    }

    @Test
    void compute_doesNotUseDatasetExpectedAsPrediction() {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("classifierStatus", "INVALID_OUTPUT");
        metrics.put(DatasetMetricContract.KEY_QUERY_TYPE_EXPECTED, "COUNT_DOCUMENTS");

        RagPresetClassifierMetrics.computeAndMerge(metrics);

        assertThat(metrics).doesNotContainKey("queryTypePredicted");
        assertThat(metrics.get("queryTypeSource")).isEqualTo(QueryTypeSource.DATASET_EXPECTED.name());
    }
}
