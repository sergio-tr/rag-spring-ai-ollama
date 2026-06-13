package com.uniovi.rag.domain.runtime.retrieval;

import java.util.List;
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
        int beforePostRetrievalCount,
        int afterRerankCount,
        int afterFilterCount,
        int afterCompressionCount,
        int protectedCandidateCount,
        int droppedCandidateCount,
        boolean rerankApplied,
        List<String> beforeRerankTopCandidateIds,
        List<String> afterRerankTopCandidateIds,
        Optional<String> rerankScoreSummary,
        int compressionCharsBefore,
        int compressionCharsAfter,
        boolean rerankOrderChanged,
        int dedupedCandidateCount) {

    public RetrievalDiagnostics {
        fusionMode = Objects.requireNonNullElseGet(fusionMode, Optional::empty);
        snapshotIdsJoined = snapshotIdsJoined == null ? "" : snapshotIdsJoined;
        beforeRerankTopCandidateIds =
                List.copyOf(Objects.requireNonNullElse(beforeRerankTopCandidateIds, List.of()));
        afterRerankTopCandidateIds =
                List.copyOf(Objects.requireNonNullElse(afterRerankTopCandidateIds, List.of()));
        rerankScoreSummary = Objects.requireNonNullElseGet(rerankScoreSummary, Optional::empty);
    }
}
