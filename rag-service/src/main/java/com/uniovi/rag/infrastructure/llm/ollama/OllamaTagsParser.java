package com.uniovi.rag.infrastructure.llm.ollama;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;

/**
 * Parses the JSON response from Ollama {@code GET /api/tags}.
 */
public final class OllamaTagsParser {

    private OllamaTagsParser() {
    }

    public static Set<String> parseModelNames(String body) {
        Set<String> names = new HashSet<>();
        JSONObject root = new JSONObject(body);
        JSONArray models = root.optJSONArray("models");
        if (models == null) {
            return names;
        }
        for (int i = 0; i < models.length(); i++) {
            JSONObject m = models.optJSONObject(i);
            if (m != null && m.has("name")) {
                names.add(m.getString("name"));
            }
        }
        return names;
    }
}
