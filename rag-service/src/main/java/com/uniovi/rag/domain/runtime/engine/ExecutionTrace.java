package com.uniovi.rag.domain.runtime.engine;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Final immutable trace for one turn, assembled only by {@code RagExecutionOrchestrator}.
 */
public record ExecutionTrace(
        List<ExecutionStageTrace> stages,
        String workflowName,
        boolean retrievalUsed,
        boolean metadataUsed,
        List<UUID> usedKnowledgeSnapshotIds,
        Optional<UUID> usedResolvedConfigSnapshotId,
        Optional<String> usedConfigHash,
        String compatibilitySeverity) {

    public ExecutionTrace {
        stages = List.copyOf(stages);
        usedKnowledgeSnapshotIds = List.copyOf(usedKnowledgeSnapshotIds);
        usedResolvedConfigSnapshotId =
                usedResolvedConfigSnapshotId == null ? Optional.empty() : usedResolvedConfigSnapshotId;
        usedConfigHash = usedConfigHash == null ? Optional.empty() : usedConfigHash;
        compatibilitySeverity = compatibilitySeverity == null ? "" : compatibilitySeverity;
    }

    public static ExecutionTrace placeholder() {
        return new ExecutionTrace(
                List.of(),
                "",
                false,
                false,
                List.of(),
                Optional.empty(),
                Optional.empty(),
                "");
    }
}
