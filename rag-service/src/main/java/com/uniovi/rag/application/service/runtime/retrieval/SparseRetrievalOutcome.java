package com.uniovi.rag.application.service.runtime.retrieval;

import com.uniovi.rag.domain.runtime.retrieval.RetrievalCandidate;
import com.uniovi.rag.domain.runtime.retrieval.SparseQueryPreparation;
import com.uniovi.rag.domain.runtime.retrieval.SparseRetrievalFallbackStage;
import com.uniovi.rag.domain.runtime.retrieval.SparseRetrievalTelemetry;
import java.util.List;

/** Result of a sparse retrieval attempt including fallback stage metadata. */
public record SparseRetrievalOutcome(
        List<RetrievalCandidate> candidates,
        SparseQueryPreparation preparation,
        SparseRetrievalFallbackStage fallbackStage,
        String rewrittenQuery,
        SparseRetrievalTelemetry telemetry) {

    public SparseRetrievalOutcome {
        candidates = List.copyOf(candidates != null ? candidates : List.of());
    }

    public boolean hit() {
        return !candidates.isEmpty();
    }
}
