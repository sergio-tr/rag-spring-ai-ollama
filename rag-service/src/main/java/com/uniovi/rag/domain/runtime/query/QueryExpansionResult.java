package com.uniovi.rag.domain.runtime.query;

import java.util.List;
import java.util.Objects;

/** Result of optional query expansion before routing, tools, or retrieval. */
public record QueryExpansionResult(
        String originalQuery,
        String expandedQuery,
        boolean applied,
        String strategy,
        List<String> addedTerms,
        String traceNote) {

    public QueryExpansionResult {
        originalQuery = Objects.requireNonNullElse(originalQuery, "");
        expandedQuery = Objects.requireNonNullElse(expandedQuery, originalQuery);
        strategy = Objects.requireNonNullElse(strategy, "SKIPPED");
        addedTerms = addedTerms != null ? List.copyOf(addedTerms) : List.of();
        traceNote = traceNote != null ? traceNote : "";
    }

    public static QueryExpansionResult skipped(String originalQuery) {
        String original = originalQuery != null ? originalQuery : "";
        return new QueryExpansionResult(original, original, false, "SKIPPED", List.of(), "expansion_disabled");
    }

    public static QueryExpansionResult applied(
            String originalQuery, String expandedQuery, String strategy, String traceNote) {
        return new QueryExpansionResult(originalQuery, expandedQuery, true, strategy, List.of(), traceNote);
    }

    public static QueryExpansionResult failed(String originalQuery, String warning) {
        String original = originalQuery != null ? originalQuery : "";
        return new QueryExpansionResult(original, original, false, "FAILED", List.of(), warning);
    }

    /** Query text consumed by downstream QU / routing / retrieval stages. */
    public String downstreamQueryText() {
        return expandedQuery.isBlank() ? originalQuery : expandedQuery;
    }
}
