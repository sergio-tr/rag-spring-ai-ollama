package com.uniovi.rag.application.result.evaluation;

import java.util.List;
import java.util.Map;

/**
 * Full RAG preset benchmark run (orchestrator output), including enriched LAB rows for persistence/export.
 */
public record RagPresetBenchmarkRunPayload(
        Map<String, Object> configuration,
        List<LlmJudgeItemResult> results,
        EvaluationSummary evaluationSummary) {

    public RagPresetBenchmarkRunPayload {
        configuration = configuration != null ? Map.copyOf(configuration) : Map.of();
        results = results != null ? List.copyOf(results) : List.of();
    }
}
