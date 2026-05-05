package com.uniovi.rag.application.service.runtime;

import com.uniovi.rag.domain.runtime.retrieval.RetrievalCandidate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class RuntimeRetrievedSourceMapper {

    private static final int MAX_SOURCES = 8;
    private static final int SNIPPET_MAX = 240;

    private RuntimeRetrievedSourceMapper() {}

    static List<Map<String, Object>> toChatSources(List<RetrievalCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        int n = Math.min(MAX_SOURCES, candidates.size());
        for (int i = 0; i < n; i++) {
            RetrievalCandidate c = candidates.get(i);
            Map<String, Object> row = new LinkedHashMap<>();
            copyMeta(row, "filename", c.metadata().get("filename"));
            copyMeta(row, "document_id", firstPresent(c.metadata(), "document_id", "documentId", "projectDocumentId"));
            copyMeta(row, "projectDocumentId", c.metadata().get("projectDocumentId"));
            copyMeta(row, "chunk_index", c.metadata().get("chunk_index"));
            copyMeta(row, "distance", c.metadata().get("distance"));
            String text = c.content();
            if (text != null && !text.isBlank()) {
                String t = text.trim();
                row.put("snippet", t.length() > SNIPPET_MAX ? t.substring(0, SNIPPET_MAX) + "…" : t);
            }
            if (!row.isEmpty()) {
                out.add(row);
            }
        }
        return out;
    }

    private static Object firstPresent(Map<String, Object> m, String... keys) {
        for (String k : keys) {
            Object v = m.get(k);
            if (v != null && !String.valueOf(v).isBlank()) {
                return v;
            }
        }
        return null;
    }

    private static void copyMeta(Map<String, Object> target, String key, Object value) {
        if (value == null) {
            return;
        }
        String s = String.valueOf(value).trim();
        if (!s.isEmpty()) {
            target.put(key, value);
        }
    }
}
