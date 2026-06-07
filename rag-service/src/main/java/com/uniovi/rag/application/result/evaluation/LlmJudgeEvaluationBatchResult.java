package com.uniovi.rag.application.result.evaluation;

import java.util.List;
import java.util.Map;

/**
 * Typed result of evaluating a list of LLM reader questions with judge scoring.
 */
public record LlmJudgeEvaluationBatchResult(
        Map<String, Object> configurationSnapshot,
        List<LlmJudgeItemResult> results,
        EvaluationSummary evaluationSummary) {

    public LlmJudgeEvaluationBatchResult {
        configurationSnapshot =
                configurationSnapshot != null ? Map.copyOf(configurationSnapshot) : Map.of();
        results = results != null ? List.copyOf(results) : List.of();
    }
}
