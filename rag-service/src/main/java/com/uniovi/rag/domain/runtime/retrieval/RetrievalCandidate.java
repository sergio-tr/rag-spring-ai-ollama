package com.uniovi.rag.domain.runtime.retrieval;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record RetrievalCandidate(
        String candidateId,
        String content,
        Map<String, Object> metadata,
        double denseScore,
        double sparseScore,
        int denseRank,
        int sparseRank,
        UUID snapshotId,
        double fusedRrfScore) {

    public RetrievalCandidate {
        candidateId = Objects.requireNonNull(candidateId, "candidateId");
        content = content != null ? content : "";
        metadata = Map.copyOf(Objects.requireNonNull(metadata, "metadata"));
        snapshotId = Objects.requireNonNull(snapshotId, "snapshotId");
    }
}
