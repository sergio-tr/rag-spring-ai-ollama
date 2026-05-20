package com.uniovi.rag.application.service.runtime;

import com.uniovi.rag.application.result.chat.ChatSource;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalCandidate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class RuntimeRetrievedSourceMapper {

    private static final int MAX_SOURCES = 8;
    private static final int SNIPPET_MAX = 240;

    private RuntimeRetrievedSourceMapper() {}

    static List<ChatSource> toChatSources(List<RetrievalCandidate> candidates) {
        return toChatSources(candidates, Optional.empty(), null);
    }

    static List<ChatSource> toChatSources(
            List<RetrievalCandidate> candidates,
            Optional<DateGroundingSupport.RequestedDate> requestedDate,
            DateGroundingSupport.DateGroundingDecision dateDecision) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        boolean globalMismatch = dateDecision != null && dateDecision.dateMismatchDetected();
        List<ChatSource> out = new ArrayList<>();
        int n = Math.min(MAX_SOURCES, candidates.size());
        for (int i = 0; i < n; i++) {
            RetrievalCandidate c = candidates.get(i);
            Map<String, Object> meta = c.metadata() != null ? c.metadata() : Map.of();
            String filename = str(meta.get("filename"));
            String documentId = firstPresentStr(meta, "documentId", "document_id", "projectDocumentId");
            String projectDocumentId = str(meta.get("projectDocumentId"));
            Integer chunkIndex = intOrNull(firstPresent(meta, "chunkIndex", "chunk_index"));
            Double distance = doubleOrNull(meta.get("distance"));
            String detectedDate = firstPresentStr(meta, "detectedDate", "documentDate", "date_iso", "date", "meetingDate");
            if (detectedDate == null) {
                String inferred = DateGroundingSupport.profile(c).isoDate();
                detectedDate = inferred != null && !inferred.isBlank() ? inferred : null;
            }

            String snippet = null;
            String text = c.content();
            if (text != null && !text.isBlank()) {
                String t = text.trim();
                snippet = t.length() > SNIPPET_MAX ? t.substring(0, SNIPPET_MAX) + "…" : t;
            }

            Map<String, Object> allowlisted = new LinkedHashMap<>(allowlistMetadata(meta));
            boolean supporting =
                    !globalMismatch
                            && (requestedDate.isEmpty()
                                    || DateGroundingSupport.candidateMatchesRequestedDate(c, requestedDate.get()));
            allowlisted.put("supportingAnswer", supporting);
            if (globalMismatch) {
                allowlisted.put("alternativeOnly", true);
            } else if (requestedDate.isPresent()
                    && detectedDate != null
                    && !DateGroundingSupport.candidateMatchesRequestedDate(c, requestedDate.get())) {
                allowlisted.put("dateMismatchWithRequest", true);
            }
            out.add(new ChatSource(
                    documentId,
                    projectDocumentId,
                    filename,
                    snippet,
                    distance,
                    "distance",
                    chunkIndex,
                    detectedDate,
                    allowlisted.isEmpty() ? null : Map.copyOf(allowlisted)));
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

    private static String firstPresentStr(Map<String, Object> m, String... keys) {
        Object v = firstPresent(m, keys);
        return str(v);
    }

    private static String str(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }

    private static Integer intOrNull(Object v) {
        if (v == null) return null;
        try {
            return v instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(v));
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Double doubleOrNull(Object v) {
        if (v == null) return null;
        try {
            return v instanceof Number n ? n.doubleValue() : Double.parseDouble(String.valueOf(v));
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Map<String, Object> allowlistMetadata(Map<String, Object> meta) {
        if (meta == null || meta.isEmpty()) return Map.of();
        Map<String, Object> out = new LinkedHashMap<>();
        copy(out, meta, "page");
        copy(out, meta, "source");
        copy(out, meta, "section");
        copy(out, meta, "date_iso");
        copy(out, meta, "documentDate");
        copy(out, meta, "detectedDate");
        copy(out, meta, "president");
        copy(out, meta, "secretary");
        return out;
    }

    private static void copy(Map<String, Object> out, Map<String, Object> meta, String k) {
        Object v = meta.get(k);
        if (v == null) return;
        String s = String.valueOf(v).trim();
        if (!s.isEmpty()) out.put(k, v);
    }
}
