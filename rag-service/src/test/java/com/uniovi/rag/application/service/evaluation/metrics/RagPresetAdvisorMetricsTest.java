package com.uniovi.rag.application.service.evaluation.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RagPresetAdvisorMetricsTest {

    @Test
    void derivesAdvisorEnabledFromUseAdvisorFlag() {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("useAdvisor", true);
        RagPresetAdvisorMetrics.computeAndMerge(metrics);
        assertThat(metrics.get(RagPresetAdvisorMetrics.KEY_ADVISOR_ENABLED)).isEqualTo(true);
    }

    @Test
    void copiesAdvisorTelemetryFields() {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put(RagPresetAdvisorMetrics.KEY_ADVISOR_ROUTE, true);
        metrics.put(RagPresetAdvisorMetrics.KEY_ADVISOR_ATTEMPTED, true);
        metrics.put(RagPresetAdvisorMetrics.KEY_ADVISOR_APPLIED, true);
        metrics.put(RagPresetAdvisorMetrics.KEY_ADVISOR_NAME, "retrievalAdvisor");
        metrics.put(RagPresetAdvisorMetrics.KEY_ADVISOR_TYPE, "RETRIEVAL");
        metrics.put(RagPresetAdvisorMetrics.KEY_ADVISOR_CONTRIBUTION_TYPE, "retrieval_guidance,context_pack");
        metrics.put(RagPresetAdvisorMetrics.KEY_ADVISOR_CHANGED_CONTEXT, true);
        metrics.put(RagPresetAdvisorMetrics.KEY_ADVISOR_RESULT_USED, true);
        RagPresetAdvisorMetrics.computeAndMerge(metrics);
        assertThat(metrics.get(RagPresetAdvisorMetrics.KEY_ADVISOR_NAME)).isEqualTo("retrievalAdvisor");
        assertThat(metrics.get(RagPresetAdvisorMetrics.KEY_ADVISOR_APPLIED)).isEqualTo(true);
        assertThat(metrics.get(RagPresetAdvisorMetrics.KEY_ADVISOR_CHANGED_CONTEXT)).isEqualTo(true);
    }
}
