package com.uniovi.rag.application.service.runtime.tool;

import java.util.Locale;
import java.util.regex.Pattern;

/** Detects deterministic tool answers that deny data or report zero/no matches (ES + EN). */
public final class DeterministicToolNegativeAnswerDetector {

    private static final Pattern NEGATIVE_OR_NO_DATA =
            Pattern.compile(
                    "(?i)(?:"
                            + "no\\s+(?:se\\s+)?(?:encontr(?:aron|ó|o|aron|ar)|encuentra|encuentran|hay|existen|existe|consta|figura|aparece|registr(?:aron|ó|o|a|an)|localiz(?:aron|ó|o|a|an))"
                            + "|ningun[ao]\\s+(?:acta|reuni[oó]n|documento|registro|asistente|coincidencia|resultado|menci[oó]n)"
                            + "|no\\s+hay\\s+ningun[ao]"
                            + "|sin\\s+(?:actas|reuniones|documentos|resultados|coincidencias|informaci[oó]n)"
                            + "|cero\\s+actas"
                            + "|0\\s+actas"
                            + "|fecha\\s+futur"
                            + "|a[uú]n\\s+no\\s+ha\\s+ocurr"
                            + "|no\\s+puede\\s+existir"
                            + "|not\\s+found"
                            + "|no\\s+matching"
                            + "|could\\s+not\\s+find"
                            + "|does\\s+not\\s+exist"
                            + "|no\\s+documents?"
                            + "|no\\s+meetings?"
                            + "|no\\s+records?"
                            + "|no\\s+information\\s+(?:available|found)"
                            + "|topic\\s+not\\s+(?:found|present|mentioned)"
                            + "|no\\s+actas?"
                            + ")",
                    Pattern.UNICODE_CASE);

    private static final Pattern EXPLICIT_ZERO_COUNT =
            Pattern.compile(
                    "(?i)(?:^|\\b)(?:0|zero)\\s+(?:actas?|reuniones?|documentos?|coincidencias?|matches?)(?:\\b|$)",
                    Pattern.UNICODE_CASE);

    private DeterministicToolNegativeAnswerDetector() {}

    public static boolean isNegativeOrNoData(String answerText) {
        if (answerText == null || answerText.isBlank()) {
            return true;
        }
        String normalized = answerText.trim();
        if (normalized.length() <= 4 && normalized.matches("(?i)(?:no|0|none|n/a)")) {
            return true;
        }
        if (EXPLICIT_ZERO_COUNT.matcher(normalized).find()) {
            return true;
        }
        return NEGATIVE_OR_NO_DATA.matcher(normalized).find();
    }

    public static boolean isAffirmativeCountOrList(String answerText) {
        if (answerText == null || answerText.isBlank() || isNegativeOrNoData(answerText)) {
            return false;
        }
        String lower = answerText.toLowerCase(Locale.ROOT);
        return lower.matches(".*\\b(?:una|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez|\\d+)\\b.*acta.*")
                || lower.contains(".pdf")
                || lower.contains("acta del")
                || lower.contains("acta de");
    }
}
