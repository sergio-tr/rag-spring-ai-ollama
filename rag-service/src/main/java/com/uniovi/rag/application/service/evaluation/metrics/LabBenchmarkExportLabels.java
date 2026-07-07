package com.uniovi.rag.application.service.evaluation.metrics;

import com.uniovi.rag.domain.evaluation.BenchmarkKind;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Human-readable labels for LAB benchmark exports and comparison tables.
 * Keeps machine keys in JSON while CSV/UI layers use normalized display values.
 */
public final class LabBenchmarkExportLabels {

    /** Rollup bucket key when grouping metadata is absent (replaces legacy {@code _UNKNOWN}). */
    public static final String MISSING_METADATA = "MISSING_METADATA";

    private LabBenchmarkExportLabels() {}

    public static String normalizeGroupKey(String raw) {
        if (raw == null) {
            return MISSING_METADATA;
        }
        String t = raw.trim();
        if (t.isEmpty() || "_UNKNOWN".equals(t) || MISSING_METADATA.equals(t)) {
            return MISSING_METADATA;
        }
        return t;
    }

    public static String comparisonAxis(BenchmarkKind kind) {
        if (kind == null) {
            return "UNKNOWN";
        }
        return switch (kind) {
            case LLM_JUDGE_QA -> "LLM_MODEL";
            case EMBEDDING_RETRIEVAL -> "EMBEDDING_MODEL";
            case RAG_PRESET_END_TO_END -> "PRESET_CODE";
            default -> "UNKNOWN";
        };
    }

    public static String comparisonAxisLabel(BenchmarkKind kind) {
        if (kind == null) {
            return "Comparison axis";
        }
        return switch (kind) {
            case LLM_JUDGE_QA -> "LLM model";
            case EMBEDDING_RETRIEVAL -> "Embedding model";
            case RAG_PRESET_END_TO_END -> "RAG preset";
            default -> "Comparison axis";
        };
    }

    public static String groupKeyForKind(BenchmarkKind kind) {
        if (kind == null) {
            return "runId";
        }
        return switch (kind) {
            case LLM_JUDGE_QA -> "modelId";
            case EMBEDDING_RETRIEVAL -> "embeddingModelId";
            case RAG_PRESET_END_TO_END -> "presetCode";
            default -> "runId";
        };
    }

    /** Display label for a comparison row value (model id, preset code, etc.). */
    public static String displayGroupValue(String groupKey, String groupValue) {
        String normalized = normalizeGroupKey(groupValue);
        if (MISSING_METADATA.equals(normalized)) {
            return "Missing metadata";
        }
        if ("presetCode".equals(groupKey) && normalized.startsWith("P")) {
            return normalized;
        }
        return normalized;
    }

    public static String metricUnavailableReason(String metricKey, BenchmarkKind kind, String outcome) {
        if (metricKey == null) {
            return "Metric not computed for this row.";
        }
        String mk = metricKey.trim();
        if (mk.startsWith("recall") || "mrr".equals(mk) || "retrievedCount".equals(mk) || "goldFound".equals(mk)) {
            if (kind == BenchmarkKind.LLM_JUDGE_QA) {
                return "Not applicable - direct LLM evaluation has no retrieval step.";
            }
            if ("FAILED".equals(outcome) || "SKIPPED".equals(outcome)) {
                return "Not available - run did not complete generation for this item.";
            }
            return "Not applicable - no retrieval metrics for this benchmark row.";
        }
        if ("llmJudgeScore".equals(mk) || "semanticScore".equals(mk) || "faithfulness".equals(mk) || "sourceSupport".equals(mk)) {
            return "Not available - judge did not score this item.";
        }
        if ("FAILED".equals(outcome)) {
            return "Not available - run failed before generation completed.";
        }
        if ("SKIPPED".equals(outcome) || "NOT_SUPPORTED".equals(outcome)) {
            return "Not applicable - item was skipped or not supported.";
        }
        return "Not available for this item.";
    }

    public static Map<String, String> humanSummaryCsvHeaders() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("campaignId", "Campaign ID");
        m.put("comparisonAxis", "Comparison axis");
        m.put("comparisonLabel", "Compared item");
        m.put("benchmarkKind", "Benchmark kind");
        m.put("runId", "Run ID");
        m.put("totalItems", "Total items");
        m.put("executed", "Executed");
        m.put("notSupported", "Not supported");
        m.put("failed", "Failed");
        m.put("skipped", "Skipped");
        m.put("meanExactMatch", "Mean exact match");
        m.put("meanSemanticScore", "Mean judge score");
        m.put("meanRecallAt1", "Mean recall@1");
        m.put("meanLatencyMs", "Mean latency (ms)");
        return m;
    }

    public static Map<String, String> humanItemsCsvHeaders() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("campaignId", "Campaign ID");
        m.put("runId", "Run ID");
        m.put("benchmarkKind", "Benchmark kind");
        m.put("datasetQuestionId", "Question ID");
        m.put("queryType", "Query type");
        m.put("modelId", "LLM model");
        m.put("embeddingModelId", "Embedding model");
        m.put("presetCode", "Preset code");
        m.put("presetLabel", "Preset label");
        m.put("outcome", "Outcome");
        m.put("correctness", "Correctness");
        m.put("llmJudgeScore", "Judge score");
        m.put("recallAt1", "Recall@1");
        m.put("latencyMs", "Latency (ms)");
        m.put("failureCode", "Failure code");
        m.put("unsupportedReason", "Unsupported reason");
        m.put("skipReason", "Skip reason");
        return m;
    }
}
