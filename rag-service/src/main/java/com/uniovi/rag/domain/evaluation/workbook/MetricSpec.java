package com.uniovi.rag.domain.evaluation.workbook;

/** Row from {@code metric_spec} sheet. */
public record MetricSpec(
        String metricId,
        String scope,
        String description,
        String primaryFor,
        String formulaOrRule) {

    public MetricSpec {
        if (metricId == null || metricId.isBlank()) {
            throw new IllegalArgumentException("metricId required");
        }
    }
}
