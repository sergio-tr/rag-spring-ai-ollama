package com.uniovi.rag.application.service.evaluation.metrics.matching;

import com.uniovi.rag.application.service.evaluation.metrics.RagPresetToolMetrics;
import com.uniovi.rag.application.service.evaluation.metrics.StructuredScoreStatus;
import com.uniovi.rag.domain.model.QueryType;
import java.util.Map;
import java.util.Optional;

/** Matches tool/function final answers using structured metrics when available. */
public final class StructuredToolAnswerMatcher {

    private StructuredToolAnswerMatcher() {}

    public static Optional<ExpectedAnswerMatchResult> evaluate(
            String expectedAnswer,
            String actualAnswer,
            QueryType queryType,
            Map<String, Object> metricsContext) {
        if (metricsContext == null || !isToolOrFunctionFinal(metricsContext)) {
            return Optional.empty();
        }

        Optional<ExpectedAnswerMatchResult> structured = Optional.empty();
        if (StructuredScoreStatus.COMPUTED.name().equals(String.valueOf(metricsContext.get("structuredScoreStatus")))) {
            ExpectedAnswerMatchResult fromStructured = fromStructuredFields(metricsContext, queryType);
            if (fromStructured != null) {
                if (fromStructured.matchedCalibrated()) {
                    return Optional.of(fromStructured);
                }
                structured = Optional.of(fromStructured);
            }
        }

        Optional<ExpectedAnswerMatchResult> extracted = evaluateFromExtractors(expectedAnswer, actualAnswer, queryType);
        if (extracted.isPresent() && extracted.get().matchedCalibrated()) {
            return extracted;
        }
        if (structured.isPresent()) {
            return structured;
        }
        return extracted;
    }

    private static boolean isToolOrFunctionFinal(Map<String, Object> metricsContext) {
        return bool(metricsContext.get(RagPresetToolMetrics.KEY_TOOL_RESULT_USED_AS_FINAL))
                || bool(metricsContext.get(RagPresetToolMetrics.KEY_FUNCTION_RESULT_USED_AS_FINAL));
    }

    private static ExpectedAnswerMatchResult fromStructuredFields(Map<String, Object> mp, QueryType queryType) {
        if (queryType == null) {
            return null;
        }
        return switch (queryType) {
            case COUNT_DOCUMENTS, COUNT_AND_EXPLAIN -> fromBooleanField(mp.get("countMatch"), "structured_count_match");
            case BOOLEAN_QUERY -> fromBooleanField(mp.get("booleanMatch"), "structured_boolean_match");
            case GET_FIELD -> fromBooleanField(mp.get("dateMatch"), "structured_date_match");
            case GET_DURATION -> fromBooleanField(mp.get("durationMatch"), "structured_duration_match");
            case EXTRACT_ENTITIES -> fromEntityFields(mp);
            case FILTER_AND_LIST -> fromListFields(mp);
            default -> null;
        };
    }

    private static ExpectedAnswerMatchResult fromBooleanField(Object raw, String reasonPrefix) {
        if (!(raw instanceof Boolean match)) {
            return null;
        }
        if (match) {
            return ExpectedAnswerMatchResult.match(
                    ExpectedAnswerMatchType.STRUCTURED_TOOL_MATCH,
                    ExpectedAnswerMatchConfidence.HIGH,
                    reasonPrefix + "_true");
        }
        return ExpectedAnswerMatchResult.noMatch(reasonPrefix + "_false");
    }

    private static ExpectedAnswerMatchResult fromEntityFields(Map<String, Object> mp) {
        Object f1 = mp.get("entityF1");
        if (f1 instanceof Number n && n.doubleValue() >= 0.6) {
            return ExpectedAnswerMatchResult.match(
                    ExpectedAnswerMatchType.STRUCTURED_TOOL_MATCH,
                    ExpectedAnswerMatchConfidence.HIGH,
                    "structured_entity_f1");
        }
        if (f1 instanceof Number) {
            return ExpectedAnswerMatchResult.noMatch("structured_entity_f1_low");
        }
        return null;
    }

    private static ExpectedAnswerMatchResult fromListFields(Map<String, Object> mp) {
        Object f1 = mp.get("listF1");
        if (f1 instanceof Number n && n.doubleValue() >= 0.6) {
            return ExpectedAnswerMatchResult.match(
                    ExpectedAnswerMatchType.STRUCTURED_TOOL_MATCH,
                    ExpectedAnswerMatchConfidence.HIGH,
                    "structured_list_f1");
        }
        if (f1 instanceof Number) {
            return ExpectedAnswerMatchResult.noMatch("structured_list_f1_low");
        }
        return null;
    }

    private static Optional<ExpectedAnswerMatchResult> evaluateFromExtractors(
            String expectedAnswer, String actualAnswer, QueryType queryType) {
        if (queryType == null) {
            return Optional.empty();
        }
        return switch (queryType) {
            case COUNT_DOCUMENTS, COUNT_AND_EXPLAIN -> numericMatch(expectedAnswer, actualAnswer);
            case GET_FIELD -> dateMatch(expectedAnswer, actualAnswer);
            case GET_DURATION -> durationMatch(expectedAnswer, actualAnswer);
            case EXTRACT_ENTITIES, FILTER_AND_LIST -> entityMatch(expectedAnswer, actualAnswer);
            default -> Optional.empty();
        };
    }

    private static Optional<ExpectedAnswerMatchResult> numericMatch(String expected, String actual) {
        Optional<Integer> exp = ScoringValueExtractor.extractPrimaryCount(expected);
        Optional<Integer> act = ScoringValueExtractor.extractPrimaryCount(actual);
        if (exp.isEmpty() || act.isEmpty()) {
            return Optional.empty();
        }
        if (exp.get().equals(act.get())) {
            return Optional.of(
                    ExpectedAnswerMatchResult.match(
                            ExpectedAnswerMatchType.STRUCTURED_TOOL_MATCH,
                            ExpectedAnswerMatchConfidence.HIGH,
                            "tool_count_value_match"));
        }
        return Optional.of(ExpectedAnswerMatchResult.noMatch("tool_count_value_mismatch"));
    }

    private static Optional<ExpectedAnswerMatchResult> dateMatch(String expected, String actual) {
        var expDates = ScoringValueExtractor.extractDates(expected);
        var actDates = ScoringValueExtractor.extractDates(actual);
        if (expDates.isEmpty()) {
            return Optional.empty();
        }
        if (actDates.isEmpty()) {
            return Optional.of(ExpectedAnswerMatchResult.noMatch("tool_date_missing"));
        }
        boolean overlap = expDates.stream().anyMatch(actDates::contains);
        if (overlap) {
            return Optional.of(
                    ExpectedAnswerMatchResult.match(
                            ExpectedAnswerMatchType.STRUCTURED_TOOL_MATCH,
                            ExpectedAnswerMatchConfidence.HIGH,
                            "tool_date_overlap"));
        }
        return Optional.of(ExpectedAnswerMatchResult.noMatch("tool_date_mismatch"));
    }

    private static Optional<ExpectedAnswerMatchResult> durationMatch(String expected, String actual) {
        Optional<Integer> exp = ScoringValueExtractor.extractDurationMinutes(expected);
        Optional<Integer> act = ScoringValueExtractor.extractDurationMinutes(actual);
        if (exp.isEmpty() || act.isEmpty()) {
            return Optional.empty();
        }
        if (exp.get().equals(act.get())) {
            return Optional.of(
                    ExpectedAnswerMatchResult.match(
                            ExpectedAnswerMatchType.STRUCTURED_TOOL_MATCH,
                            ExpectedAnswerMatchConfidence.HIGH,
                            "tool_duration_match"));
        }
        return Optional.of(ExpectedAnswerMatchResult.noMatch("tool_duration_mismatch"));
    }

    private static Optional<ExpectedAnswerMatchResult> entityMatch(String expected, String actual) {
        ScoringValueExtractor.EntityMatchScore score = ScoringValueExtractor.entityRecall(expected, actual);
        if (score.unsafe()) {
            return Optional.empty();
        }
        if (score.matched()) {
            return Optional.of(
                    ExpectedAnswerMatchResult.match(
                            ExpectedAnswerMatchType.STRUCTURED_TOOL_MATCH,
                            ExpectedAnswerMatchConfidence.MEDIUM,
                            score.reason()));
        }
        return Optional.of(ExpectedAnswerMatchResult.noMatch(score.reason()));
    }

    private static boolean bool(Object raw) {
        if (raw instanceof Boolean b) {
            return b;
        }
        if (raw instanceof String s) {
            return Boolean.parseBoolean(s);
        }
        return false;
    }
}
