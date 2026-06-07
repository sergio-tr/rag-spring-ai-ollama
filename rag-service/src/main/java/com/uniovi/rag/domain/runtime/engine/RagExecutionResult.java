package com.uniovi.rag.domain.runtime.engine;

import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalDiagnostics;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
        QueryType resolvedQueryType,
        boolean usedTool,
        List<ExecutionStageTrace> workflowStageTraces,
        Optional<RetrievalDiagnostics> retrievalDiagnostics,
        List<Map<String, Object>> responseSources) {

    /** Canonical ctor normalizes nullable Optional components (explicit body avoids record compact ctor Sonar noise). */
    public RagExecutionResult(
            String answerText,
            String workflowName,
            boolean retrievalUsed,
            boolean metadataUsed,
            Optional<UUID> usedResolvedConfigSnapshotId,
            Optional<String> usedConfigHash,
            List<UUID> usedKnowledgeSnapshotIds,
            ExecutionTrace executionTrace,
            String toolUsedLabel,
            QueryType resolvedQueryType,
            boolean usedTool,
            List<ExecutionStageTrace> workflowStageTraces,
            Optional<RetrievalDiagnostics> retrievalDiagnostics,
            List<Map<String, Object>> responseSources) {
        this.answerText = answerText;
        this.workflowName = workflowName;
        this.retrievalUsed = retrievalUsed;
        this.metadataUsed = metadataUsed;
        this.usedResolvedConfigSnapshotId =
                Objects.requireNonNullElseGet(usedResolvedConfigSnapshotId, Optional::empty);
        this.usedConfigHash = Objects.requireNonNullElseGet(usedConfigHash, Optional::empty);
        this.usedKnowledgeSnapshotIds = List.copyOf(usedKnowledgeSnapshotIds);
        this.executionTrace = executionTrace;
        this.toolUsedLabel = toolUsedLabel;
        this.resolvedQueryType = resolvedQueryType;
        this.usedTool = usedTool;
        this.workflowStageTraces = List.copyOf(workflowStageTraces);
        this.retrievalDiagnostics = Objects.requireNonNullElseGet(retrievalDiagnostics, Optional::empty);
        this.responseSources = List.copyOf(Objects.requireNonNullElseGet(responseSources, List::of));
    }

    public static RagExecutionResult withPlaceholderTrace(
            String answerText,
            String workflowName,
            boolean retrievalUsed,
            boolean metadataUsed,
            List<UUID> usedKnowledgeSnapshotIds,
            String toolUsedLabel,
            List<ExecutionStageTrace> workflowStageTraces) {
        return withPlaceholderTrace(
                answerText,
                workflowName,
                retrievalUsed,
                metadataUsed,
                usedKnowledgeSnapshotIds,
                toolUsedLabel,
                Optional.empty(),
                workflowStageTraces);
    }

    public static RagExecutionResult withPlaceholderTrace(
            String answerText,
            String workflowName,
            boolean retrievalUsed,
            boolean metadataUsed,
            List<UUID> usedKnowledgeSnapshotIds,
            String toolUsedLabel,
            Optional<RetrievalDiagnostics> retrievalDiagnostics,
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
                workflowStageTraces,
                retrievalDiagnostics,
                List.of());
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
                resolvedQueryType,
                usedTool,
                workflowStageTraces,
                retrievalDiagnostics,
                responseSources);
    }

    public RagExecutionResult withResponseSources(List<Map<String, Object>> nextSources) {
        return new RagExecutionResult(
                answerText,
                workflowName,
                retrievalUsed,
                metadataUsed,
                usedResolvedConfigSnapshotId,
                usedConfigHash,
                usedKnowledgeSnapshotIds,
                executionTrace,
                toolUsedLabel,
                resolvedQueryType,
                usedTool,
                workflowStageTraces,
                retrievalDiagnostics,
                nextSources);
    }
}
