package com.uniovi.rag.application.service.evaluation.metrics;

import com.uniovi.rag.application.service.evaluation.metrics.matching.ExpectedAnswerNormalizer;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Public signals for negative-answer detection used by calibrated matching. */
public final class AnswerabilityNegativeSignals {

    private static final Pattern AFFIRMATIVE_UNSUPPORTED =
            Pattern.compile(
                    "\\bs[ií]\\s+se\\s+decidi[oó]\\b|\\ben\\s+el\\s+acta\\s+correspondiente\\b|\\basistieron\\s+\\d+\\b|\\b\\d+\\s+(reuniones|actas|documentos)\\b",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.DOTALL);
    private static final Pattern EXTENDED_NEGATIVE_PARAPHRASE =
            Pattern.compile(
                    "\\bno\\s+se\\s+coment(?:o|aron)\\b|\\bno\\s+se\\s+indic(?:o|aron)\\b|\\bno\\s+se\\s+registr(?:o|aron)\\b|\\bno\\s+se\\s+mencion(?:a|an)\\b",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern NEGATED_COMMUNICATION_VERB =
            Pattern.compile(
                    "\\bno\\s+se\\s+(?:mencion(?:a|an)|coment(?:o|aron)|detall(?:a|aron)|indic(?:o|aron)|registr(?:o|aron)|aparece|consta)\\b"
                            + "|\\bno\\s+consta\\b|\\bno\\s+aparece\\b|\\bno\\s+hay\\s+constancia\\b",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern VAGUE_ABSENCE_WITHOUT_TOPIC =
            Pattern.compile(
                    "\\bno\\s+hay\\s+informacion\\s+suficiente\\b|\\bno\\s+se\\s+sabe\\b",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Set<String> GENERIC_ABSENCE_TOKENS =
            Set.of(
                    "datos",
                    "dato",
                    "informacion",
                    "registro",
                    "mencion",
                    "evidencia",
                    "consta",
                    "disponible",
                    "actas",
                    "acta",
                    "fuentes",
                    "fuente",
                    "ninguna",
                    "ninguno",
                    "ningun",
                    "alguna",
                    "alguno",
                    "reunion",
                    "reuniones",
                    "celebrada",
                    "celebradas",
                    "menciona",
                    "menciono",
                    "mencionan",
                    "durante",
                    "detalles",
                    "detalle",
                    "respecto",
                    "sobre",
                    "ninguna de las",
                    "encuentra",
                    "comentaron",
                    "comento",
                    "comentada",
                    "comentadas");

    private AnswerabilityNegativeSignals() {}

    public static boolean hasHighPrecisionNegativePhrasing(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String normalized = ExpectedAnswerNormalizer.normalizedFold(text);
        return AnswerabilityLabelRules.hasHighPrecisionNegativePhrasing(text)
                || EXTENDED_NEGATIVE_PARAPHRASE.matcher(normalized).find();
    }

    public static boolean indicatesAbsenceExpected(String expectedAnswer) {
        if (expectedAnswer == null || expectedAnswer.isBlank()) {
            return false;
        }
        return hasHighPrecisionNegativePhrasing(expectedAnswer);
    }

    public static boolean deniesEvidenceOrAbstains(String actualAnswer, boolean abstained) {
        if (abstained) {
            return true;
        }
        return hasHighPrecisionNegativePhrasing(actualAnswer);
    }

    public static boolean hasTopicAnchoredNegativeParaphrase(String expectedAnswer, String actualAnswer) {
        if (expectedAnswer == null
                || expectedAnswer.isBlank()
                || actualAnswer == null
                || actualAnswer.isBlank()) {
            return false;
        }
        if (!indicatesAbsenceExpected(expectedAnswer) || hasAffirmativeUnsupportedClaim(actualAnswer)) {
            return false;
        }
        String actual = ExpectedAnswerNormalizer.normalizedFold(actualAnswer);
        if (!NEGATED_COMMUNICATION_VERB.matcher(actual).find()) {
            return false;
        }
        if (VAGUE_ABSENCE_WITHOUT_TOPIC.matcher(actual).find() && !sharesSubstantiveTopic(expectedAnswer, actualAnswer)) {
            return false;
        }
        return sharesSubstantiveTopic(expectedAnswer, actualAnswer);
    }

    public static boolean sharesSubstantiveTopicWithExpected(String expectedAnswer, String actualAnswer) {
        return sharesSubstantiveTopic(expectedAnswer, actualAnswer);
    }

    public static boolean hasAffirmativeUnsupportedClaim(String actualAnswer) {
        if (actualAnswer == null || actualAnswer.isBlank()) {
            return false;
        }
        String normalized = ExpectedAnswerNormalizer.normalizedFold(actualAnswer);
        return AFFIRMATIVE_UNSUPPORTED.matcher(normalized).find();
    }

    private static boolean sharesSubstantiveTopic(String expectedAnswer, String actualAnswer) {
        var expectedTokens =
                ExpectedAnswerNormalizer.normalizedTokens(
                        expectedAnswer, ExpectedAnswerNormalizer.TokenMode.ENTITY);
        var actualTokens =
                ExpectedAnswerNormalizer.normalizedTokens(
                        actualAnswer, ExpectedAnswerNormalizer.TokenMode.ENTITY);
        for (String expectedToken : expectedTokens) {
            if (expectedToken.length() <= 3 || GENERIC_ABSENCE_TOKENS.contains(expectedToken)) {
                continue;
            }
            if (actualTokens.contains(expectedToken)) {
                return true;
            }
            String stem = expectedToken.length() > 4 ? expectedToken.substring(0, expectedToken.length() - 1) : expectedToken;
            for (String actualToken : actualTokens) {
                if (actualToken.startsWith(stem) || stem.startsWith(actualToken)) {
                    return true;
                }
            }
            if (ExpectedAnswerNormalizer.normalizedFold(actualAnswer).contains(expectedToken)) {
                return true;
            }
        }
        return false;
    }
}
