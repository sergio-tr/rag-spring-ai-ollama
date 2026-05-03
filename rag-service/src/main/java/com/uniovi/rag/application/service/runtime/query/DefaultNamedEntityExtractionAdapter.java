package com.uniovi.rag.application.service.runtime.query;

import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.query.EntityExtractionResult;
import com.uniovi.rag.service.analyser.QueryAnalyser;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
            return map(json);
        } catch (Exception e) {
            return EntityExtractionResult.emptyWithNote("FALLBACK: " + safeMsg(e));
        }
    }

    private static EntityExtractionResult map(JSONObject json) {
        if (json == null) {
            return EntityExtractionResult.emptyWithNote("FALLBACK: null_ner_json");
        }

        List<String> people = new ArrayList<>();
        // attendees + president + secretary
        people.addAll(readArray(json, "attendees"));
        people.addAll(readArray(json, "president"));
        people.addAll(readArray(json, "secretary"));

        List<String> dates = readArray(json, "date");
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

