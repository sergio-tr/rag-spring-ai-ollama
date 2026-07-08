package com.uniovi.rag.application.service.evaluation.baseline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.application.service.evaluation.EvaluationService;
import com.uniovi.rag.application.service.evaluation.judge.EvaluationJudgeException;
import com.uniovi.rag.application.service.evaluation.metrics.BenchmarkMvpMetricsCalculator;
import com.uniovi.rag.application.service.evaluation.metrics.matching.ScoringValueExtractor;
import com.uniovi.rag.domain.evaluation.workbook.LlmRoleEvalCase;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/** MVP scoring for {@link LlmRoleEvalCase} rows (comma-separated {@code scoring_type} values). */
public final class LlmRoleEvalScorer {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private LlmRoleEvalScorer() {}

    public static Map<String, Object> score(
            LlmRoleEvalCase evalCase, String generated, EvaluationService evaluationService) {
        Map<String, Object> scores = new LinkedHashMap<>();
        List<String> types = parseScoringTypes(evalCase.scoringType());
        boolean allPassed = !types.isEmpty();
        String expected = evalCase.expectedOutput() != null ? evalCase.expectedOutput() : "";
        String actual = generated != null ? generated : "";

        for (String type : types) {
            Map<String, Object> one = scoreOne(type, evalCase, expected, actual, evaluationService);
            scores.put(type, one);
            if (!Boolean.TRUE.equals(one.get("passed"))) {
                allPassed = false;
            }
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("scoringTypes", types);
        out.put("scores", scores);
        out.put("roleEvalPassed", allPassed);
        out.put("scoringType", evalCase.scoringType());
        return out;
    }

    private static Map<String, Object> scoreOne(
            String type,
            LlmRoleEvalCase evalCase,
            String expected,
            String actual,
            EvaluationService evaluationService) {
        return switch (type.trim().toLowerCase(Locale.ROOT)) {
            case "normalized_match" -> normalizedMatch(expected, actual);
            case "keyword_coverage" -> keywordCoverage(evalCase.expectedKeywords(), actual, evalCase.forbiddenTerms());
            case "json_schema" -> jsonSchema(actual, evalCase.requiredJsonKeys());
            case "judge_qa" -> judgeQa(evalCase.input(), expected, actual, evaluationService);
            case "entity_f1" -> entityF1(expected, actual);
            case "ranking_order" -> rankingOrder(expected, actual);
            case "memory_preservation" -> memoryPreservation(evalCase, expected, actual);
            default -> Map.of("passed", false, "metric", 0.0, "reason", "unsupported_scoring_type:" + type);
        };
    }

    private static Map<String, Object> normalizedMatch(String expected, String actual) {
        boolean match = BenchmarkMvpMetricsCalculator.normalizedExactMatch(expected, actual);
        return Map.of("passed", match, "metric", match ? 1.0 : 0.0, "normalizedExactMatch", match);
    }

    private static Map<String, Object> keywordCoverage(String keywordsCsv, String actual, String forbiddenCsv) {
        List<String> keywords = splitCsv(keywordsCsv);
        List<String> forbidden = splitCsv(forbiddenCsv);
        String folded = normalizedFold(actual);
        int hits = 0;
        List<String> missing = new ArrayList<>();
        for (String kw : keywords) {
            if (folded.contains(normalizedFold(kw))) {
                hits++;
            } else {
                missing.add(kw);
            }
        }
        double coverage = keywords.isEmpty() ? 1.0 : (double) hits / keywords.size();
        List<String> violations = new ArrayList<>();
        for (String term : forbidden) {
            if (!term.isBlank() && folded.contains(normalizedFold(term))) {
                violations.add(term);
            }
        }
        boolean passed = coverage >= 0.75 && violations.isEmpty();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("passed", passed);
        out.put("metric", coverage);
        out.put("keywordCoverage", coverage);
        out.put("missingKeywords", missing);
        out.put("forbiddenViolations", violations);
        return out;
    }

    private static Map<String, Object> jsonSchema(String actual, String requiredKeysCsv) {
        List<String> required = splitCsv(requiredKeysCsv);
        try {
            JsonNode node = OBJECT_MAPPER.readTree(actual.trim().isEmpty() ? "{}" : actual);
            if (!node.isObject()) {
                return Map.of("passed", false, "metric", 0.0, "reason", "not_json_object");
            }
            List<String> missing = new ArrayList<>();
            for (String key : required) {
                if (!node.has(key) || node.get(key).isNull()) {
                    missing.add(key);
                }
            }
            boolean passed = missing.isEmpty();
            double metric = required.isEmpty() ? 1.0 : (double) (required.size() - missing.size()) / required.size();
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("passed", passed);
            out.put("metric", metric);
            out.put("missingJsonKeys", missing);
            out.put("validJson", true);
            return out;
        } catch (Exception ex) {
            return Map.of("passed", false, "metric", 0.0, "validJson", false, "reason", "json_parse_error");
        }
    }

    private static Map<String, Object> judgeQa(
            String question, String expected, String actual, EvaluationService evaluationService) {
        if (evaluationService == null) {
            return Map.of("passed", false, "metric", 0.0, "reason", "judge_unavailable");
        }
        try {
            String judge = evaluationService.judgeQaAnswer(question, expected, actual);
            boolean passed = judge != null && !judge.isBlank();
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("passed", passed);
            out.put("metric", passed ? 1.0 : 0.0);
            out.put("judgeExcerpt", judge != null && judge.length() > 200 ? judge.substring(0, 200) : judge);
            return out;
        } catch (EvaluationJudgeException ex) {
            return Map.of("passed", false, "metric", 0.0, "reason", ex.errorCode());
        }
    }

    private static Map<String, Object> entityF1(String expected, String actual) {
        ScoringValueExtractor.EntityMatchScore recall = ScoringValueExtractor.entityRecall(expected, actual);
        if (recall.unsafe()) {
            return Map.of("passed", false, "metric", 0.0, "reason", recall.reason(), "entityF1", 0.0);
        }
        double f1 = recall.recall();
        boolean passed = recall.matched();
        return Map.of("passed", passed, "metric", f1, "entityF1", f1, "reason", recall.reason());
    }

    private static Map<String, Object> rankingOrder(String expected, String actual) {
        List<String> expItems = orderedItems(expected);
        List<String> actItems = orderedItems(actual);
        if (expItems.isEmpty()) {
            return Map.of("passed", false, "metric", 0.0, "reason", "expected_order_empty");
        }
        int matches = 0;
        int limit = Math.min(expItems.size(), actItems.size());
        for (int i = 0; i < limit; i++) {
            if (normalizedFold(expItems.get(i)).equals(normalizedFold(actItems.get(i)))) {
                matches++;
            }
        }
        double metric = (double) matches / expItems.size();
        boolean passed = metric >= 0.6;
        return Map.of("passed", passed, "metric", metric, "orderMatches", matches, "expectedCount", expItems.size());
    }

    private static Map<String, Object> memoryPreservation(LlmRoleEvalCase evalCase, String expected, String actual) {
        Map<String, Object> keywords = keywordCoverage(evalCase.expectedKeywords(), actual, evalCase.forbiddenTerms());
        boolean lengthOk = !actual.isBlank() && actual.length() <= Math.max(expected.length() * 2, 64);
        boolean passed = Boolean.TRUE.equals(keywords.get("passed")) && lengthOk;
        Map<String, Object> out = new LinkedHashMap<>(keywords);
        out.put("passed", passed);
        out.put("lengthWithinBudget", lengthOk);
        out.put("metric", keywords.get("metric"));
        return out;
    }

    private static List<String> parseScoringTypes(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of("normalized_match");
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private static List<String> splitCsv(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    private static List<String> orderedItems(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String trimmed = text.trim();
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            try {
                JsonNode arr = OBJECT_MAPPER.readTree(trimmed);
                if (arr.isArray()) {
                    List<String> out = new ArrayList<>();
                    arr.forEach(n -> out.add(n.asText()));
                    return out;
                }
            } catch (Exception ignored) {
                // fall through
            }
        }
        return Arrays.stream(trimmed.split("[\\n;,]+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private static String normalizedFold(String text) {
        if (text == null) {
            return "";
        }
        String n = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }
}
