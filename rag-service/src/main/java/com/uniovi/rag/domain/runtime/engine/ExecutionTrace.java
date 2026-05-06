package com.uniovi.rag.domain.runtime.engine;

import com.uniovi.rag.domain.runtime.retrieval.RetrievalDiagnostics;
import java.util.List;
import java.util.Objects;
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
        boolean memoryAttempted,
        String memoryOutcome,
        boolean memoryHistoryLoaded,
        boolean memoryCondensationAttempted,
        boolean memoryCondensationUsed,
        boolean memoryFallbackApplied,
        boolean routingAttempted,
        String routingOutcome,
        String routingRouteKind,
        boolean routingFallbackApplied,
        String routingFallbackRouteKind,
        boolean routingWorkflowSelectorInvoked,
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
        int packedContextSourceCount,
        boolean judgeAttempted,
        String judgeCandidateSource,
        boolean judgeRetryRequested,
        boolean judgeRetryAttempted,
        boolean judgeRetrySucceeded,
        String judgeFinalOutcome,
        boolean judgeFinalAnswerFromRetry,
        String judgeKind,
        String judgeDetail,
        boolean clarificationAttempted,
        String clarificationOutcome,
        boolean clarificationPendingStateConsumed,
        boolean clarificationQuestionAsked,
        String originalQuery,
        String retrievalQuery,
        String packedContextPreview,
        int sourceCount,
        List<String> retrievedDocumentNames) {

    public ExecutionTrace {
        stages = List.copyOf(stages);
        usedKnowledgeSnapshotIds = List.copyOf(usedKnowledgeSnapshotIds);
        usedResolvedConfigSnapshotId = Objects.requireNonNullElseGet(usedResolvedConfigSnapshotId, Optional::empty);
        usedConfigHash = Objects.requireNonNullElseGet(usedConfigHash, Optional::empty);
        queryPlanVersion = queryPlanVersion == null ? "" : queryPlanVersion;
        classifierStatus = classifierStatus == null ? "" : classifierStatus;
        classifierLabel = classifierLabel == null ? "" : classifierLabel;
        expectedAnswerShape = expectedAnswerShape == null ? "" : expectedAnswerShape;
        ambiguityStatus = ambiguityStatus == null ? "" : ambiguityStatus;
        compatibilitySeverity = compatibilitySeverity == null ? "" : compatibilitySeverity;
        memoryOutcome = memoryOutcome == null ? "" : memoryOutcome;
        routingOutcome = routingOutcome == null ? "" : routingOutcome;
        routingRouteKind = routingRouteKind == null ? "" : routingRouteKind;
        routingFallbackRouteKind = routingFallbackRouteKind == null ? "" : routingFallbackRouteKind;
        deterministicToolOutcome = deterministicToolOutcome == null ? "" : deterministicToolOutcome;
        deterministicToolKind = deterministicToolKind == null ? "" : deterministicToolKind;
        deterministicToolDetail = deterministicToolDetail == null ? "" : deterministicToolDetail;
        functionCallingOutcome = functionCallingOutcome == null ? "" : functionCallingOutcome;
        functionCallingToolKind = functionCallingToolKind == null ? "" : functionCallingToolKind;
        retrievalDiagnostics = Objects.requireNonNullElseGet(retrievalDiagnostics, Optional::empty);
        advisorKindsExecuted = advisorKindsExecuted == null ? "" : advisorKindsExecuted;
        advisorOutcome = advisorOutcome == null ? "" : advisorOutcome;
        judgeCandidateSource = judgeCandidateSource == null ? "" : judgeCandidateSource;
        judgeFinalOutcome = judgeFinalOutcome == null ? "" : judgeFinalOutcome;
        judgeKind = judgeKind == null ? "" : judgeKind;
        judgeDetail = judgeDetail == null ? "" : judgeDetail;
        clarificationOutcome = clarificationOutcome == null ? "" : clarificationOutcome;
        originalQuery = originalQuery == null ? "" : originalQuery;
        retrievalQuery = retrievalQuery == null ? "" : retrievalQuery;
        packedContextPreview = packedContextPreview == null ? "" : packedContextPreview;
        retrievedDocumentNames = List.copyOf(Objects.requireNonNullElseGet(retrievedDocumentNames, List::of));
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
                false,
                "",
                false,
                false,
                false,
                false,
                false,
                "",
                "",
                false,
                "",
                false,
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
                0,
                false,
                "",
                false,
                false,
                false,
                "",
                false,
                "",
                "",
                false,
                "",
                false,
                false,
                "",
                "",
                "",
                0,
                List.of());
    }
}
