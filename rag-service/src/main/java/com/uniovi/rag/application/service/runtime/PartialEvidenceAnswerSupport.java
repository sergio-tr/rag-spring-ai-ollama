package com.uniovi.rag.application.service.runtime;

import com.uniovi.rag.application.service.runtime.language.QueryLanguagePolicy;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Ensures partial but grounded answers state what is known and what is missing. */
public final class PartialEvidenceAnswerSupport {

    private static final Pattern LIMITATION_ALREADY_STATED =
            Pattern.compile(
                    "(?i)(?:limitaci[oó]n|no\\s+(?:se\\s+)?(?:detall|incluy|list)|"
                            + "sin\\s+nombres|no\\s+consta|fragmento|parcial|"
                            + "only\\s+partial|not\\s+(?:fully|completely)\\s+(?:available|listed))",
                    Pattern.UNICODE_CASE);

    private static final Pattern COUNT_ONLY_ANSWER =
            Pattern.compile(
                    "\\d{1,9}\\s+(?:asistentes|participantes|participants|attendees)\\b",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.CANON_EQ);

    private static final Pattern NAMED_ATTENDEE_IN_ANSWER =
            Pattern.compile(
                    "(?:Jorge|María|Manuel|Ana|Pedro|[A-ZÁÉÍÓÚÑ][a-záéíóúñ]+\\s+[A-ZÁÉÍÓÚÑ])",
                    Pattern.UNICODE_CASE | Pattern.CANON_EQ);

    private PartialEvidenceAnswerSupport() {}

    public static String enrichIfPartial(QueryPlan plan, String answer, List<Map<String, Object>> responseSources) {
        if (answer == null || answer.isBlank() || responseSources == null || responseSources.isEmpty()) {
            return answer;
        }
        if (LIMITATION_ALREADY_STATED.matcher(answer).find()) {
            return answer;
        }
        String query = queryText(plan);
        String attendeeGap = attendeeNameGapNote(query, answer);
        if (attendeeGap != null) {
            return attendeeGap;
        }
        if (shouldSkipCorpusDateGuard(query, answer, responseSources)) {
            return answer;
        }
        String actaNote = actaPartialEvidenceNote(query, answer, responseSources);
        return actaNote != null ? actaNote : answer;
    }

    private static String attendeeNameGapNote(String query, String answer) {
        if (!isAttendeeQuery(query) || !mentionsCountOnly(answer)) {
            return null;
        }
        boolean spanish = QueryLanguagePolicy.looksSpanish(query != null && !query.isBlank() ? query : answer);
        if (spanish) {
            return answer.trim()
                    + "\n\nLimitación: el contexto recuperado indica el número de asistentes, pero no incluye la lista completa de nombres.";
        }
        return answer.trim()
                + "\n\nLimitation: retrieved context shows the attendee count but not the full name list.";
    }

    private static boolean isAttendeeQuery(String query) {
        if (query == null || query.isBlank()) {
            return false;
        }
        String queryLower = query.toLowerCase(Locale.ROOT);
        return queryLower.contains("asistieron")
                || queryLower.contains("presentes")
                || queryLower.contains("participantes")
                || queryLower.contains("attended")
                || queryLower.contains("attendees")
                || queryLower.contains("participants");
    }

    private static boolean mentionsCountOnly(String answer) {
        return COUNT_ONLY_ANSWER.matcher(answer).find()
                && !NAMED_ATTENDEE_IN_ANSWER.matcher(answer).find();
    }

    private static boolean shouldSkipCorpusDateGuard(
            String query, String answer, List<Map<String, Object>> responseSources) {
        return !responseSources.isEmpty()
                && answer.length() < 220
                && CorpusDateEvidenceAnswerGuard.answerDeniesDespiteMatchingSources(query, answer, responseSources);
    }

    private static String actaPartialEvidenceNote(
            String query, String answer, List<Map<String, Object>> responseSources) {
        if (responseSources.isEmpty() || answer.length() >= 180) {
            return null;
        }
        if (!QueryLanguagePolicy.looksSpanish(query != null ? query : answer)) {
            return null;
        }
        if (answer.toLowerCase(Locale.ROOT).contains("acta")) {
            return null;
        }
        if (query == null || !query.toLowerCase(Locale.ROOT).contains("acta")) {
            return null;
        }
        return answer.trim()
                + "\n\nLimitación: la evidencia recuperada es parcial; revisa las fuentes citadas para el detalle completo.";
    }

    private static String queryText(QueryPlan plan) {
        if (plan == null) {
            return "";
        }
        if (plan.rewrittenQueryText() != null && !plan.rewrittenQueryText().isBlank()) {
            return plan.rewrittenQueryText();
        }
        return plan.normalizedQueryText() != null ? plan.normalizedQueryText() : "";
    }
}
