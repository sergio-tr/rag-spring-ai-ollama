package com.uniovi.rag.application.service.evaluation;

import com.uniovi.rag.application.result.evaluation.EvaluationSummary;
import com.uniovi.rag.application.result.evaluation.GenerationSummaryMetrics;
import com.uniovi.rag.application.result.evaluation.JudgeSummarizableRow;
import com.uniovi.rag.application.result.evaluation.LlmJudgeEvaluationBatchResult;
import com.uniovi.rag.application.result.evaluation.LlmJudgeItemResult;
import com.uniovi.rag.application.result.evaluation.RagPresetBenchmarkRunPayload;
import com.uniovi.rag.application.result.evaluation.RetrievalSummaryMetrics;
import com.uniovi.rag.application.service.evaluation.AbstractEvaluationService;
import com.uniovi.rag.domain.evaluation.BenchmarkItemOutcome;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps typed LAB evaluation results to JSON-shaped maps only at persistence/async-task boundaries.
 */
public final class EvaluationPayloadMapper {

    private static final String JSON_KEY_CORRECT_ANSWER = "correct_answer";
    private static final String JSON_KEY_GENERATED_ANSWER = "generated_answer";
    private static final String JSON_KEY_METRICS_PAYLOAD = "metrics_payload";

    private EvaluationPayloadMapper() {}

    public static Map<String, Object> toAsyncPayload(LlmJudgeEvaluationBatchResult batch) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("configuration", batch.configurationSnapshot());
        out.put("results", batch.results().stream().map(EvaluationPayloadMapper::toRowMap).toList());
        out.put("evaluation_summary", summaryToMap(batch.evaluationSummary()));
        return out;
    }

    public static Map<String, Object> toAsyncPayload(RagPresetBenchmarkRunPayload payload) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("configuration", payload.configuration());
        out.put("results", payload.results().stream().map(EvaluationPayloadMapper::toRowMap).toList());
        out.put("evaluation_summary", summaryToMap(payload.evaluationSummary()));
        return out;
    }

    public static LlmJudgeEvaluationBatchResult fromAsyncPayload(Map<String, Object> payload) {
        if (payload == null) {
            return new LlmJudgeEvaluationBatchResult(Map.of(), List.of(), EvaluationSummaryBuilder.summarize(List.of()));
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> configuration =
                payload.get("configuration") instanceof Map<?, ?> m
                        ? new LinkedHashMap<>((Map<String, Object>) m)
                        : Map.of();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rawRows =
                payload.get("results") instanceof List<?> list
                        ? (List<Map<String, Object>>) list
                        : List.of();
        List<LlmJudgeItemResult> rows = rawRows.stream().map(EvaluationPayloadMapper::fromRowMap).toList();
        @SuppressWarnings("unchecked")
        Map<String, Object> summaryMap =
                payload.get("evaluation_summary") instanceof Map<?, ?> sm
                        ? new LinkedHashMap<>((Map<String, Object>) sm)
                        : null;
        EvaluationSummary summary =
                summaryMap != null ? summaryFromMap(summaryMap) : EvaluationSummaryBuilder.summarize(rows);
        return new LlmJudgeEvaluationBatchResult(configuration, rows, summary);
    }

    public static LlmJudgeItemResult fromRowMap(Map<String, Object> row) {
        if (row == null) {
            return LlmJudgeItemResult.builder().build();
        }
        BenchmarkItemOutcome outcome = BenchmarkItemOutcome.EXECUTED;
        Object outcomeRaw = row.get(BenchmarkResultRowKeys.ITEM_OUTCOME);
        if (outcomeRaw != null) {
            try {
                outcome = BenchmarkItemOutcome.valueOf(outcomeRaw.toString());
            } catch (IllegalArgumentException ignored) {
                outcome = BenchmarkItemOutcome.EXECUTED;
            }
        }
        return LlmJudgeItemResult.builder()
                .question(str(row.get("question")))
                .correctAnswer(str(row.get(JSON_KEY_CORRECT_ANSWER)))
                .generatedAnswer(str(row.get(JSON_KEY_GENERATED_ANSWER)))
                .llmEvaluation(str(row.get("llm_evaluation")))
                .toolUsed(str(row.get("tool_used")))
                .queryType(str(row.get("query_type")))
                .usedTool(Boolean.TRUE.equals(row.get("used_tool")))
                .datasetQuestionId(str(row.get(BenchmarkResultRowKeys.DATASET_QUESTION_ID)))
                .itemOutcome(outcome)
                .latencyMs(row.get(BenchmarkResultRowKeys.LATENCY_MS) instanceof Number n ? n.longValue() : null)
                .errorCode(str(row.get(BenchmarkResultRowKeys.ERROR_CODE)))
                .reason(str(row.get(BenchmarkResultRowKeys.REASON)))
                .errorMessage(str(row.get("error")))
                .presetCode(str(row.get(BenchmarkResultRowKeys.PRESET_CODE)))
                .presetLabel(str(row.get(BenchmarkResultRowKeys.PRESET_LABEL)))
                .difficulty(str(row.get(BenchmarkResultRowKeys.DIFFICULTY)))
                .evaluationProtocol(str(row.get("benchmark_protocol")))
                .llmModelId(str(row.get(BenchmarkResultRowKeys.LLM_MODEL_ID)))
                .embeddingModelId(str(row.get(BenchmarkResultRowKeys.EMBEDDING_MODEL_ID)))
                .chatTelemetry(copyMap(row.get(AbstractEvaluationService.EVALUATION_CHAT_TELEMETRY_ROW_KEY)))
                .baselineMetrics(copyMap(row.get("baseline_metrics")))
                .labMetricsPayload(copyMap(row.get(JSON_KEY_METRICS_PAYLOAD)))
                .build();
    }

    public static Map<String, Object> toRowMap(LlmJudgeItemResult row) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (row.question() != null) {
            m.put("question", row.question());
        }
        m.put(JSON_KEY_CORRECT_ANSWER, row.correctAnswer() != null ? row.correctAnswer() : "");
        m.put(JSON_KEY_GENERATED_ANSWER, row.generatedAnswer() != null ? row.generatedAnswer() : "");
        m.put("llm_evaluation", row.llmEvaluation() != null ? row.llmEvaluation() : "");
        if (row.toolUsed() != null) {
            m.put("tool_used", row.toolUsed());
        }
        if (row.queryType() != null) {
            m.put("query_type", row.queryType());
        }
        m.put("used_tool", row.usedTool());
        if (row.datasetQuestionId() != null) {
            m.put(BenchmarkResultRowKeys.DATASET_QUESTION_ID, row.datasetQuestionId());
        }
        if (row.itemOutcome() != null) {
            m.put(BenchmarkResultRowKeys.ITEM_OUTCOME, row.itemOutcome().name());
        }
        if (row.latencyMs() != null) {
            m.put(BenchmarkResultRowKeys.LATENCY_MS, row.latencyMs());
        }
        if (row.errorCode() != null) {
            m.put(BenchmarkResultRowKeys.ERROR_CODE, row.errorCode());
        }
        if (row.reason() != null) {
            m.put(BenchmarkResultRowKeys.REASON, row.reason());
        }
        if (row.errorMessage() != null) {
            m.put("error", row.errorMessage());
        }
        if (row.presetCode() != null) {
            m.put(BenchmarkResultRowKeys.PRESET_CODE, row.presetCode());
        }
        if (row.presetLabel() != null) {
            m.put(BenchmarkResultRowKeys.PRESET_LABEL, row.presetLabel());
        }
        if (row.difficulty() != null) {
            m.put(BenchmarkResultRowKeys.DIFFICULTY, row.difficulty());
        }
        if (row.evaluationProtocol() != null) {
            m.put("benchmark_protocol", row.evaluationProtocol());
        }
        if (row.llmModelId() != null) {
            m.put(BenchmarkResultRowKeys.LLM_MODEL_ID, row.llmModelId());
        }
        if (row.embeddingModelId() != null) {
            m.put(BenchmarkResultRowKeys.EMBEDDING_MODEL_ID, row.embeddingModelId());
        }
        if (!row.chatTelemetry().isEmpty()) {
            m.put(AbstractEvaluationService.EVALUATION_CHAT_TELEMETRY_ROW_KEY, new LinkedHashMap<>(row.chatTelemetry()));
        }
        if (!row.baselineMetrics().isEmpty()) {
            m.put("baseline_metrics", new LinkedHashMap<>(row.baselineMetrics()));
        }
        if (!row.labMetricsPayload().isEmpty()) {
            m.put(JSON_KEY_METRICS_PAYLOAD, new LinkedHashMap<>(row.labMetricsPayload()));
        }
        return m;
    }

    public static List<JudgeSummarizableRow> summarizableFromRowMaps(List<Map<String, Object>> rows) {
        if (rows == null) {
            return List.of();
        }
        return new ArrayList<>(rows.stream().map(EvaluationPayloadMapper::fromRowMap).toList());
    }

    public static Map<String, Object> summaryToMap(EvaluationSummary summary) {
        if (summary == null) {
            return Map.of();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        if (summary.generation() != null) {
            out.put("generation", generationToMap(summary.generation()));
        }
        if (summary.retrieval() != null) {
            out.put("retrieval", retrievalToMap(summary.retrieval()));
        }
        if (Boolean.TRUE.equals(summary.cancelled())) {
            out.put("cancelled", true);
        }
        if (summary.cancelReason() != null) {
            out.put("cancel_reason", summary.cancelReason());
        }
        if (summary.completedItems() != null) {
            out.put("completed_items", summary.completedItems());
        }
        if (summary.totalItems() != null) {
            out.put("total_items", summary.totalItems());
        }
        if (summary.extensions() != null && !summary.extensions().isEmpty()) {
            out.putAll(summary.extensions());
        }
        return out;
    }

    public static EvaluationSummary summaryFromMap(Map<String, Object> summaryMap) {
        if (summaryMap == null || summaryMap.isEmpty()) {
            return EvaluationSummaryBuilder.summarize(List.of());
        }
        GenerationSummaryMetrics generation = null;
        RetrievalSummaryMetrics retrieval = null;
        if (summaryMap.get("generation") instanceof Map<?, ?> gm) {
            @SuppressWarnings("unchecked")
            Map<String, Object> gen = (Map<String, Object>) gm;
            generation =
                    new GenerationSummaryMetrics(
                            dbl(gen.get("mean_correctness")),
                            dbl(gen.get("mean_context_sufficiency")),
                            dbl(gen.get("mean_relevance")),
                            dbl(gen.get("mean_independence")),
                            dbl(gen.get("mean_groundedness")),
                            dbl(gen.get("pct_correctness_ge_4")),
                            intVal(gen.get("n_parsed")),
                            dbl(gen.get("bleu")),
                            dbl(gen.get("rouge_l")),
                            dbl(gen.get("meteor")));
        }
        if (summaryMap.get("retrieval") instanceof Map<?, ?> rm) {
            @SuppressWarnings("unchecked")
            Map<String, Object> ret = (Map<String, Object>) rm;
            retrieval =
                    new RetrievalSummaryMetrics(
                            dbl(ret.get("mean_context_sufficiency")),
                            dbl(ret.get("precision_at_k")),
                            dbl(ret.get("recall_at_k")),
                            dbl(ret.get("mrr")));
        }
        Map<String, Object> extensions = new LinkedHashMap<>(summaryMap);
        extensions.remove("generation");
        extensions.remove("retrieval");
        return new EvaluationSummary(
                generation,
                retrieval,
                bool(summaryMap.get("cancelled")),
                str(summaryMap.get("cancel_reason")),
                intVal(summaryMap.get("completed_items")),
                intVal(summaryMap.get("total_items")),
                extensions);
    }

    private static Map<String, Object> generationToMap(GenerationSummaryMetrics g) {
        Map<String, Object> m = new LinkedHashMap<>();
        putIfNotNull(m, "mean_correctness", g.meanCorrectness());
        putIfNotNull(m, "mean_context_sufficiency", g.meanContextSufficiency());
        putIfNotNull(m, "mean_relevance", g.meanRelevance());
        putIfNotNull(m, "mean_independence", g.meanIndependence());
        putIfNotNull(m, "mean_groundedness", g.meanGroundedness());
        putIfNotNull(m, "pct_correctness_ge_4", g.pctCorrectnessGe4());
        putIfNotNull(m, "n_parsed", g.nParsed());
        putIfNotNull(m, "bleu", g.bleu());
        putIfNotNull(m, "rouge_l", g.rougeL());
        putIfNotNull(m, "meteor", g.meteor());
        return m;
    }

    private static Map<String, Object> retrievalToMap(RetrievalSummaryMetrics r) {
        Map<String, Object> m = new LinkedHashMap<>();
        putIfNotNull(m, "mean_context_sufficiency", r.meanContextSufficiency());
        putIfNotNull(m, "precision_at_k", r.precisionAtK());
        putIfNotNull(m, "recall_at_k", r.recallAtK());
        putIfNotNull(m, "mrr", r.mrr());
        return m;
    }

    private static void putIfNotNull(Map<String, Object> m, String key, Object value) {
        if (value != null) {
            m.put(key, value);
        }
    }

    private static String str(Object v) {
        return v == null ? null : v.toString().trim();
    }

    private static Double dbl(Object v) {
        return v instanceof Number n ? n.doubleValue() : null;
    }

    private static Integer intVal(Object v) {
        return v instanceof Number n ? n.intValue() : null;
    }

    private static Boolean bool(Object v) {
        return v instanceof Boolean b ? b : null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> copyMap(Object v) {
        if (!(v instanceof Map<?, ?> m)) {
            return Map.of();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : m.entrySet()) {
            if (e.getKey() != null && e.getValue() != null) {
                out.put(String.valueOf(e.getKey()), e.getValue());
            }
        }
        return Map.copyOf(out);
    }
}
