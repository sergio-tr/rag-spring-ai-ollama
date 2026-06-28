package com.uniovi.rag.application.service.runtime.routing.safety;

import com.uniovi.rag.application.service.evaluation.preset.CampaignParentOutcome;
import com.uniovi.rag.application.service.runtime.RagExecutionOrchestrationSupport.ExecutionOutcome;
import com.uniovi.rag.domain.evaluation.workbook.RagExperimentalPresetCode;
import com.uniovi.rag.domain.runtime.engine.AnswerFinality;
import com.uniovi.rag.domain.runtime.engine.RagExecutionResult;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/** Frozen parent candidate contract captured at selection time. */
public final class ParentCandidateSnapshot {

    private final RagExperimentalPresetCode parentPresetCode;
    private final String parentFinalAnswerText;
    private final String parentMatcherVisibleAnswer;
    private final String parentFinalAnswerSource;
    private final String parentRouteDecision;
    private final List<String> parentEvidenceRefs;
    private final String parentSafetyStatus;
    private final String parentVerifierStatus;
    private final String parentConstraintCoverage;
    private final boolean campaignReplay;
    private final ExecutionOutcome outcome;

    private ParentCandidateSnapshot(
            RagExperimentalPresetCode parentPresetCode,
            String parentFinalAnswerText,
            String parentMatcherVisibleAnswer,
            String parentFinalAnswerSource,
            String parentRouteDecision,
            List<String> parentEvidenceRefs,
            String parentSafetyStatus,
            String parentVerifierStatus,
            String parentConstraintCoverage,
            boolean campaignReplay,
            ExecutionOutcome outcome) {
        this.parentPresetCode = Objects.requireNonNull(parentPresetCode, "parentPresetCode");
        this.parentFinalAnswerText = parentFinalAnswerText != null ? parentFinalAnswerText : "";
        this.parentMatcherVisibleAnswer =
                parentMatcherVisibleAnswer != null ? parentMatcherVisibleAnswer : "";
        this.parentFinalAnswerSource = parentFinalAnswerSource != null ? parentFinalAnswerSource : "";
        this.parentRouteDecision = parentRouteDecision != null ? parentRouteDecision : "";
        this.parentEvidenceRefs = parentEvidenceRefs != null ? List.copyOf(parentEvidenceRefs) : List.of();
        this.parentSafetyStatus = parentSafetyStatus != null ? parentSafetyStatus : "";
        this.parentVerifierStatus = parentVerifierStatus != null ? parentVerifierStatus : "";
        this.parentConstraintCoverage = parentConstraintCoverage != null ? parentConstraintCoverage : "";
        this.campaignReplay = campaignReplay;
        this.outcome = Objects.requireNonNull(outcome, "outcome");
    }

    public static ParentCandidateSnapshot capture(
            RagExperimentalPresetCode parentPresetCode,
            ExecutionOutcome outcome,
            Optional<CampaignParentOutcome> campaignRecord,
            boolean campaignReplay) {
        Objects.requireNonNull(outcome, "outcome");
        String answer = outcome.result().answerText();
        String matcherVisible = answer;
        String routeDecision = safe(outcome.trace().routingRouteKind());
        String finalSource = ParentFinalAnswerSources.forPreset(parentPresetCode);
        String constraintCoverage = "";
        String safetyStatus = "";
        String verifierStatus = "";
        if (campaignRecord.isPresent()) {
            CampaignParentOutcome record = campaignRecord.get();
            if (!record.answerText().isBlank()) {
                answer = record.answerText();
            }
            matcherVisible =
                    record.matcherVisibleAnswerText().isBlank()
                            ? answer
                            : record.matcherVisibleAnswerText();
            if (!record.routingRouteKind().isBlank()) {
                routeDecision = record.routingRouteKind();
            }
            constraintCoverage = record.constraintCoverageStatus();
            safetyStatus = record.parentSafetyStatus();
            verifierStatus = record.parentVerifierStatus();
        }
        List<String> evidenceRefs =
                campaignRecord.map(CampaignParentOutcome::evidenceRefs)
                        .filter(list -> !list.isEmpty())
                        .orElseGet(
                                () ->
                                        outcome.result().usedKnowledgeSnapshotIds().stream()
                                                .map(UUID::toString)
                                                .collect(Collectors.toList()));
        return new ParentCandidateSnapshot(
                parentPresetCode,
                answer,
                matcherVisible,
                finalSource,
                routeDecision,
                evidenceRefs,
                safetyStatus,
                verifierStatus,
                constraintCoverage,
                campaignReplay,
                outcome);
    }

    /** Legacy capture for unit tests without preset metadata. */
    public static ParentCandidateSnapshot capture(ExecutionOutcome outcome) {
        return capture(
                RagExperimentalPresetCode.P7,
                outcome,
                Optional.empty(),
                false);
    }

    public RagExperimentalPresetCode parentPresetCode() {
        return parentPresetCode;
    }

    public String parentFinalAnswerText() {
        return parentFinalAnswerText;
    }

    public String parentMatcherVisibleAnswer() {
        return parentMatcherVisibleAnswer;
    }

    public String parentFinalAnswerSource() {
        return parentFinalAnswerSource;
    }

    public String parentRouteDecision() {
        return parentRouteDecision;
    }

    public List<String> parentEvidenceRefs() {
        return parentEvidenceRefs;
    }

    public String parentSafetyStatus() {
        return parentSafetyStatus;
    }

    public String parentVerifierStatus() {
        return parentVerifierStatus;
    }

    public String parentConstraintCoverage() {
        return parentConstraintCoverage;
    }

    public boolean campaignReplay() {
        return campaignReplay;
    }

    public String answerText() {
        return parentFinalAnswerText;
    }

    public int answerLength() {
        return parentFinalAnswerText.length();
    }

    public String parentFinalAnswerHash() {
        return ParentAnswerFingerprint.sha256Hex(parentFinalAnswerText);
    }

    public String parentMatcherVisibleAnswerHash() {
        return ParentAnswerFingerprint.sha256Hex(parentMatcherVisibleAnswer);
    }

    public ExecutionOutcome toPreservedOutcome() {
        RagExecutionResult result = outcome.result();
        if (Objects.equals(result.answerText(), parentFinalAnswerText)) {
            return outcome;
        }
        return new ExecutionOutcome(
                new RagExecutionResult(
                        parentFinalAnswerText,
                        result.workflowName(),
                        result.retrievalUsed(),
                        result.metadataUsed(),
                        result.usedResolvedConfigSnapshotId(),
                        result.usedConfigHash(),
                        result.usedKnowledgeSnapshotIds(),
                        result.executionTrace(),
                        result.toolUsedLabel(),
                        result.resolvedQueryType(),
                        result.usedTool(),
                        result.workflowStageTraces(),
                        result.retrievalDiagnostics(),
                        result.responseSources(),
                        result.answerFinality()),
                outcome.trace());
    }

    private static String safe(String value) {
        return value != null ? value : "";
    }
}
