package com.uniovi.rag.domain.runtime.engine;

import com.uniovi.rag.domain.runtime.retrieval.RetrievalDiagnostics;

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
        String queryPlanVersion,
        String classifierStatus,
        String classifierLabel,
        String expectedAnswerShape,
        String ambiguityStatus,
        String compatibilitySeverity,
        String deterministicToolOutcome,
        String deterministicToolKind,
        String deterministicToolDetail,
        boolean functionCallingAttempted,
        String functionCallingOutcome,
        String functionCallingToolKind,
        boolean functionCallingShortCircuited,
        Optional<RetrievalDiagnostics> retrievalDiagnostics,
        boolean advisorAttempted,
        boolean advisorShortCircuitedContextPrep,
        String advisorKindsExecuted,
        String advisorOutcome,
        int packedContextBlockCount,
        int packedContextSourceCount) {

    public ExecutionTrace {
        stages = List.copyOf(stages);
        usedKnowledgeSnapshotIds = List.copyOf(usedKnowledgeSnapshotIds);
        usedResolvedConfigSnapshotId =
                usedResolvedConfigSnapshotId == null ? Optional.empty() : usedResolvedConfigSnapshotId;
        usedConfigHash = usedConfigHash == null ? Optional.empty() : usedConfigHash;
        queryPlanVersion = queryPlanVersion == null ? "" : queryPlanVersion;
        classifierStatus = classifierStatus == null ? "" : classifierStatus;
        classifierLabel = classifierLabel == null ? "" : classifierLabel;
        expectedAnswerShape = expectedAnswerShape == null ? "" : expectedAnswerShape;
        ambiguityStatus = ambiguityStatus == null ? "" : ambiguityStatus;
        compatibilitySeverity = compatibilitySeverity == null ? "" : compatibilitySeverity;
        deterministicToolOutcome = deterministicToolOutcome == null ? "" : deterministicToolOutcome;
        deterministicToolKind = deterministicToolKind == null ? "" : deterministicToolKind;
        deterministicToolDetail = deterministicToolDetail == null ? "" : deterministicToolDetail;
        functionCallingOutcome = functionCallingOutcome == null ? "" : functionCallingOutcome;
        functionCallingToolKind = functionCallingToolKind == null ? "" : functionCallingToolKind;
        retrievalDiagnostics = retrievalDiagnostics == null ? Optional.empty() : retrievalDiagnostics;
        advisorKindsExecuted = advisorKindsExecuted == null ? "" : advisorKindsExecuted;
        advisorOutcome = advisorOutcome == null ? "" : advisorOutcome;
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
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                false,
                "",
                "",
                false,
                Optional.empty(),
                false,
                false,
                "",
                "",
                0,
                0);
    }
}
