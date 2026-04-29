package com.uniovi.rag.domain.runtime.retrieval;

import java.util.Objects;
import java.util.Optional;

/**
 * Summary counts and modes for retrieval, projected into {@link com.uniovi.rag.domain.runtime.engine.ExecutionTrace}.
 */
public record RetrievalDiagnostics(
        RetrievalMode retrievalMode,
        Optional<RetrievalFusionMode> fusionMode,
        String snapshotIdsJoined,
        int denseCandidateCount,
        int sparseCandidateCount,
        int afterFusionCount,
        int afterRerankCount,
        int afterFilterCount,
        int afterCompressionCount) {

    public RetrievalDiagnostics {
        fusionMode = Objects.requireNonNullElseGet(fusionMode, Optional::empty);
        snapshotIdsJoined = snapshotIdsJoined == null ? "" : snapshotIdsJoined;
    }
}
