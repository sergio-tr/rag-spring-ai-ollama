package com.uniovi.rag.application.service.evaluation.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RagPresetToolMetricsTest {

    @Test
    void derivesQueryTypeSourceFromClassifier() {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("classifierStatus", "OK");
        metrics.put("queryTypePredicted", "COUNT_DOCUMENTS");
        metrics.put(DatasetMetricContract.KEY_QUERY_TYPE_EXPECTED, "COUNT_DOCUMENTS");
        RagPresetToolMetrics.computeAndMerge(metrics);
        assertThat(metrics.get("queryTypeSource")).isEqualTo(QueryTypeSource.CLASSIFIER.name());
        assertThat(metrics.get("toolCoverageStatus")).isEqualTo(ToolCoverageStatus.APPLICABLE.name());
    }

    @Test
    void derivesRoutingRouteKindFromExecutionRouteWhenMissing() {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put(RagPresetToolMetrics.KEY_EXECUTION_ROUTE, "FUNCTION_CALLING_ROUTE");
        RagPresetToolMetrics.computeAndMerge(metrics);
        assertThat(metrics.get(RagPresetToolMetrics.KEY_ROUTING_ROUTE_KIND))
                .isEqualTo("FUNCTION_CALLING_ROUTE");
    }

    @Test
    void copiesFunctionProposalTelemetryFields() {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put(RagPresetToolMetrics.KEY_FUNCTION_PROPOSAL_MODE, "BACKEND_DETERMINISTIC");
        metrics.put(RagPresetToolMetrics.KEY_FUNCTION_PROPOSAL_SOURCE, "QUERY_SHAPE");
        metrics.put(RagPresetToolMetrics.KEY_FUNCTION_PROPOSAL_VALID, true);
        metrics.put(RagPresetToolMetrics.KEY_BACKEND_FUNCTION_CALL_ATTEMPTED, true);
        metrics.put(RagPresetToolMetrics.KEY_FUNCTION_TOOL_KIND, "COUNT_DOCUMENTS_TOOL");
        RagPresetToolMetrics.computeAndMerge(metrics);
        assertThat(metrics.get(RagPresetToolMetrics.KEY_FUNCTION_PROPOSAL_MODE)).isEqualTo("BACKEND_DETERMINISTIC");
        assertThat(metrics.get(RagPresetToolMetrics.KEY_FUNCTION_TOOL_KIND)).isEqualTo("COUNT_DOCUMENTS_TOOL");
    }

    @Test
    void copiesFunctionCallingTelemetryFields() {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put(RagPresetToolMetrics.KEY_FUNCTION_CALL_ATTEMPTED, true);
        metrics.put(RagPresetToolMetrics.KEY_FUNCTION_CALLING_USED, true);
        metrics.put(RagPresetToolMetrics.KEY_FUNCTION_CALL_NAME, "countDocuments");
        metrics.put(RagPresetToolMetrics.KEY_FUNCTION_CALL_FALLBACK_REASON, "model_declined");
        RagPresetToolMetrics.computeAndMerge(metrics);
        assertThat(metrics.get(RagPresetToolMetrics.KEY_FUNCTION_CALL_NAME)).isEqualTo("countDocuments");
        assertThat(metrics.get(RagPresetToolMetrics.KEY_FUNCTION_CALL_FALLBACK_REASON)).isEqualTo("model_declined");
    }

    @Test
    void derivesDatasetExpectedWhenClassifierInvalid() {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("classifierStatus", "INVALID_OUTPUT");
        metrics.put(DatasetMetricContract.KEY_QUERY_TYPE_EXPECTED, "SUMMARIZE_TOPIC");
        RagPresetToolMetrics.computeAndMerge(metrics);
        assertThat(metrics.get("queryTypeSource")).isEqualTo(QueryTypeSource.DATASET_EXPECTED.name());
        assertThat(metrics.get("toolCoverageStatus")).isEqualTo(ToolCoverageStatus.NOT_APPLICABLE.name());
    }
}
