package com.uniovi.rag.application.service.evaluation.metrics.matching;

import com.uniovi.rag.application.service.evaluation.metrics.AbstentionDetector;
import com.uniovi.rag.application.service.evaluation.metrics.Answerability;
import com.uniovi.rag.application.service.evaluation.metrics.AnswerabilityNegativeSignals;
import com.uniovi.rag.application.service.evaluation.metrics.BenchmarkMvpMetricsCalculator;
import com.uniovi.rag.application.service.evaluation.metrics.BenchmarkMvpSchema;
import com.uniovi.rag.domain.model.QueryType;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Conservative calibrated expected-answer matcher for evaluation and reporting. */
public final class ExpectedAnswerMatchCalibrator {

    public static final String VERSION = "1";
    private static final int NORMALIZED_CONTAINS_MIN_EXPECTED_LEN = 10;
    private static final double SEMANTIC_SUPPORT_THRESHOLD = 0.8;

    private ExpectedAnswerMatchCalibrator() {}

    public static ExpectedAnswerMatchResult calibrate(
            String expectedAnswer,
            String actualAnswer,
            Answerability answerability,
            QueryType queryType,
            Map<String, Object> metricsContext,
            boolean rawContained,
            AbstentionDetector.Result abstention,
            Object semanticScore) {
        String expected = expectedAnswer != null ? expectedAnswer.trim() : "";
        String actual = actualAnswer != null ? actualAnswer.trim() : "";

        if (expected.isEmpty() || actual.isEmpty()) {
            return ExpectedAnswerMatchResult.unsafeToJudge("missing_expected_or_actual");
        }

        if (rawContained) {
            return ExpectedAnswerMatchResult.match(
                    ExpectedAnswerMatchType.RAW_CONTAINS,
                    ExpectedAnswerMatchConfidence.HIGH,
                    "raw_substring_contains");
        }

        if (answerability == Answerability.ANSWERABLE && abstention.abstained()) {
            return ExpectedAnswerMatchResult.noMatch("answerable_abstention");
        }

        if (ExpectedAnswerNormalizer.normalizedContains(expected, actual)
                && expected.length() >= NORMALIZED_CONTAINS_MIN_EXPECTED_LEN) {
            return ExpectedAnswerMatchResult.match(
                    ExpectedAnswerMatchType.NORMALIZED_CONTAINS,
                    ExpectedAnswerMatchConfidence.HIGH,
                    "normalized_substring_contains");
        }

        Optional<ExpectedAnswerMatchResult> toolMatch =
                StructuredToolAnswerMatcher.evaluate(expected, actual, queryType, metricsContext);
        if (toolMatch.isPresent()) {
            return toolMatch.get();
        }

        if (!shouldSkipTypedMatch(answerability, expected)) {
            Optional<ExpectedAnswerMatchResult> typedMatch = typedValueMatch(expected, actual, queryType);
            if (typedMatch.isPresent()) {
                return typedMatch.get();
            }
        }

        ExpectedAnswerMatchResult negative =
                evaluateNegativeEquivalence(
                        expected,
                        actual,
                        answerability,
                        abstention,
                        str(metricsContext != null ? metricsContext.get("finalAnswerSource") : null));
        if (negative != null) {
            return negative;
        }

        if (hasSemanticSupport(semanticScore)) {
            return ExpectedAnswerMatchResult.supportOnly("semantic_judge_high_no_deterministic_match");
        }

        return ExpectedAnswerMatchResult.noMatch("no_calibrated_match");
    }

    private static boolean shouldSkipTypedMatch(Answerability answerability, String expectedAnswer) {
        return answerability == Answerability.UNANSWERABLE
                && AnswerabilityNegativeSignals.indicatesAbsenceExpected(expectedAnswer);
    }

    private static Optional<ExpectedAnswerMatchResult> typedValueMatch(
            String expected, String actual, QueryType queryType) {
        if (queryType == null) {
            return Optional.empty();
        }
        return switch (queryType) {
            case COUNT_DOCUMENTS, COUNT_AND_EXPLAIN -> numericValueMatch(expected, actual);
            case GET_FIELD -> dateValueMatch(expected, actual);
            case GET_DURATION -> durationValueMatch(expected, actual);
            case EXTRACT_ENTITIES, FILTER_AND_LIST -> entityValueMatch(expected, actual);
            default -> Optional.empty();
        };
    }

    private static Optional<ExpectedAnswerMatchResult> numericValueMatch(String expected, String actual) {
        Optional<Integer> exp = ScoringValueExtractor.extractPrimaryCount(expected);
        Optional<Integer> act = ScoringValueExtractor.extractPrimaryCount(actual);
        if (exp.isEmpty() || act.isEmpty()) {
            return Optional.of(ExpectedAnswerMatchResult.unsafeToJudge("numeric_extraction_failed"));
        }
        if (exp.get().equals(act.get())) {
            return Optional.of(
                    ExpectedAnswerMatchResult.match(
                            ExpectedAnswerMatchType.NUMERIC_VALUE_MATCH,
                            ExpectedAnswerMatchConfidence.HIGH,
                            "numeric_value_equal"));
        }
        return Optional.of(ExpectedAnswerMatchResult.noMatch("numeric_value_mismatch"));
    }

    private static Optional<ExpectedAnswerMatchResult> dateValueMatch(String expected, String actual) {
        Set<java.time.LocalDate> expDates = ScoringValueExtractor.extractDates(expected);
        Set<java.time.LocalDate> actDates = ScoringValueExtractor.extractDates(actual);
        if (expDates.isEmpty()) {
            return Optional.of(ExpectedAnswerMatchResult.unsafeToJudge("date_extraction_failed"));
        }
        if (actDates.isEmpty()) {
            return Optional.of(ExpectedAnswerMatchResult.noMatch("date_missing_in_actual"));
        }
        boolean overlap = expDates.stream().anyMatch(actDates::contains);
        if (overlap) {
            return Optional.of(
                    ExpectedAnswerMatchResult.match(
                            ExpectedAnswerMatchType.DATE_VALUE_MATCH,
                            ExpectedAnswerMatchConfidence.HIGH,
                            "date_value_overlap"));
        }
        return Optional.of(ExpectedAnswerMatchResult.noMatch("date_value_mismatch"));
    }

    private static Optional<ExpectedAnswerMatchResult> durationValueMatch(String expected, String actual) {
        Optional<Integer> exp = ScoringValueExtractor.extractDurationMinutes(expected);
        Optional<Integer> act = ScoringValueExtractor.extractDurationMinutes(actual);
        if (exp.isEmpty() || act.isEmpty()) {
            return Optional.of(ExpectedAnswerMatchResult.unsafeToJudge("duration_extraction_failed"));
        }
        if (exp.get().equals(act.get())) {
            return Optional.of(
                    ExpectedAnswerMatchResult.match(
                            ExpectedAnswerMatchType.DATE_VALUE_MATCH,
                            ExpectedAnswerMatchConfidence.HIGH,
                            "duration_value_equal"));
        }
        return Optional.of(ExpectedAnswerMatchResult.noMatch("duration_value_mismatch"));
    }

    private static Optional<ExpectedAnswerMatchResult> entityValueMatch(String expected, String actual) {
        ScoringValueExtractor.EntityMatchScore score = ScoringValueExtractor.entityRecall(expected, actual);
        if (score.unsafe()) {
            return Optional.of(ExpectedAnswerMatchResult.unsafeToJudge(score.reason()));
        }
        if (score.matched()) {
            return Optional.of(
                    ExpectedAnswerMatchResult.match(
                            ExpectedAnswerMatchType.ENTITY_SET_MATCH,
                            ExpectedAnswerMatchConfidence.MEDIUM,
                            score.reason()));
        }
        return Optional.of(ExpectedAnswerMatchResult.noMatch(score.reason()));
    }

    private static ExpectedAnswerMatchResult evaluateNegativeEquivalence(
            String expected,
            String actual,
            Answerability answerability,
            AbstentionDetector.Result abstention,
            String finalAnswerSource) {
        if (answerability != Answerability.UNANSWERABLE) {
            return null;
        }
        SpanishNegativeEquivalenceDetector.NegativeEquivalenceResult result =
                SpanishNegativeEquivalenceDetector.evaluate(
                        expected, actual, answerability, abstention.abstained(), abstention.source(), finalAnswerSource);
        if (!result.applicable()) {
            return null;
        }
        if (result.unsafe()) {
            return ExpectedAnswerMatchResult.unsafeToJudge(result.reason());
        }
        if (result.matched() && result.type() != null) {
            return ExpectedAnswerMatchResult.match(
                    result.type(),
                    result.confidence() != null ? result.confidence() : ExpectedAnswerMatchConfidence.MEDIUM,
                    result.reason());
        }
        if (result.type() == ExpectedAnswerMatchType.NO_MATCH) {
            return ExpectedAnswerMatchResult.noMatch(result.reason());
        }
        return null;
    }

    private static boolean hasSemanticSupport(Object semanticScore) {
        if (semanticScore instanceof Number n) {
            return n.doubleValue() >= SEMANTIC_SUPPORT_THRESHOLD;
        }
        if (BenchmarkMvpSchema.NOT_AVAILABLE.equals(semanticScore)) {
            return false;
        }
        return false;
    }

    /** Convenience overload preserving raw containment computation unchanged. */
    public static ExpectedAnswerMatchResult calibrate(
            String expectedAnswer,
            String actualAnswer,
            Answerability answerability,
            QueryType queryType,
            Map<String, Object> metricsContext) {
        boolean rawContained = BenchmarkMvpMetricsCalculator.containsExpectedAnswer(expectedAnswer, actualAnswer);
        AbstentionDetector.Result abstention =
                AbstentionDetector.detect(metricsContext != null ? metricsContext : Map.of(), actualAnswer);
        Object semantic = semanticFromContext(metricsContext);
        return calibrate(
                expectedAnswer,
                actualAnswer,
                answerability,
                queryType,
                metricsContext,
                rawContained,
                abstention,
                semantic);
    }

    private static Object semanticFromContext(Map<String, Object> metricsContext) {
        if (metricsContext == null) {
            return BenchmarkMvpSchema.NOT_AVAILABLE;
        }
        Object js = metricsContext.get("judge_scores");
        if (!(js instanceof Map<?, ?> m)) {
            return BenchmarkMvpSchema.NOT_AVAILABLE;
        }
        Object c = m.get("correctness");
        if (!(c instanceof Number n)) {
            return BenchmarkMvpSchema.NOT_AVAILABLE;
        }
        return n.doubleValue() / 5.0;
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }
}
