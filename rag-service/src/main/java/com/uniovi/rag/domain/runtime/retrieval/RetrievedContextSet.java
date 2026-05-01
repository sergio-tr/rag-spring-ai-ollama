package com.uniovi.rag.domain.runtime.retrieval;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record RetrievedContextSet(
        List<RetrievalCandidate> candidates,
        Optional<RetrievalFusionMode> fusionModeUsed,
        int denseInputCount,
        int sparseInputCount,
        int fusedCount) {

    public RetrievedContextSet {
        candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates"));
        fusionModeUsed = Objects.requireNonNullElseGet(fusionModeUsed, Optional::empty);
    }
}
