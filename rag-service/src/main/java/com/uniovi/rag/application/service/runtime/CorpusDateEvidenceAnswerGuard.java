package com.uniovi.rag.application.service.runtime;

import com.uniovi.rag.application.service.runtime.DateGroundingSupport.DatePrecision;
import com.uniovi.rag.application.service.runtime.DateGroundingSupport.RequestedDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Detects answers that deny corpus evidence for a requested date when retrieved sources already contain it.
 */
public final class CorpusDateEvidenceAnswerGuard {

    private static final Pattern DENIAL_PHRASE =
            Pattern.compile(
                    "(?i)(no\\s+(?:existe|hay|encuentro|se\\s+puede|puede\\s+existir)|no\\s+he\\s+encontrado|"
                            + "no\\s+est[aá]\\s+registrad|fecha\\s+futur|a[uú]n\\s+no\\s+ha\\s+ocurr|imposible|"
                            + "does\\s+not\\s+exist|could\\s+not\\s+find|no\\s+meeting)",
                    Pattern.UNICODE_CASE);

    private CorpusDateEvidenceAnswerGuard() {}

    public static boolean answerDeniesDespiteMatchingSources(
            String query, String answer, List<Map<String, Object>> responseSources) {
        if (query == null
                || query.isBlank()
                || answer == null
                || answer.isBlank()
                || responseSources == null
                || responseSources.isEmpty()) {
            return false;
        }
        if (!DENIAL_PHRASE.matcher(answer).find()) {
            return false;
        }
        Optional<RequestedDate> requested = DateGroundingSupport.requestedDate(query);
        if (requested.isEmpty()) {
            return false;
        }
        return sourcesContainRequestedDate(responseSources, requested.get());
    }

    public static String groundedEvidenceReminder(String query) {
        Optional<RequestedDate> requested = DateGroundingSupport.requestedDate(query);
        String date = requested.map(RequestedDate::display).orElse("la fecha solicitada");
        if (looksSpanish(query)) {
            return "Según las fuentes recuperadas, sí hay documentación del acta con fecha "
                    + date
                    + ". Revisa el contexto citado antes de afirmar que no existe.";
        }
        return "The retrieved sources include documentation for " + date + ".";
    }

    private static boolean sourcesContainRequestedDate(
            List<Map<String, Object>> responseSources, RequestedDate requested) {
        for (Map<String, Object> source : responseSources) {
            if (source == null || source.isEmpty()) {
                continue;
            }
            for (String key : List.of("date_iso", "actaDate", "date", "meetingDate", "documentDate")) {
                Object raw = source.get(key);
                if (raw == null) {
                    continue;
                }
                if (metadataValueMatches(raw.toString(), requested)) {
                    return true;
                }
            }
            Object snippet = source.get("snippet");
            if (snippet != null && DateGroundingSupport.requestedDate(snippet.toString()).equals(Optional.of(requested))) {
                return true;
            }
        }
        return false;
    }

    private static boolean metadataValueMatches(String raw, RequestedDate requested) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        return switch (requested.precision()) {
            case DAY -> raw.trim().startsWith(requested.value());
            case MONTH -> raw.trim().startsWith(requested.value());
            case YEAR -> raw.trim().contains(requested.value());
        };
    }

    private static boolean looksSpanish(String query) {
        String q = query.toLowerCase(Locale.ROOT);
        return q.contains("acta") || q.contains("asistentes") || q.contains("¿");
    }
}
