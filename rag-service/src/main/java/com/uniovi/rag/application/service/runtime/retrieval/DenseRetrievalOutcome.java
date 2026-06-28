package com.uniovi.rag.application.service.runtime.retrieval;

import com.uniovi.rag.domain.runtime.retrieval.RetrievalCandidate;
import java.util.List;

/** Dense retrieval result with stage counts for diagnostics and Lab export. */
public record DenseRetrievalOutcome(
        List<RetrievalCandidate> candidates,
        int rawCandidateCount,
        int postSnapshotCandidateCount,
        int postProjectCandidateCount) {

    public DenseRetrievalOutcome {
        candidates = candidates != null ? List.copyOf(candidates) : List.of();
    }
}
