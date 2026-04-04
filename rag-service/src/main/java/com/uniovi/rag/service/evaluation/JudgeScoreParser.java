package com.uniovi.rag.service.evaluation;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses judge scores from {@code llm_evaluation} text blocks (same patterns as {@link AbstractEvaluationService}).
 */
public final class JudgeScoreParser {

    private static final Pattern CORRECTNESS_LINE = Pattern.compile(
            "Correctness\\s*:\\s*\\[?\\s*([1-5])\\.?\\d*\\s*\\]?",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern CONTEXT_SUFFICIENCY_LINE = Pattern.compile(
            "Context\\s+Sufficiency\\s*:\\s*\\[?\\s*([1-5])\\.?\\d*\\s*\\]?",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern RELEVANCE_LINE = Pattern.compile(
            "Relevance\\s*:\\s*\\[?\\s*([1-5])\\.?\\d*\\s*\\]?",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern INDEPENDENCE_LINE = Pattern.compile(
            "Independence\\s*:\\s*\\[?\\s*([1-5])\\.?\\d*\\s*\\]?",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern GROUNDEDNESS_LINE = Pattern.compile(
            "Groundedness\\s*:\\s*\\[?\\s*([1-5])\\.?\\d*\\s*\\]?",
            Pattern.CASE_INSENSITIVE
    );

    private JudgeScoreParser() {
    }

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
}
