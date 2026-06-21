package com.uniovi.rag.application.service.evaluation.metrics.querytype;

import com.uniovi.rag.application.service.evaluation.metrics.BenchmarkMvpMetricsCalculator;
import com.uniovi.rag.application.service.evaluation.metrics.StructuredScoreStatus;
import com.uniovi.rag.domain.model.QueryType;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Normalized deterministic evaluators for structured workbook query types. */
public final class StructuredQueryTypeEvaluators {

    private static final Pattern INTEGER_PATTERN = Pattern.compile("-?\\d+");
    private static final Pattern ISO_DATE = Pattern.compile("\\b(\\d{4}-\\d{2}-\\d{2})\\b");
    private static final Pattern DMY_NUMERIC = Pattern.compile("\\b(\\d{1,2})[/-](\\d{1,2})[/-](\\d{4})\\b");
    private static final Pattern DURATION_MINUTES =
            Pattern.compile("(\\d+)\\s*(minutos?|mins?|minutes?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern DURATION_HOURS =
            Pattern.compile("(\\d+)\\s*(horas?|hrs?|hours?)", Pattern.CASE_INSENSITIVE);
    private static final Set<String> TRUE_TOKENS = Set.of("true", "yes", "si", "sí", "verdadero");
    private static final Set<String> FALSE_TOKENS = Set.of("false", "no", "falso");

    private StructuredQueryTypeEvaluators() {}

    public static StructuredEvaluationResult evaluateDetailed(
            QueryType queryType, String expectedAnswer, String actualAnswer, String answerMode) {
        if (queryType == null) {
            return StructuredEvaluationResult.notAvailable();
        }
        return switch (queryType) {
            case COUNT_DOCUMENTS, COUNT_AND_EXPLAIN -> countEvaluation(expectedAnswer, actualAnswer);
            case BOOLEAN_QUERY -> booleanEvaluation(expectedAnswer, actualAnswer);
            case GET_FIELD -> fieldEvaluation(expectedAnswer, actualAnswer);
            case FIND_PARAGRAPH -> paragraphEvaluation(expectedAnswer, actualAnswer);
            case GET_DURATION -> durationEvaluation(expectedAnswer, actualAnswer);
            case EXTRACT_ENTITIES -> entityEvaluation(expectedAnswer, actualAnswer);
            case FILTER_AND_LIST -> listEvaluation(expectedAnswer, actualAnswer);
            case COMPARE -> compareEvaluation(expectedAnswer, actualAnswer);
            case SUMMARIZE_TOPIC, SUMMARIZE_MEETING, DECISION_EXTRACTION ->
                    StructuredEvaluationResult.notAvailable();
        };
    }

    public static Optional<Double> evaluate(
            QueryType queryType, String expectedAnswer, String actualAnswer, String answerMode) {
        StructuredEvaluationResult result = evaluateDetailed(queryType, expectedAnswer, actualAnswer, answerMode);
        if (result.status() != StructuredScoreStatus.COMPUTED || result.score() == null) {
            return Optional.empty();
        }
        return Optional.of(result.score());
    }

    static StructuredEvaluationResult countEvaluation(String expected, String actual) {
        Integer exp = firstInteger(expected);
        Integer act = firstInteger(actual);
        if (exp == null || act == null) {
            return StructuredEvaluationResult.notAvailable();
        }
        boolean match = exp.equals(act);
        return computed(match ? 1.0 : 0.0, match, null, null, null, null, null, null, null, null, null, null);
    }

    static StructuredEvaluationResult booleanEvaluation(String expected, String actual) {
        Boolean exp = parseBooleanToken(expected);
        Boolean act = parseBooleanToken(actual);
        if (exp == null || act == null) {
            return StructuredEvaluationResult.notAvailable();
        }
        boolean match = exp.equals(act);
        return computed(match ? 1.0 : 0.0, null, match, null, null, null, null, null, null, null, null, null);
    }

    static StructuredEvaluationResult fieldEvaluation(String expected, String actual) {
        if (expected == null || expected.isBlank()) {
            return StructuredEvaluationResult.notAvailable();
        }
        Boolean dateMatch = dateMatch(expected, actual);
        if (dateMatch != null) {
            return computed(dateMatch ? 1.0 : 0.0, null, null, dateMatch, null, null, null, null, null, null, null, null);
        }
        double score = textScoreValue(expected, actual);
        return computed(score, null, null, null, null, score, null, null, null, null, null, null);
    }

    static StructuredEvaluationResult paragraphEvaluation(String expected, String actual) {
        if (expected == null || expected.isBlank()) {
            return StructuredEvaluationResult.notAvailable();
        }
        double score = textScoreValue(expected, actual);
        return computed(score, null, null, null, null, score, null, null, null, null, null, null);
    }

    static StructuredEvaluationResult durationEvaluation(String expected, String actual) {
        Integer expMinutes = durationMinutes(expected);
        Integer actMinutes = durationMinutes(actual);
        if (expMinutes == null || actMinutes == null) {
            return StructuredEvaluationResult.notAvailable();
        }
        boolean match = expMinutes.equals(actMinutes);
        return computed(match ? 1.0 : 0.0, null, null, null, match, null, null, null, null, null, null, null);
    }

    static StructuredEvaluationResult entityEvaluation(String expected, String actual) {
        SetScores scores = setScores(expected, actual);
        if (scores == null) {
            return StructuredEvaluationResult.notAvailable();
        }
        return computed(
                scores.f1(),
                null,
                null,
                null,
                null,
                null,
                scores.precision(),
                scores.recall(),
                scores.f1(),
                null,
                null,
                null);
    }

    static StructuredEvaluationResult listEvaluation(String expected, String actual) {
        SetScores scores = setScores(expected, actual);
        if (scores == null) {
            return StructuredEvaluationResult.notAvailable();
        }
        return computed(
                scores.f1(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                scores.precision(),
                scores.recall(),
                scores.f1());
    }

    static StructuredEvaluationResult compareEvaluation(String expected, String actual) {
        if (!hasStructuredCompareGold(expected)) {
            return StructuredEvaluationResult.notAvailable();
        }
        double score = textScoreValue(expected, actual);
        return computed(score, null, null, null, null, score, null, null, null, null, null, null);
    }

    static Optional<Double> countScore(String expected, String actual) {
        StructuredEvaluationResult r = countEvaluation(expected, actual);
        return r.status() == StructuredScoreStatus.COMPUTED && r.score() != null
                ? Optional.of(r.score())
                : Optional.empty();
    }

    static Optional<Double> booleanScore(String expected, String actual) {
        StructuredEvaluationResult r = booleanEvaluation(expected, actual);
        return r.status() == StructuredScoreStatus.COMPUTED && r.score() != null
                ? Optional.of(r.score())
                : Optional.empty();
    }

    static Optional<Double> textScore(String expected, String actual) {
        if (expected == null || expected.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(textScoreValue(expected, actual));
    }

    static Optional<Double> setF1(String expected, String actual) {
        SetScores scores = setScores(expected, actual);
        return scores != null ? Optional.of(scores.f1()) : Optional.empty();
    }

    static Optional<Double> compareScore(String expected, String actual) {
        StructuredEvaluationResult r = compareEvaluation(expected, actual);
        return r.status() == StructuredScoreStatus.COMPUTED && r.score() != null
                ? Optional.of(r.score())
                : Optional.empty();
    }

    private static StructuredEvaluationResult computed(
            double score,
            Boolean countMatch,
            Boolean booleanMatch,
            Boolean dateMatch,
            Boolean durationMatch,
            Double fieldMatchScore,
            Double entityPrecision,
            Double entityRecall,
            Double entityF1,
            Double listPrecision,
            Double listRecall,
            Double listF1) {
        return new StructuredEvaluationResult(
                StructuredScoreStatus.COMPUTED,
                score,
                countMatch,
                booleanMatch,
                dateMatch,
                durationMatch,
                fieldMatchScore,
                entityPrecision,
                entityRecall,
                entityF1,
                listPrecision,
                listRecall,
                listF1);
    }

    private static double textScoreValue(String expected, String actual) {
        if (BenchmarkMvpMetricsCalculator.normalizedExactMatch(expected, actual)) {
            return 1.0;
        }
        if (BenchmarkMvpMetricsCalculator.containsExpectedAnswer(expected, actual)) {
            return 1.0;
        }
        return 0.0;
    }

    private static boolean hasStructuredCompareGold(String expected) {
        if (expected == null || expected.isBlank()) {
            return false;
        }
        String n = normalize(expected);
        return n.contains(" vs ")
                || n.contains(" versus ")
                || n.contains(" compared to ")
                || n.split("\\s+").length >= 2;
    }

    private static SetScores setScores(String expected, String actual) {
        Set<String> exp = tokenSet(expected);
        Set<String> act = tokenSet(actual);
        if (exp.isEmpty()) {
            return null;
        }
        if (act.isEmpty()) {
            return new SetScores(0.0, 0.0, 0.0);
        }
        int hits = 0;
        for (String t : act) {
            if (exp.contains(t)) {
                hits++;
            }
        }
        double precision = (double) hits / act.size();
        double recall = (double) hits / exp.size();
        if (precision + recall <= 0) {
            return new SetScores(0.0, 0.0, 0.0);
        }
        double f1 = 2.0 * precision * recall / (precision + recall);
        return new SetScores(precision, recall, f1);
    }

    private static Boolean dateMatch(String expected, String actual) {
        String expDate = firstIsoDate(expected);
        String actDate = firstIsoDate(actual);
        if (expDate == null) {
            return null;
        }
        if (actDate == null) {
            return false;
        }
        return expDate.equals(actDate);
    }

    private static String firstIsoDate(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher iso = ISO_DATE.matcher(text);
        if (iso.find()) {
            return iso.group(1);
        }
        Matcher dmy = DMY_NUMERIC.matcher(text);
        if (dmy.find()) {
            int day = Integer.parseInt(dmy.group(1));
            int month = Integer.parseInt(dmy.group(2));
            int year = Integer.parseInt(dmy.group(3));
            return String.format(Locale.ROOT, "%04d-%02d-%02d", year, month, day);
        }
        return null;
    }

    private static Integer durationMinutes(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher hours = DURATION_HOURS.matcher(text);
        if (hours.find()) {
            return Integer.parseInt(hours.group(1)) * 60;
        }
        Matcher minutes = DURATION_MINUTES.matcher(text);
        if (minutes.find()) {
            return Integer.parseInt(minutes.group(1));
        }
        Integer asInt = firstInteger(text);
        return asInt;
    }

    private static Integer firstInteger(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher m = INTEGER_PATTERN.matcher(text);
        if (!m.find()) {
            return null;
        }
        try {
            return Integer.parseInt(m.group());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Boolean parseBooleanToken(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String n = normalize(text);
        if (TRUE_TOKENS.contains(n)) {
            return true;
        }
        if (FALSE_TOKENS.contains(n)) {
            return false;
        }
        String first = n.split("[\\s,.;:!?]+")[0];
        if (TRUE_TOKENS.contains(first)) {
            return true;
        }
        if (FALSE_TOKENS.contains(first)) {
            return false;
        }
        return null;
    }

    private static Set<String> tokenSet(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        String[] parts = text.split("[;,\\n|]");
        Set<String> out = new LinkedHashSet<>();
        for (String part : parts) {
            String n = normalize(part);
            if (!n.isEmpty()) {
                out.add(n);
            }
        }
        if (out.isEmpty()) {
            for (String w : normalize(text).split("\\s+")) {
                if (!w.isBlank()) {
                    out.add(w);
                }
            }
        }
        return out;
    }

    private static String normalize(String s) {
        if (s == null) {
            return "";
        }
        String n = Normalizer.normalize(s, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        return n.replaceAll("\\s+", " ").trim();
    }

    private record SetScores(double precision, double recall, double f1) {}
}
