package com.uniovi.rag.application.service.runtime.query;

import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.query.EntityExtractionResult;
import com.uniovi.rag.application.service.runtime.query.analyser.QueryAnalyser;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DefaultNamedEntityExtractionAdapter implements NamedEntityExtractionAdapter {

    private final QueryAnalyser analyser;

    public DefaultNamedEntityExtractionAdapter(QueryAnalyser analyser) {
        this.analyser = analyser;
    }

    @Override
    public EntityExtractionResult extract(ExecutionContext ctx, String normalizedText) {
        RagConfig rag = ctx.resolved().toRagConfig();
        if (!rag.nerEnabled()) {
            return EntityExtractionResult.emptyWithNote("DISABLED");
        }
        try {
            JSONObject json = analyser.analyse(normalizedText);
            return map(json, normalizedText);
        } catch (Exception e) {
            return EntityExtractionResult.emptyWithNote("FALLBACK: " + safeMsg(e));
        }
    }

    private static EntityExtractionResult map(JSONObject json, String normalizedText) {
        if (json == null) {
            return EntityExtractionResult.emptyWithNote("FALLBACK: null_ner_json");
        }

        List<String> people = new ArrayList<>();
        // attendees + president + secretary
        people.addAll(readArray(json, "attendees"));
        people.addAll(readArray(json, "president"));
        people.addAll(readArray(json, "secretary"));

        List<String> dates = normalizeDates(readArray(json, "date"), normalizedText);
        List<String> locations = readArray(json, "place");
        List<String> topics = readArray(json, "topics");
        List<String> organizations = readArray(json, "mentionedEntities");

        Optional<String> temporalContext = readString(json, "temporalContext");
        Optional<String> answerTypeHint = readString(json, "answerType");
        Optional<String> comparisonTypeHint = readString(json, "comparisonType");

        return new EntityExtractionResult(
                normalizeList(people),
                normalizeList(dates),
                normalizeList(locations),
                normalizeList(topics),
                normalizeList(organizations),
                temporalContext,
                answerTypeHint,
                comparisonTypeHint,
                List.of());
    }

    /**
     * The upstream analyser sometimes returns low-signal month-only tokens (e.g. "february") for Spanish queries.
     * We ensure date-bearing queries keep the full temporal signal by extracting explicit date strings from the
     * input text and preferring them over month-only outputs.
     */
    private static List<String> normalizeDates(List<String> analyserDates, String normalizedText) {
        List<String> extracted = extractExplicitDatesFromText(normalizedText);
        if (!extracted.isEmpty()) {
            return extracted;
        }
        // Otherwise, keep analyser output but drop obvious month-only tokens (which cause QU rewrite validation noise).
        if (analyserDates == null || analyserDates.isEmpty()) {
            return List.of();
        }
        return analyserDates.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(String::trim)
                .filter(s -> !looksLikeMonthOnlyToken(s))
                .distinct()
                .toList();
    }

    private static boolean looksLikeMonthOnlyToken(String raw) {
        if (raw == null) {
            return false;
        }
        String s = raw.trim().toLowerCase();
        return switch (s) {
            case "january", "february", "march", "april", "may", "june", "july", "august",
                    "september", "october", "november", "december",
                    "enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto",
                    "septiembre", "setiembre", "octubre", "noviembre", "diciembre" -> true;
            default -> false;
        };
    }

    private static final Pattern DATE_DMY_SLASH = Pattern.compile("\\b\\d{1,2}[/-]\\d{1,2}[/-]\\d{4}\\b");
    private static final Pattern DATE_D_DE_M_DE_Y = Pattern.compile(
            "\\b\\d{1,2}\\s+de\\s+[a-záéíóúñ]+\\s+de\\s+\\d{4}\\b",
            Pattern.CASE_INSENSITIVE);

    private static List<String> extractExplicitDatesFromText(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        Matcher m1 = DATE_DMY_SLASH.matcher(text);
        while (m1.find()) {
            out.add(m1.group());
        }
        Matcher m2 = DATE_D_DE_M_DE_Y.matcher(text);
        while (m2.find()) {
            out.add(m2.group());
        }
        return out.stream().distinct().toList();
    }

    private static List<String> readArray(JSONObject json, String key) {
        if (!json.has(key)) {
            return List.of();
        }
        Object v = json.opt(key);
        if (v instanceof JSONArray arr) {
            List<String> out = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                String s = arr.optString(i, null);
                if (s != null && !s.isBlank()) {
                    out.add(s.trim());
                }
            }
            return out;
        }
        if (v instanceof String s && !s.isBlank() && !"none".equalsIgnoreCase(s) && !"unknown".equalsIgnoreCase(s)) {
            return List.of(s.trim());
        }
        return List.of();
    }

    private static Optional<String> readString(JSONObject json, String key) {
        if (!json.has(key)) {
            return Optional.empty();
        }
        String s = json.optString(key, null);
        if (s == null || s.isBlank()) {
            return Optional.empty();
        }
        String t = s.trim();
        if ("none".equalsIgnoreCase(t) || "unknown".equalsIgnoreCase(t)) {
            return Optional.empty();
        }
        return Optional.of(t);
    }

    private static List<String> normalizeList(List<String> in) {
        if (in == null || in.isEmpty()) {
            return List.of();
        }
        return in.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    private static String safeMsg(Exception e) {
        String m = e.getMessage();
        return m == null ? e.getClass().getSimpleName() : m;
    }
}

