package com.uniovi.rag.application.service.evaluation;

import com.uniovi.rag.application.result.evaluation.EvaluationSummary;
import com.uniovi.rag.application.result.evaluation.LlmJudgeEvaluationBatchResult;
import com.uniovi.rag.application.result.evaluation.LlmJudgeItemResult;
import com.uniovi.rag.application.result.evaluation.RagPresetBenchmarkRunPayload;
import com.uniovi.rag.application.result.evaluation.RagPresetEvaluationBatchResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Shared typed evaluation fixtures for unit tests. */
public final class EvaluationTestFixtures {

    private EvaluationTestFixtures() {}

    public static EvaluationSummary emptySummary() {
        return EvaluationSummaryBuilder.summarize(List.of());
    }

    public static LlmJudgeEvaluationBatchResult emptyLlmBatch() {
        return new LlmJudgeEvaluationBatchResult(Map.of(), List.of(), emptySummary());
    }

    public static RagPresetEvaluationBatchResult emptyRagBatch() {
        return new RagPresetEvaluationBatchResult(Map.of(), List.of(), emptySummary());
    }

    public static RagPresetBenchmarkRunPayload emptyRagRunPayload() {
        return new RagPresetBenchmarkRunPayload(Map.of(), List.of(), emptySummary());
    }

    public static RagPresetEvaluationBatchResult ragBatchFromRowMaps(List<Map<String, Object>> rows) {
        List<LlmJudgeItemResult> typed = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            typed.add(EvaluationPayloadMapper.fromRowMap(row));
        }
        return new RagPresetEvaluationBatchResult(Map.of(), typed, emptySummary());
    }

    public static List<Map<String, Object>> toRowMaps(RagPresetBenchmarkRunPayload payload) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (LlmJudgeItemResult row : payload.results()) {
            out.add(new LinkedHashMap<>(EvaluationPayloadMapper.toRowMap(row)));
        }
        return out;
    }
}
