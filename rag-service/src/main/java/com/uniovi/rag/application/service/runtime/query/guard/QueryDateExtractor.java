package com.uniovi.rag.application.service.runtime.query.guard;

import com.uniovi.rag.infrastructure.observability.Loggable;
import com.uniovi.rag.util.NerDateFieldSupport;
import com.uniovi.rag.util.QueryDateSupport;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONObject;

/**
 * Extracts a single normalized (ISO) date from a query and optional NER entities.
 * Used by DateExistenceGuard to check if an act exists for the requested date.
 * Uses only regex and NER (no LLM) for deterministic behavior.
 */
public class QueryDateExtractor implements Loggable {

    /**
     * Extracts the first valid date from the query (and NER) and returns it in ISO format (yyyy-MM-dd).
     * Returns null if no date is found or parsing fails.
     */
    public String extractNormalizedDate(String query, JSONObject nerEntities) {
        if (query == null || query.trim().isEmpty()) {
            return null;
        }
        List<String> candidates = extractDateCandidates(query, nerEntities);
        for (String candidate : candidates) {
            LocalDate parsed = QueryDateSupport.parseFlexibleOrNull(candidate);
            if (parsed != null) {
                String normalized = parsed.format(DateTimeFormatter.ISO_LOCAL_DATE);
                log().debug("QueryDateExtractor: extracted date {} -> {}", candidate, normalized);
                return normalized;
            }
        }
        log().debug("QueryDateExtractor: no valid date found in query");
        return null;
    }

    private List<String> extractDateCandidates(String query, JSONObject ner) {
        List<String> out = new ArrayList<>();
        appendNerDateStrings(ner, out);
        if (query != null) {
            out.addAll(QueryDateSupport.extractDateCandidatesFromText(query));
        }
        return out.stream().distinct().toList();
    }

    private void appendNerDateStrings(JSONObject ner, List<String> out) {
        if (ner == null || !ner.has("date")) {
            return;
        }
        for (String s : NerDateFieldSupport.readDateStrings(ner)) {
            if (!s.isBlank()) {
                out.add(s);
            }
        }
    }

    /**
     * Parses a date string to LocalDate (e.g. from document metadata). Returns null if parsing fails.
     */
    public LocalDate parseToLocalDate(String dateStr) {
        return QueryDateSupport.parseFlexibleOrNull(dateStr);
    }
}
