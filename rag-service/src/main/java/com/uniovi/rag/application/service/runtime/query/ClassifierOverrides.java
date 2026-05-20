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
        return classified;
    }
}
