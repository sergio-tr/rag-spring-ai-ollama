package com.uniovi.rag.application.service.evaluation.metrics;

import com.uniovi.rag.domain.model.QueryType;
import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** High-precision expected-answer patterns for controlled answerability inference. */
final class AnswerabilityLabelRules {

    static final String RULES_VERSION = "1";

    static final String DATASET_AMBIGUOUS = "DATASET_AMBIGUOUS";
    static final String DATASET_UNANSWERABLE = "DATASET_UNANSWERABLE";
    static final String DATASET_ANSWERABLE = "DATASET_ANSWERABLE";

    static final String REVIEW_MIXED_CLAUSE = "REVIEW_MIXED_CLAUSE";
    static final String REVIEW_PARTIAL_COMPARE = "REVIEW_PARTIAL_COMPARE";
    static final String REVIEW_EMPTY_EXPECTED = "REVIEW_EMPTY_EXPECTED";

    static final String NEG_NO_HAY = "NEG_NO_HAY";
    static final String NEG_NO_EXISTE = "NEG_NO_EXISTE";
    static final String NEG_NINGUNA = "NEG_NINGUNA";
    static final String NEG_NO_SE_ENCUENTRA = "NEG_NO_SE_ENCUENTRA";
    static final String NEG_NO_SE_MENCIONA = "NEG_NO_SE_MENCIONA";
    static final String NEG_NO_CONSTA = "NEG_NO_CONSTA";
    static final String NEG_EXPLICIT_NO = "NEG_EXPLICIT_NO";

    static final String POS_FACTUAL = "POS_FACTUAL";
    static final String POS_DATE = "POS_DATE";
    static final String POS_DESCRIPTIVE = "POS_DESCRIPTIVE";

    private static final Pattern MIXED_POSITIVE_CLAUSE =
            Pattern.compile("\\bs[ií]\\s+se\\s+decidi[oó]\\b|\\ben\\s+el\\s+acta\\s+correspondiente\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern AFFIRMATIVE_ACTA_CLAUSE =
            Pattern.compile("\\ben\\s+el\\s+acta\\b.*\\bs[ií]\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.DOTALL);

    private static final Pattern P_NO_HAY = Pattern.compile("\\bno\\s+hay\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern P_NO_EXISTE = Pattern.compile("\\bno\\s+existen?\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern P_NINGUNA = Pattern.compile("\\bninguna?\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern P_NO_SE_ENCUENTRA = Pattern.compile("\\bno\\s+se\\s+encuentra\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern P_NO_SE_MENCIONA =
            Pattern.compile("\\bno\\s+se\\s+mencion(?:a|an)\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern P_NO_SE_COMENTO =
            Pattern.compile("\\bno\\s+se\\s+coment(?:o|aron)\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern P_NO_SE_DETALLA =
            Pattern.compile("\\bno\\s+se\\s+detall(?:a|aron)\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern P_NO_APARECE = Pattern.compile("\\bno\\s+aparece\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern P_NO_HAY_CONSTANCIA =
            Pattern.compile("\\bno\\s+hay\\s+constancia\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern P_NO_CONSTA = Pattern.compile("\\bno\\s+consta\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern P_EXPLICIT_NO = Pattern.compile("^\\s*no\\s*,", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern P_POS_FACTUAL =
            Pattern.compile(
                    "\\b\\d+\\b|\\bse\\s+trat[oó]\\b|\\basistieron\\b|\\basistentes\\b|\\bdecidi[oó]\\b|\\baprob[oó]\\b|\\btotal\\b|\\bcompar",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern P_POS_DATE =
            Pattern.compile(
                    "\\b\\d{1,2}\\s+de\\s+(enero|febrero|marzo|abril|mayo|junio|julio|agosto|septiembre|octubre|noviembre|diciembre)\\b|\\b\\d{4}\\b",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern P_PARTIAL_COMPARE =
            Pattern.compile("no\\s+se\\s+encuentran\\s+actas", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private AnswerabilityLabelRules() {}

    static AnswerabilityLabelResult inferFromExpectedAnswer(String expectedAnswer, QueryType queryType) {
        String raw = expectedAnswer != null ? expectedAnswer.trim() : "";
        if (raw.isEmpty()) {
            return review(REVIEW_EMPTY_EXPECTED, "empty_expected_answer");
        }

        String normalized = normalize(raw);
        if (MIXED_POSITIVE_CLAUSE.matcher(normalized).find()) {
            return review(REVIEW_MIXED_CLAUSE, "mixed_positive_clause");
        }
        if (queryType == QueryType.COMPARE && P_PARTIAL_COMPARE.matcher(normalized).find()) {
            return review(REVIEW_PARTIAL_COMPARE, "partial_compare_answer");
        }
        if (P_NO_CONSTA.matcher(normalized).find() && hasTrailingAffirmativeClause(raw)) {
            return review(REVIEW_MIXED_CLAUSE, "no_consta_with_affirmative_clause");
        }

        AnswerabilityLabelResult negative = matchNegative(normalized, raw);
        if (negative != null) {
            return negative;
        }

        AnswerabilityLabelResult positive = matchPositive(normalized, raw);
        if (positive != null) {
            return positive;
        }

        return new AnswerabilityLabelResult(
                Answerability.UNKNOWN,
                AnswerabilitySource.DEFAULT_UNKNOWN,
                "",
                null,
                "no_matching_rule");
    }

    static boolean hasHighPrecisionNegativePhrasing(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String normalized = normalize(text);
        return P_NO_HAY.matcher(normalized).find()
                || P_NO_EXISTE.matcher(normalized).find()
                || P_NINGUNA.matcher(normalized).find()
                || P_NO_SE_ENCUENTRA.matcher(normalized).find()
                || P_NO_SE_MENCIONA.matcher(normalized).find()
                || P_NO_SE_COMENTO.matcher(normalized).find()
                || P_NO_SE_DETALLA.matcher(normalized).find()
                || P_NO_APARECE.matcher(normalized).find()
                || P_NO_HAY_CONSTANCIA.matcher(normalized).find()
                || P_NO_CONSTA.matcher(normalized).find()
                || P_EXPLICIT_NO.matcher(normalized).find();
    }

    private static AnswerabilityLabelResult matchNegative(String normalized, String raw) {
        if (P_EXPLICIT_NO.matcher(normalized).find()) {
            return unanswerable(NEG_EXPLICIT_NO, AnswerabilityLabelConfidence.HIGH, "explicit_no_prefix");
        }
        if (P_NO_HAY.matcher(normalized).find()) {
            return unanswerable(NEG_NO_HAY, AnswerabilityLabelConfidence.HIGH, "no_hay");
        }
        if (P_NO_EXISTE.matcher(normalized).find()) {
            return unanswerable(NEG_NO_EXISTE, AnswerabilityLabelConfidence.HIGH, "no_existe");
        }
        if (P_NINGUNA.matcher(normalized).find()) {
            return unanswerable(NEG_NINGUNA, AnswerabilityLabelConfidence.HIGH, "ninguna");
        }
        if (P_NO_SE_ENCUENTRA.matcher(normalized).find()) {
            return unanswerable(NEG_NO_SE_ENCUENTRA, AnswerabilityLabelConfidence.HIGH, "no_se_encuentra");
        }
        if (P_NO_SE_MENCIONA.matcher(normalized).find()) {
            if (AFFIRMATIVE_ACTA_CLAUSE.matcher(normalize(raw)).find()) {
                return review(REVIEW_MIXED_CLAUSE, "no_se_menciona_with_acta_clause");
            }
            return unanswerable(NEG_NO_SE_MENCIONA, AnswerabilityLabelConfidence.HIGH, "no_se_menciona");
        }
        if (P_NO_CONSTA.matcher(normalized).find()) {
            return unanswerable(NEG_NO_CONSTA, AnswerabilityLabelConfidence.MEDIUM, "no_consta");
        }
        return null;
    }

    private static AnswerabilityLabelResult matchPositive(String normalized, String raw) {
        if (P_POS_DATE.matcher(normalized).find()) {
            return answerable(POS_DATE, AnswerabilityLabelConfidence.HIGH, "date_phrase");
        }
        if (P_POS_FACTUAL.matcher(normalized).find()) {
            return answerable(POS_FACTUAL, AnswerabilityLabelConfidence.HIGH, "factual_signal");
        }
        if (raw.length() > 50 && !startsWithNegation(normalized)) {
            return answerable(POS_DESCRIPTIVE, AnswerabilityLabelConfidence.MEDIUM, "descriptive_answer");
        }
        return null;
    }

    private static boolean startsWithNegation(String normalized) {
        return normalized.startsWith("no ") || normalized.startsWith("no,") || normalized.startsWith("ningun");
    }

    private static boolean hasTrailingAffirmativeClause(String raw) {
        int semi = raw.indexOf(';');
        if (semi < 0) {
            return false;
        }
        String tail = raw.substring(semi + 1).trim();
        return !tail.isEmpty() && (tail.toLowerCase(Locale.ROOT).startsWith("sí") || tail.toLowerCase(Locale.ROOT).startsWith("si "));
    }

    private static AnswerabilityLabelResult review(String ruleId, String reason) {
        return new AnswerabilityLabelResult(
                Answerability.NEEDS_REVIEW,
                AnswerabilitySource.REVIEW_REQUIRED,
                ruleId,
                AnswerabilityLabelConfidence.LOW,
                reason);
    }

    private static AnswerabilityLabelResult unanswerable(
            String ruleId, AnswerabilityLabelConfidence confidence, String reason) {
        return new AnswerabilityLabelResult(
                Answerability.UNANSWERABLE,
                AnswerabilitySource.INFERRED_FROM_EXPECTED_ANSWER,
                ruleId,
                confidence,
                reason);
    }

    private static AnswerabilityLabelResult answerable(
            String ruleId, AnswerabilityLabelConfidence confidence, String reason) {
        return new AnswerabilityLabelResult(
                Answerability.ANSWERABLE,
                AnswerabilitySource.INFERRED_FROM_EXPECTED_ANSWER,
                ruleId,
                confidence,
                reason);
    }

    private static String normalize(String text) {
        String nfd = Normalizer.normalize(text, Normalizer.Form.NFD);
        String stripped = nfd.replaceAll("\\p{M}+", "");
        return stripped.toLowerCase(Locale.ROOT).replace('\u00A0', ' ').trim();
    }
}
