package com.uniovi.rag.domain.runtime.engine;

import com.uniovi.rag.domain.runtime.retrieval.RetrievalDiagnostics;
import java.util.ArrayList;
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
        List<String> retrievedDocumentNames,
        String answerGroundingPolicy,
        int promptContextCharCount,
        boolean abstentionTriggered,
        String abstentionReason) {

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
        answerGroundingPolicy = answerGroundingPolicy == null ? "" : answerGroundingPolicy;
        abstentionReason = abstentionReason == null ? "" : abstentionReason;
    }

    public ExecutionTrace withIntegratedMonotonicFallback(boolean applied, String fallbackRouteKind) {
        if (!applied) {
            return this;
        }
        String fbKind = fallbackRouteKind == null ? "" : fallbackRouteKind;
        return new ExecutionTrace(
                stages,
                workflowName,
                retrievalUsed,
                metadataUsed,
                usedKnowledgeSnapshotIds,
                usedResolvedConfigSnapshotId,
                usedConfigHash,
                queryPlanVersion,
                classifierStatus,
                classifierLabel,
                expectedAnswerShape,
                ambiguityStatus,
                compatibilitySeverity,
                memoryAttempted,
                memoryOutcome,
                memoryHistoryLoaded,
                memoryCondensationAttempted,
                memoryCondensationUsed,
                memoryFallbackApplied,
                routingAttempted,
                "PRIMARY_ROUTE_SELECTED_WITH_WORKFLOW_FALLBACK",
                routingRouteKind,
                true,
                fbKind,
                routingWorkflowSelectorInvoked,
                "",
                "",
                "",
                functionCallingAttempted,
                functionCallingOutcome,
                "",
                false,
                retrievalDiagnostics,
                advisorAttempted,
                advisorShortCircuitedContextPrep,
                advisorKindsExecuted,
                advisorOutcome,
                packedContextBlockCount,
                packedContextSourceCount,
                judgeAttempted,
                judgeCandidateSource,
                judgeRetryRequested,
                judgeRetryAttempted,
                judgeRetrySucceeded,
                judgeFinalOutcome,
                judgeFinalAnswerFromRetry,
                judgeKind,
                judgeDetail,
                clarificationAttempted,
                clarificationOutcome,
                clarificationPendingStateConsumed,
                clarificationQuestionAsked,
                originalQuery,
                retrievalQuery,
                packedContextPreview,
                sourceCount,
                retrievedDocumentNames,
                answerGroundingPolicy,
                promptContextCharCount,
                abstentionTriggered,
                abstentionReason);
    }

    public ExecutionTrace withAppendedStages(ExecutionStageTrace... extra) {
        if (extra == null || extra.length == 0) {
            return this;
        }
        List<ExecutionStageTrace> merged = new ArrayList<>(stages);
        merged.addAll(List.of(extra));
        return new ExecutionTrace(
                merged,
                workflowName,
                retrievalUsed,
                metadataUsed,
                usedKnowledgeSnapshotIds,
                usedResolvedConfigSnapshotId,
                usedConfigHash,
                queryPlanVersion,
                classifierStatus,
                classifierLabel,
                expectedAnswerShape,
                ambiguityStatus,
                compatibilitySeverity,
                memoryAttempted,
                memoryOutcome,
                memoryHistoryLoaded,
                memoryCondensationAttempted,
                memoryCondensationUsed,
                memoryFallbackApplied,
                routingAttempted,
                routingOutcome,
                routingRouteKind,
                routingFallbackApplied,
                routingFallbackRouteKind,
                routingWorkflowSelectorInvoked,
                deterministicToolOutcome,
                deterministicToolKind,
                deterministicToolDetail,
                functionCallingAttempted,
                functionCallingOutcome,
                functionCallingToolKind,
                functionCallingShortCircuited,
                retrievalDiagnostics,
                advisorAttempted,
                advisorShortCircuitedContextPrep,
                advisorKindsExecuted,
                advisorOutcome,
                packedContextBlockCount,
                packedContextSourceCount,
                judgeAttempted,
                judgeCandidateSource,
                judgeRetryRequested,
                judgeRetryAttempted,
                judgeRetrySucceeded,
                judgeFinalOutcome,
                judgeFinalAnswerFromRetry,
                judgeKind,
                judgeDetail,
                clarificationAttempted,
                clarificationOutcome,
                clarificationPendingStateConsumed,
                clarificationQuestionAsked,
                originalQuery,
                retrievalQuery,
                packedContextPreview,
                sourceCount,
                retrievedDocumentNames,
                answerGroundingPolicy,
                promptContextCharCount,
                abstentionTriggered,
                abstentionReason);
    }

    public ExecutionTrace replacingStages(List<ExecutionStageTrace> newStages) {
        return new ExecutionTrace(
                List.copyOf(newStages),
                workflowName,
                retrievalUsed,
                metadataUsed,
                usedKnowledgeSnapshotIds,
                usedResolvedConfigSnapshotId,
                usedConfigHash,
                queryPlanVersion,
                classifierStatus,
                classifierLabel,
                expectedAnswerShape,
                ambiguityStatus,
                compatibilitySeverity,
                memoryAttempted,
                memoryOutcome,
                memoryHistoryLoaded,
                memoryCondensationAttempted,
                memoryCondensationUsed,
                memoryFallbackApplied,
                routingAttempted,
                routingOutcome,
                routingRouteKind,
                routingFallbackApplied,
                routingFallbackRouteKind,
                routingWorkflowSelectorInvoked,
                deterministicToolOutcome,
                deterministicToolKind,
                deterministicToolDetail,
                functionCallingAttempted,
                functionCallingOutcome,
                functionCallingToolKind,
                functionCallingShortCircuited,
                retrievalDiagnostics,
                advisorAttempted,
                advisorShortCircuitedContextPrep,
                advisorKindsExecuted,
                advisorOutcome,
                packedContextBlockCount,
                packedContextSourceCount,
                judgeAttempted,
                judgeCandidateSource,
                judgeRetryRequested,
                judgeRetryAttempted,
                judgeRetrySucceeded,
                judgeFinalOutcome,
                judgeFinalAnswerFromRetry,
                judgeKind,
                judgeDetail,
                clarificationAttempted,
                clarificationOutcome,
                clarificationPendingStateConsumed,
                clarificationQuestionAsked,
                originalQuery,
                retrievalQuery,
                packedContextPreview,
                sourceCount,
                retrievedDocumentNames,
                answerGroundingPolicy,
                promptContextCharCount,
                abstentionTriggered,
                abstentionReason);
    }

    public static ExecutionTrace placeholder() {
        return campaignParentReplay("", false, "", false);
    }

    public static ExecutionTrace campaignParentReplay(
            String workflowName,
            boolean retrievalUsed,
            String routingRouteKind,
            boolean abstentionTriggered) {
        return new ExecutionTrace(
                List.of(),
                workflowName != null ? workflowName : "",
                retrievalUsed,
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
                true,
                "PRIMARY_ROUTE_SELECTED",
                routingRouteKind != null ? routingRouteKind : "",
                false,
                "",
                true,
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
                List.of(),
                "",
                0,
                abstentionTriggered,
                abstentionTriggered ? "insufficient_context" : "");
    }
}
