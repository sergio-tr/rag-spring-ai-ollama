package com.uniovi.rag.domain.config.indexing;

import java.util.List;

/**
 * Normalized reindex decision ({@link com.uniovi.rag.application.config.ReindexImpactAnalyzer}).
 */
public record ReindexImpact(ReindexImpactLevel level, List<String> reasons) {

    public ReindexImpact {
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
    }

    public static ReindexImpact none() {
        return new ReindexImpact(ReindexImpactLevel.NO_REINDEX, List.of());
    }
}
