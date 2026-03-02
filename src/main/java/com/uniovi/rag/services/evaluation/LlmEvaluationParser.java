package com.uniovi.rag.services.evaluation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses LLM evaluation text (llm_evaluation) to extract numeric scores for
 * Correctness, Context Sufficiency, Relevance, Independence, and Groundedness.
 * Used to build evaluation_summary (F.3, F.4).
 */
public final class LlmEvaluationParser {

    private static final Pattern CORRECTNESS_LINE = Pattern.compile(
            "Correctness\\s*:\\s*([1-5])",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern CONTEXT_SUFFICIENCY_LINE = Pattern.compile(
            "Context Sufficiency\\s*:\\s*([1-5])",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern RELEVANCE_LINE = Pattern.compile(
            "Relevance\\s*:\\s*([1-5])",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern INDEPENDENCE_LINE = Pattern.compile(
            "Independence\\s*:\\s*([1-5])",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern GROUNDEDNESS_LINE = Pattern.compile(
            "Groundedness\\s*:\\s*([1-5])",
            Pattern.CASE_INSENSITIVE
    );

    private LlmEvaluationParser() {}

    /**
     * Parses a single llm_evaluation text block and returns a map with keys
     * correctness, context_sufficiency, relevance, independence, groundedness (Integer 1-5 or null if not found).
     */
    public static Map<String, Integer> parseScores(String llmEvaluation) {
        Map<String, Integer> scores = new HashMap<>();
        if (llmEvaluation == null || llmEvaluation.isBlank()) {
            return scores;
        }
        scores.put("correctness", extractScore(llmEvaluation, CORRECTNESS_LINE));
        scores.put("context_sufficiency", extractScore(llmEvaluation, CONTEXT_SUFFICIENCY_LINE));
        scores.put("relevance", extractScore(llmEvaluation, RELEVANCE_LINE));
        scores.put("independence", extractScore(llmEvaluation, INDEPENDENCE_LINE));
        scores.put("groundedness", extractScore(llmEvaluation, GROUNDEDNESS_LINE));
        return scores;
    }

    private static Integer extractScore(String text, Pattern pattern) {
        Matcher m = pattern.matcher(text);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    /**
     * Builds evaluation_summary from a list of per-question results.
     * Each result map should contain "llm_evaluation" (String).
     * Returns a map with structure: generation (mean_*, pct_correctness_ge_4), retrieval (placeholder).
     */
    public static Map<String, Object> buildEvaluationSummary(List<Map<String, Object>> results) {
        Map<String, Object> summary = new HashMap<>();
        Map<String, Object> generation = new HashMap<>();

        List<Integer> correctness = new ArrayList<>();
        List<Integer> contextSufficiency = new ArrayList<>();
        List<Integer> relevance = new ArrayList<>();
        List<Integer> independence = new ArrayList<>();
        List<Integer> groundedness = new ArrayList<>();

        for (Map<String, Object> r : results) {
            Object evalObj = r.get("llm_evaluation");
            String evalText = evalObj instanceof String ? (String) evalObj : (evalObj != null ? evalObj.toString() : null);
            Map<String, Integer> scores = parseScores(evalText);
            addIfNotNull(correctness, scores.get("correctness"));
            addIfNotNull(contextSufficiency, scores.get("context_sufficiency"));
            addIfNotNull(relevance, scores.get("relevance"));
            addIfNotNull(independence, scores.get("independence"));
            addIfNotNull(groundedness, scores.get("groundedness"));
        }

        generation.put("mean_correctness", mean(correctness));
        generation.put("mean_context_sufficiency", mean(contextSufficiency));
        generation.put("mean_relevance", mean(relevance));
        generation.put("mean_independence", mean(independence));
        generation.put("mean_groundedness", mean(groundedness));

        int total = correctness.size();
        long ge4 = correctness.stream().filter(s -> s != null && s >= 4).count();
        generation.put("pct_correctness_ge_4", total > 0 ? (100.0 * ge4 / total) : null);
        generation.put("n_parsed", total);

        generation.put("bleu", null);
        generation.put("rouge_l", null);
        generation.put("meteor", null);

        summary.put("generation", generation);
        summary.put("retrieval", Map.of(
                "mean_context_sufficiency", generation.get("mean_context_sufficiency"),
                "precision_at_k", (Object) null,
                "recall_at_k", (Object) null,
                "mrr", (Object) null
        ));
        return summary;
    }

    private static void addIfNotNull(List<Integer> list, Integer value) {
        if (value != null) {
            list.add(value);
        }
    }

    private static Double mean(List<Integer> values) {
        if (values == null || values.isEmpty()) return null;
        double sum = 0;
        for (Integer v : values) {
            if (v != null) sum += v;
        }
        return sum / values.size();
    }
}
