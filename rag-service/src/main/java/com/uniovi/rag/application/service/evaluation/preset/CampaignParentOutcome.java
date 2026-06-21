package com.uniovi.rag.application.service.evaluation.preset;

import com.uniovi.rag.application.service.runtime.routing.safety.ParentAnswerFingerprint;
import java.util.List;

/** Frozen campaign parent-preset answer captured when P6/P7 benchmark items complete. */
public record CampaignParentOutcome(
        String answerText,
        String workflowName,
        boolean retrievalUsed,
        String routingRouteKind,
        boolean abstentionTriggered,
        boolean usedTool,
        String toolUsedLabel,
        String finalAnswerSource,
        String constraintCoverageStatus,
        String parentSafetyStatus,
        String parentVerifierStatus,
        String datasetQuestionId,
        String presetCode,
        String matcherVisibleAnswerText,
        String finalAnswerHash,
        String matcherVisibleAnswerHash,
        List<String> evidenceRefs) {

    public static final String MISSING_PARENT_REJECTION = "parent_campaign_outcome_pending_or_missing";

    public CampaignParentOutcome {
        answerText = answerText != null ? answerText : "";
        workflowName = workflowName != null ? workflowName : "";
        routingRouteKind = routingRouteKind != null ? routingRouteKind : "";
        toolUsedLabel = toolUsedLabel != null ? toolUsedLabel : "";
        finalAnswerSource = finalAnswerSource != null ? finalAnswerSource : "";
        constraintCoverageStatus = constraintCoverageStatus != null ? constraintCoverageStatus : "";
        parentSafetyStatus = parentSafetyStatus != null ? parentSafetyStatus : "";
        parentVerifierStatus = parentVerifierStatus != null ? parentVerifierStatus : "";
        datasetQuestionId = datasetQuestionId != null ? datasetQuestionId : "";
        presetCode = presetCode != null ? presetCode : "";
        matcherVisibleAnswerText =
                matcherVisibleAnswerText != null && !matcherVisibleAnswerText.isBlank()
                        ? matcherVisibleAnswerText
                        : answerText;
        finalAnswerHash =
                finalAnswerHash != null && !finalAnswerHash.isBlank()
                        ? finalAnswerHash
                        : ParentAnswerFingerprint.sha256Hex(answerText);
        matcherVisibleAnswerHash =
                matcherVisibleAnswerHash != null && !matcherVisibleAnswerHash.isBlank()
                        ? matcherVisibleAnswerHash
                        : ParentAnswerFingerprint.sha256Hex(matcherVisibleAnswerText);
        evidenceRefs = evidenceRefs != null ? List.copyOf(evidenceRefs) : List.of();
    }

    public static CampaignParentOutcome capture(
            String presetCode,
            String datasetQuestionId,
            String answerText,
            String workflowName,
            boolean retrievalUsed,
            String routingRouteKind,
            boolean abstentionTriggered,
            boolean usedTool,
            String toolUsedLabel,
            String finalAnswerSource,
            String constraintCoverageStatus,
            String parentSafetyStatus,
            String parentVerifierStatus,
            List<String> evidenceRefs) {
        return new CampaignParentOutcome(
                answerText,
                workflowName,
                retrievalUsed,
                routingRouteKind,
                abstentionTriggered,
                usedTool,
                toolUsedLabel,
                finalAnswerSource,
                constraintCoverageStatus,
                parentSafetyStatus,
                parentVerifierStatus,
                datasetQuestionId,
                presetCode,
                answerText,
                "",
                "",
                evidenceRefs);
    }

    /** Backward-compatible constructor for tests and legacy call sites. */
    public CampaignParentOutcome(
            String answerText,
            String workflowName,
            boolean retrievalUsed,
            String routingRouteKind,
            boolean abstentionTriggered,
            boolean usedTool,
            String toolUsedLabel,
            String finalAnswerSource,
            String constraintCoverageStatus,
            String parentSafetyStatus,
            String parentVerifierStatus) {
        this(
                answerText,
                workflowName,
                retrievalUsed,
                routingRouteKind,
                abstentionTriggered,
                usedTool,
                toolUsedLabel,
                finalAnswerSource,
                constraintCoverageStatus,
                parentSafetyStatus,
                parentVerifierStatus,
                "",
                "",
                "",
                "",
                "",
                List.of());
    }

    /** Backward-compatible constructor for tests and legacy call sites. */
    public CampaignParentOutcome(
            String answerText,
            String workflowName,
            boolean retrievalUsed,
            String routingRouteKind,
            boolean abstentionTriggered,
            boolean usedTool,
            String toolUsedLabel) {
        this(
                answerText,
                workflowName,
                retrievalUsed,
                routingRouteKind,
                abstentionTriggered,
                usedTool,
                toolUsedLabel,
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                List.of());
    }
}
