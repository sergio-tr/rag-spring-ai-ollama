package com.uniovi.rag.application.service.runtime.tool;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Detects deterministic tool answers that deny data or report zero/no matches (ES + EN). */
public final class DeterministicToolNegativeAnswerDetector {

    private static final int UNICODE_CANON = Pattern.UNICODE_CASE | Pattern.CANON_EQ;

    private static final Pattern SHORT_NEGATIVE = Pattern.compile("(?i)(?:no|0|none|n/a)", UNICODE_CANON);

    private static final Pattern EXPLICIT_ZERO_COUNT =
            Pattern.compile(
                    "(?:^|\\b)(?:0|zero)\\s+(?:actas?|reuniones?|documentos?|coincidencias?|matches?)(?:\\b|$)",
                    Pattern.CASE_INSENSITIVE | UNICODE_CANON);

    private static final List<String> NEGATIVE_ENGLISH_PHRASES =
            List.of(
                    "not found",
                    "no matching",
                    "could not find",
                    "does not exist",
                    "no documents",
                    "no meetings",
                    "no records",
                    "no information available",
                    "no information found",
                    "topic not found",
                    "topic not present",
                    "topic not mentioned");

    private static final List<Pattern> NEGATIVE_SPANISH_PATTERNS =
            List.of(
                    Pattern.compile("(?i)no\\s+(?:se\\s+)?encontr", UNICODE_CANON),
                    Pattern.compile(
                            "(?i)no\\s+(?:se\\s+)?(?:encuentra|encuentran|hay|existen|existe|consta|figura|aparece)",
                            UNICODE_CANON),
                    Pattern.compile("(?i)no\\s+(?:se\\s+)?registr", UNICODE_CANON),
                    Pattern.compile("(?i)no\\s+(?:se\\s+)?localiz", UNICODE_CANON),
                    Pattern.compile(
                            "(?i)ningun[ao]\\s+(?:acta|reuni[oó]n|documento|registro|asistente|coincidencia|resultado|menci[oó]n)",
                            UNICODE_CANON),
                    Pattern.compile("(?i)no\\s+hay\\s+ningun[ao]", UNICODE_CANON),
                    Pattern.compile(
                            "(?i)sin\\s+(?:actas|reuniones|documentos|resultados|coincidencias|informaci[oó]n)",
                            UNICODE_CANON),
                    Pattern.compile("(?i)cero\\s+actas", UNICODE_CANON),
                    Pattern.compile("(?i)0\\s+actas", UNICODE_CANON),
                    Pattern.compile("(?i)fecha\\s+futur", UNICODE_CANON),
                    Pattern.compile("(?i)a[uú]n\\s+no\\s+ha\\s+ocurr", UNICODE_CANON),
                    Pattern.compile("(?i)no\\s+puede\\s+existir", UNICODE_CANON),
                    Pattern.compile("(?i)no\\s+actas?", UNICODE_CANON));

    private static final Pattern NUMBER_WORD =
            Pattern.compile(
                    "\\b(?:una|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez|\\d+)\\b", UNICODE_CANON);

    private static final int MAX_CHARS_BETWEEN_COUNT_AND_ACTA = 80;

    private DeterministicToolNegativeAnswerDetector() {}

    public static boolean isNegativeOrNoData(String answerText) {
        if (answerText == null || answerText.isBlank()) {
            return true;
        }
        String normalized = answerText.trim();
        if (normalized.length() <= 4 && SHORT_NEGATIVE.matcher(normalized).matches()) {
            return true;
        }
        if (EXPLICIT_ZERO_COUNT.matcher(normalized).find()) {
            return true;
        }
        return containsNegativePhrase(normalized);
    }

    public static boolean isAffirmativeCountOrList(String answerText) {
        if (answerText == null || answerText.isBlank() || isNegativeOrNoData(answerText)) {
            return false;
        }
        String lower = answerText.toLowerCase(Locale.ROOT);
        return hasAffirmativeCountWithActa(lower)
                || lower.contains(".pdf")
                || lower.contains("acta del")
                || lower.contains("acta de");
    }

    private static boolean containsNegativePhrase(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        for (String phrase : NEGATIVE_ENGLISH_PHRASES) {
            if (lower.contains(phrase)) {
                return true;
            }
        }
        for (Pattern pattern : NEGATIVE_SPANISH_PATTERNS) {
            if (pattern.matcher(text).find()) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasAffirmativeCountWithActa(String lower) {
        Matcher matcher = NUMBER_WORD.matcher(lower);
        while (matcher.find()) {
            int actaIndex = lower.indexOf("acta", matcher.end());
            if (actaIndex >= 0 && actaIndex - matcher.end() <= MAX_CHARS_BETWEEN_COUNT_AND_ACTA) {
                return true;
            }
        }
        return false;
    }
}
