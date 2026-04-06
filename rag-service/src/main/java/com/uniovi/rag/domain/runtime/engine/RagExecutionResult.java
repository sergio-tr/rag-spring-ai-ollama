package com.uniovi.rag.domain.runtime.engine;

import com.uniovi.rag.domain.model.QueryType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Canonical runtime output for one turn before mapping to {@code QueryResponse}.
 */
public record RagExecutionResult(
        String answerText,
        String workflowName,
        boolean retrievalUsed,
        boolean metadataUsed,
        Optional<UUID> usedResolvedConfigSnapshotId,
        Optional<String> usedConfigHash,
        List<UUID> usedKnowledgeSnapshotIds,
        ExecutionTrace executionTrace,
        String toolUsedLabel,
        QueryType queryTypeForLegacy,
        boolean usedTool,
        List<ExecutionStageTrace> workflowStageTraces) {

    public RagExecutionResult {
        usedKnowledgeSnapshotIds = List.copyOf(usedKnowledgeSnapshotIds);
        workflowStageTraces = List.copyOf(workflowStageTraces);
        usedResolvedConfigSnapshotId =
                usedResolvedConfigSnapshotId == null ? Optional.empty() : usedResolvedConfigSnapshotId;
        usedConfigHash = usedConfigHash == null ? Optional.empty() : usedConfigHash;
        toolUsedLabel = toolUsedLabel;
    }

    public static RagExecutionResult withPlaceholderTrace(
            String answerText,
            String workflowName,
            boolean retrievalUsed,
            boolean metadataUsed,
            List<UUID> usedKnowledgeSnapshotIds,
            String toolUsedLabel,
            List<ExecutionStageTrace> workflowStageTraces) {
        return new RagExecutionResult(
                answerText,
                workflowName,
                retrievalUsed,
                metadataUsed,
                Optional.empty(),
                Optional.empty(),
                usedKnowledgeSnapshotIds,
                ExecutionTrace.placeholder(),
                toolUsedLabel,
                null,
                false,
                workflowStageTraces);
    }

    public RagExecutionResult withFinalTrace(ExecutionTrace finalTrace) {
        return new RagExecutionResult(
                answerText,
                workflowName,
                retrievalUsed,
                metadataUsed,
                usedResolvedConfigSnapshotId,
                usedConfigHash,
                usedKnowledgeSnapshotIds,
                finalTrace,
                toolUsedLabel,
                queryTypeForLegacy,
                usedTool,
                workflowStageTraces);
    }
}
