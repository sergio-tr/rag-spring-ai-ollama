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

    private PartialEvidenceAnswerSupport() {}

    public static String enrichIfPartial(QueryPlan plan, String answer, List<Map<String, Object>> responseSources) {
        if (answer == null || answer.isBlank() || responseSources == null || responseSources.isEmpty()) {
            return answer;
        }
        if (LIMITATION_ALREADY_STATED.matcher(answer).find()) {
            return answer;
        }
        String query = queryText(plan);
        boolean spanish = QueryLanguagePolicy.looksSpanish(query != null ? query : answer);
        String queryLower = query != null ? query.toLowerCase(Locale.ROOT) : "";
        boolean attendeeQuery =
                !queryLower.isBlank()
                        && (queryLower.contains("asistieron")
                                || queryLower.contains("presentes")
                                || queryLower.contains("participantes")
                                || queryLower.contains("attended")
                                || queryLower.contains("attendees")
                                || queryLower.contains("participants"));
        boolean mentionsCountOnly =
                answer.matches("(?s).*(?:\\d+\\s+(?:asistentes|participantes|participants|attendees)).*")
                        && !answer.matches("(?s).*(?:Jorge|María|Manuel|Ana|Pedro|[A-ZÁÉÍÓÚÑ][a-záéíóúñ]+\\s+[A-ZÁÉÍÓÚÑ]).*");
        if (attendeeQuery && mentionsCountOnly) {
            if (spanish) {
                return answer.trim()
                        + "\n\nLimitación: el contexto recuperado indica el número de asistentes, pero no incluye la lista completa de nombres.";
            }
            return answer.trim()
                    + "\n\nLimitation: retrieved context shows the attendee count but not the full name list.";
        }
        if (responseSources.size() >= 1
                && answer.length() < 220
                && CorpusDateEvidenceAnswerGuard.answerDeniesDespiteMatchingSources(query, answer, responseSources)) {
            return answer;
        }
        if (responseSources.size() >= 1
                && answer.length() < 180
                && spanish
                && !answer.toLowerCase(Locale.ROOT).contains("acta")
                && query != null
                && query.toLowerCase(Locale.ROOT).contains("acta")) {
            return answer.trim()
                    + "\n\nLimitación: la evidencia recuperada es parcial; revisa las fuentes citadas para el detalle completo.";
        }
        return answer;
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
