package com.uniovi.rag.domain.runtime.retrieval;

import java.util.Objects;

/** Sparse leg telemetry projected into retrieval diagnostics and exports. */
public record SparseRetrievalTelemetry(
        String originalQuery,
        String rewrittenQuery,
        SparseRetrievalFallbackStage fallbackStage,
        boolean hit,
        String noHitReason) {

    public SparseRetrievalTelemetry {
        originalQuery = originalQuery == null ? "" : originalQuery;
        rewrittenQuery = rewrittenQuery == null ? "" : rewrittenQuery;
        fallbackStage = Objects.requireNonNullElse(fallbackStage, SparseRetrievalFallbackStage.NO_HIT);
        noHitReason = noHitReason == null ? "" : noHitReason;
    }
}
