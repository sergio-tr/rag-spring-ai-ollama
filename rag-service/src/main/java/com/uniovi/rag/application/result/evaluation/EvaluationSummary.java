package com.uniovi.rag.application.result.evaluation;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Typed evaluation_summary for LAB judge benchmarks (LLM and RAG preset).
 */
public record EvaluationSummary(
        GenerationSummaryMetrics generation,
        RetrievalSummaryMetrics retrieval,
        Boolean cancelled,
        String cancelReason,
        Integer completedItems,
        Integer totalItems,
        Map<String, Object> extensions) {

    public EvaluationSummary {
        extensions = extensions != null ? Map.copyOf(extensions) : Map.of();
    }

    public static EvaluationSummary ofMetrics(GenerationSummaryMetrics generation, RetrievalSummaryMetrics retrieval) {
        return new EvaluationSummary(generation, retrieval, null, null, null, null, Map.of());
    }

    public EvaluationSummary withExtensions(Map<String, Object> extra) {
        if (extra == null || extra.isEmpty()) {
            return this;
        }
        LinkedHashMap<String, Object> merged = new LinkedHashMap<>(extensions);
        merged.putAll(extra);
        return new EvaluationSummary(generation, retrieval, cancelled, cancelReason, completedItems, totalItems, Map.copyOf(merged));
    }

    public EvaluationSummary withCancellation(
            boolean cancelled, String cancelReason, int completedItems, int totalItems) {
        return new EvaluationSummary(
                generation,
                retrieval,
                cancelled,
                cancelReason,
                completedItems,
                totalItems,
                extensions);
    }
}
