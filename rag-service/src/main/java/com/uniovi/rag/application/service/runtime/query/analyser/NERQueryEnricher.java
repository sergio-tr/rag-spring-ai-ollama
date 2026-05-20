package com.uniovi.rag.application.service.runtime.query.analyser;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds a short "enriched" query from NER entities for retrieval only (vector search).
 * Keeps strict length limits to avoid context/embedding truncation (see notes under rag-service/docs/ or local /docs/ drafts).
 * The enriched query is used only for retriever.retrieve() / retrieveWithMetadataFilters();
 * createContext and LLM prompts continue to use the original query.
 */
public final class NERQueryEnricher {

    private final int maxExtraChars;
    private final int maxTotalChars;

    public NERQueryEnricher(int maxExtraChars, int maxTotalChars) {
        this.maxExtraChars = maxExtraChars > 0 ? maxExtraChars : 80;
        this.maxTotalChars = maxTotalChars > 0 ? maxTotalChars : 512;
    }

    /**
     * Builds query + up to maxExtraChars of NER-derived terms for retrieval.
     * Prioritises: date (first), topics (up to 2), agenda (first), president/secretary.
     */
    public String buildEnrichedQueryForRetrieval(String query, JSONObject ner) {
        if (query == null) query = "";
        if (ner == null || ner.isEmpty()) return query.trim();

        List<String> terms = new ArrayList<>();

        addFirst(ner, "date", terms);
        addUpTo(ner, "topics", 2, terms);
        addFirst(ner, "agenda", terms);
        addFirst(ner, "president", terms);
        addFirst(ner, "secretary", terms);

        if (terms.isEmpty()) return query.trim();

        String suffix = String.join(" ", terms).trim();
        if (suffix.isEmpty()) return query.trim();

        if (suffix.length() > maxExtraChars) {
            suffix = suffix.substring(0, maxExtraChars).trim();
            int lastSpace = suffix.lastIndexOf(' ');
            if (lastSpace > maxExtraChars / 2) suffix = suffix.substring(0, lastSpace);
        }

        String result = (query.trim() + " " + suffix).trim();
        if (result.length() > maxTotalChars) {
            int keep = maxTotalChars - query.trim().length() - 1;
            if (keep <= 0) return query.trim();
            suffix = suffix.length() > keep ? suffix.substring(0, keep).trim() : suffix;
            result = (query.trim() + " " + suffix).trim();
        }
        return result;
    }

    private static void addFirst(JSONObject ner, String key, List<String> out) {
        if (!ner.has(key) || ner.isNull(key)) return;
        try {
            JSONArray arr = ner.getJSONArray(key);
            if (arr.length() > 0) {
                String v = arr.optString(0, "").trim();
                if (!v.isEmpty() && !out.contains(v)) out.add(v);
            }
        } catch (Exception ignored) {
            // Value is present but not a JSONArray (unexpected NER shape); skip this key.
        }
    }

    private static void addUpTo(JSONObject ner, String key, int max, List<String> out) {
        if (!ner.has(key) || ner.isNull(key)) return;
        try {
            JSONArray arr = ner.getJSONArray(key);
            for (int i = 0; i < Math.min(arr.length(), max); i++) {
                String v = arr.optString(i, "").trim();
                if (!v.isEmpty() && !out.contains(v)) out.add(v);
            }
        } catch (Exception ignored) {
            // Value is present but not a JSONArray (unexpected NER shape); skip this key.
        }
    }
}
