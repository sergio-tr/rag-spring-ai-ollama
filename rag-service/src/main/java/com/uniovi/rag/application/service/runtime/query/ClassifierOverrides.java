package com.uniovi.rag.application.service.runtime.query;

import com.uniovi.rag.domain.model.QueryType;

import java.util.Locale;

/**
 * Rule-based adjustments after ML classifier output (Spanish phrasing hints).
 */
public final class ClassifierOverrides {

    private ClassifierOverrides() {}

    /**
     * @param query       user text (typically expanded)
     * @param classified  label from {@link com.uniovi.rag.infrastructure.classifier.QueryClassifier}, may be null
     * @return overridden {@link QueryType}, or {@code classified} when no rule matches
     */
    public static QueryType apply(String query, QueryType classified) {
        if (query == null || query.isBlank()) {
            return classified;
        }
        String q = query.toLowerCase(Locale.ROOT);
        // Presence / verification phrasing → boolean-style answer over generic summarization
        if (q.contains("confirma si aparece")) {
            return QueryType.BOOLEAN_QUERY;
        }
        // Spanish minutes ("acta") field extraction. This is a deterministic fallback when the ML classifier
        // returns null/unknown (e.g. Spanish query with date grounding).
        if ((q.contains("presidente") || q.contains("presidió") || q.contains("presidio"))
                && (q.contains("acta") || q.contains("minuta") || q.contains("reunión") || q.contains("reunion"))
                && (q.matches(".*\\b\\d{4}-\\d{2}-\\d{2}\\b.*")
                        || q.matches(".*\\b\\d{1,2}[/-]\\d{1,2}[/-]\\d{4}\\b.*")
                        || q.matches(".*\\b\\d{1,2}\\s+de\\s+\\p{L}+\\s+de\\s+\\d{4}\\b.*"))) {
            return QueryType.GET_FIELD;
        }
        return classified;
    }
}
