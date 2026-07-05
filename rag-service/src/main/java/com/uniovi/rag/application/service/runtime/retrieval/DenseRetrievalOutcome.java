package com.uniovi.rag.application.service.runtime.retrieval;

import com.uniovi.rag.domain.runtime.retrieval.RetrievalCandidate;
import java.util.List;

/** Dense retrieval result with stage counts for diagnostics and Lab export. */
public record DenseRetrievalOutcome(
        List<RetrievalCandidate> candidates,
        int rawCandidateCount,
        int postSnapshotCandidateCount,
        int postProjectCandidateCount,
        double similarityThresholdUsed,
        int denseFetchLimitUsed) {

    public DenseRetrievalOutcome {
        candidates = candidates != null ? List.copyOf(candidates) : List.of();
    }

    public DenseRetrievalOutcome(
            List<RetrievalCandidate> candidates,
            int rawCandidateCount,
            int postSnapshotCandidateCount,
            int postProjectCandidateCount) {
        this(
                candidates,
                rawCandidateCount,
                postSnapshotCandidateCount,
                postProjectCandidateCount,
                Double.NaN,
                0);
    }
}
