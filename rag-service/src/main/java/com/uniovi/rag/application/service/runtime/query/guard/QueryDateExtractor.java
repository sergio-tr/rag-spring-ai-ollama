package com.uniovi.rag.application.service.runtime.query.guard;

import org.json.JSONObject;

import com.uniovi.rag.infrastructure.observability.Loggable;
import com.uniovi.rag.util.NerDateFieldSupport;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
/**
 * Extracts a single normalized (ISO) date from a query and optional NER entities.
 * Used by DateExistenceGuard to check if an act exists for the requested date.
 * Uses only regex and NER (no LLM) for deterministic behavior.
 */
public class QueryDateExtractor implements Loggable {

    private static final Map<String, Integer> SPANISH_MONTHS = Map.ofEntries(
            Map.entry("enero", 1), Map.entry("febrero", 2), Map.entry("marzo", 3), Map.entry("abril", 4),
            Map.entry("mayo", 5), Map.entry("junio", 6), Map.entry("julio", 7), Map.entry("agosto", 8),
            Map.entry("septiembre", 9), Map.entry("setiembre", 9), Map.entry("octubre", 10),
            Map.entry("noviembre", 11), Map.entry("diciembre", 12)
    );

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
            LocalDate parsed = parseDateFlexible(candidate);
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
        appendRegexDateMatches(query, out);
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

    private void appendRegexDateMatches(String query, List<String> out) {
        if (query == null) {
            return;
        }
        findAllMatches(Pattern.compile("(\\d{4}-\\d{2}-\\d{2})"), query, out);
        findAllMatches(Pattern.compile("(\\d{1,2}/\\d{1,2}/\\d{4})"), query, out);
        findAllMatches(Pattern.compile("(\\d{1,2}-\\d{1,2}-\\d{4})"), query, out);
        findAllMatches(Pattern.compile("(\\d{1,2}\\s+de\\s+\\p{L}+\\s+de\\s+\\d{4})", Pattern.CASE_INSENSITIVE), query, out);
        findAllMatches(Pattern.compile(
                "(\\d{1,2}\\s+(enero|febrero|marzo|abril|mayo|junio|julio|agosto|septiembre|setiembre|octubre|noviembre|diciembre)\\s+\\d{4})",
                Pattern.CASE_INSENSITIVE), query, out);
        Matcher yearOnly = Pattern.compile("(?:del\\s+)?año\\s+(\\d{4})", Pattern.CASE_INSENSITIVE).matcher(query);
        if (yearOnly.find()) {
            out.add(yearOnly.group(1) + "-01-01");
        }
    }

    private static void findAllMatches(Pattern pattern, String query, List<String> out) {
        Matcher m = pattern.matcher(query);
        while (m.find()) {
            out.add(m.group(1));
        }
    }

    private LocalDate parseDateFlexible(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) return null;
        String v = dateStr.trim().toLowerCase();

        try {
            return LocalDate.parse(v, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException ignored) {
            // Not ISO_LOCAL_DATE; try flexible formats below.
        }

        List<DateTimeFormatter> formatters = Arrays.asList(
                DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es")),
                DateTimeFormatter.ofPattern("dd 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es")),
                DateTimeFormatter.ofPattern("d de MMMM de yyyy", Locale.forLanguageTag("es")),
                DateTimeFormatter.ofPattern("dd de MMMM de yyyy", Locale.forLanguageTag("es")),
                DateTimeFormatter.ofPattern("d 'de' MMM 'de' yyyy", Locale.forLanguageTag("es")),
                DateTimeFormatter.ofPattern("dd 'de' MMM 'de' yyyy", Locale.forLanguageTag("es")),
                DateTimeFormatter.ofPattern("d/M/yyyy"), DateTimeFormatter.ofPattern("dd/MM/yyyy"),
                DateTimeFormatter.ofPattern("d-M-yyyy"), DateTimeFormatter.ofPattern("dd-MM-yyyy"),
                DateTimeFormatter.ofPattern("yyyy/MM/dd"), DateTimeFormatter.ofPattern("yyyy-MM-dd")
        );
        for (DateTimeFormatter f : formatters) {
            try {
                return LocalDate.parse(v, f);
            } catch (DateTimeParseException ignored) {
                // Try next formatter in the list.
            }
        }

        Pattern spanishPattern = Pattern.compile(
                "(\\d{1,2})\\s+de\\s+(\\p{L}+)\\s+de\\s+(\\d{4})",
                Pattern.CASE_INSENSITIVE);
        Matcher matcher = spanishPattern.matcher(v);
        if (matcher.matches()) {
            int day = Integer.parseInt(matcher.group(1));
            int month = SPANISH_MONTHS.getOrDefault(matcher.group(2).toLowerCase(), -1);
            int year = Integer.parseInt(matcher.group(3));
            if (month >= 1 && month <= 12 && day >= 1 && day <= 31) {
                try {
                    return LocalDate.of(year, month, Math.min(day, LocalDate.of(year, month, 1).lengthOfMonth()));
                } catch (Exception ignored) {
                    // Invalid calendar combination for parsed components.
                }
            }
        }
        return null;
    }

    /**
     * Parses a date string to LocalDate (e.g. from document metadata). Returns null if parsing fails.
     */
    public LocalDate parseToLocalDate(String dateStr) {
        return parseDateFlexible(dateStr);
    }
}
