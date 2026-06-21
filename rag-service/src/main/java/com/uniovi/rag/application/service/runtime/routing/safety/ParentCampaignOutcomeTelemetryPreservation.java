package com.uniovi.rag.application.service.runtime.routing.safety;

import com.uniovi.rag.application.service.evaluation.preset.CampaignParentOutcome;
import com.uniovi.rag.domain.runtime.engine.ExecutionTrace;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRouteKind;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolOutcome;

/** Restores parent P6/P7 tool-final export signals when replaying campaign-recorded outcomes. */
public final class ParentCampaignOutcomeTelemetryPreservation {

    private ParentCampaignOutcomeTelemetryPreservation() {}

    public static ExecutionTrace preserveParentToolSignals(
            ExecutionTrace trace, CampaignParentOutcome campaignRecord) {
        if (trace == null || campaignRecord == null || !campaignRecord.usedTool()) {
            return trace;
        }
        String routeKind =
                campaignRecord.routingRouteKind().isBlank()
                        ? AdaptiveRouteKind.DETERMINISTIC_TOOL_ROUTE.name()
                        : campaignRecord.routingRouteKind();
        String workflowName =
                campaignRecord.workflowName().isBlank() ? trace.workflowName() : campaignRecord.workflowName();
        String toolKind = campaignRecord.toolUsedLabel();
        return new ExecutionTrace(
                trace.stages(),
                workflowName,
                trace.retrievalUsed(),
                trace.metadataUsed(),
                trace.usedKnowledgeSnapshotIds(),
                trace.usedResolvedConfigSnapshotId(),
                trace.usedConfigHash(),
                trace.queryPlanVersion(),
                trace.classifierStatus(),
                trace.classifierLabel(),
                trace.expectedAnswerShape(),
                trace.ambiguityStatus(),
                trace.compatibilitySeverity(),
                trace.memoryAttempted(),
                trace.memoryOutcome(),
                trace.memoryHistoryLoaded(),
                trace.memoryCondensationAttempted(),
                trace.memoryCondensationUsed(),
                trace.memoryFallbackApplied(),
                trace.routingAttempted(),
                trace.routingOutcome(),
                routeKind,
                false,
                trace.routingFallbackRouteKind(),
                trace.routingWorkflowSelectorInvoked(),
                DeterministicToolOutcome.EXECUTED_SUCCESS.name(),
                toolKind,
                trace.deterministicToolDetail(),
                trace.functionCallingAttempted(),
                trace.functionCallingOutcome(),
                trace.functionCallingToolKind(),
                trace.functionCallingShortCircuited(),
                trace.retrievalDiagnostics(),
                trace.advisorAttempted(),
                trace.advisorShortCircuitedContextPrep(),
                trace.advisorKindsExecuted(),
                trace.advisorOutcome(),
                trace.packedContextBlockCount(),
                trace.packedContextSourceCount(),
                trace.judgeAttempted(),
                trace.judgeCandidateSource(),
                trace.judgeRetryRequested(),
                trace.judgeRetryAttempted(),
                trace.judgeRetrySucceeded(),
                trace.judgeFinalOutcome(),
                trace.judgeFinalAnswerFromRetry(),
                trace.judgeKind(),
                trace.judgeDetail(),
                trace.clarificationAttempted(),
                trace.clarificationOutcome(),
                trace.clarificationPendingStateConsumed(),
                trace.clarificationQuestionAsked(),
                trace.originalQuery(),
                trace.retrievalQuery(),
                trace.packedContextPreview(),
                trace.sourceCount(),
                trace.retrievedDocumentNames(),
                trace.answerGroundingPolicy(),
                trace.promptContextCharCount(),
                trace.abstentionTriggered(),
                trace.abstentionReason());
    }
}
