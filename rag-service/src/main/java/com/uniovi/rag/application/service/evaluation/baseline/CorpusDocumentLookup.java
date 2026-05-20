package com.uniovi.rag.application.service.evaluation.baseline;

import com.uniovi.rag.domain.evaluation.workbook.CorpusDocument;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Resolves full-document text from {@link CorpusDocument} auxiliary columns. */
public final class CorpusDocumentLookup {

    private CorpusDocumentLookup() {}

    public static Map<String, String> indexByDocumentId(List<CorpusDocument> corpus) {
        Map<String, String> out = new LinkedHashMap<>();
        if (corpus == null) {
            return out;
        }
        for (CorpusDocument d : corpus) {
            out.put(d.documentId(), primaryText(d));
        }
        return out;
    }

    /**
     * Prefer dedicated full-text columns when present; otherwise join non-empty auxiliary values (deterministic sheet order).
     */
    public static String primaryText(CorpusDocument doc) {
        Objects.requireNonNull(doc, "doc");
        Map<String, String> m = doc.additionalColumns();
        if (m == null || m.isEmpty()) {
            return "";
        }
        for (String preferred :
                List.of("full_document_text", "document_text", "full_text", "text", "body", "content")) {
            for (Map.Entry<String, String> e : m.entrySet()) {
                if (e.getKey() != null && e.getKey().equalsIgnoreCase(preferred)) {
                    return nvl(e.getValue());
                }
            }
        }
        return m.values().stream().filter(Objects::nonNull).map(String::trim).filter(s -> !s.isEmpty()).reduce((a, b) -> a + "\n" + b).orElse("");
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }

    /** Case-insensitive lookup when keys differ only by case. */
    public static String findDocumentText(Map<String, String> byId, String sourceDocumentId) {
        if (sourceDocumentId == null || sourceDocumentId.isBlank() || byId == null || byId.isEmpty()) {
            return "";
        }
        String want = sourceDocumentId.trim();
        if (byId.containsKey(want)) {
            return nvl(byId.get(want));
        }
        for (Map.Entry<String, String> e : byId.entrySet()) {
            if (e.getKey() != null && e.getKey().trim().equalsIgnoreCase(want)) {
                return nvl(e.getValue());
            }
        }
        return "";
    }
}
