package com.uniovi.rag.application.result.evaluation;

import java.util.List;
import java.util.Map;

/**
 * Typed result of evaluating RAG preset question rows through the product query runtime.
 */
public record RagPresetEvaluationBatchResult(
        Map<String, Object> configurationSnapshot,
        List<LlmJudgeItemResult> results,
        EvaluationSummary evaluationSummary) {

    public RagPresetEvaluationBatchResult {
        configurationSnapshot =
                configurationSnapshot != null ? Map.copyOf(configurationSnapshot) : Map.of();
        results = results != null ? List.copyOf(results) : List.of();
    }
}
