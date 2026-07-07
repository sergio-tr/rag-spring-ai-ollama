package com.uniovi.rag.application.service.runtime.routing.safety;

import java.util.ArrayList;
import java.util.List;

public final class MonotonicSafetyTelemetry {

    private boolean candidateToolConsidered;
    private boolean candidateFunctionConsidered;
    private boolean candidateRetrievalConsidered;
    private boolean parentCandidateConsidered;
    private String selectedCandidateSource = "";
    private String selectedParentPreset = "";
    private final List<String> rejectedCandidateSources = new ArrayList<>();
    private final List<String> candidateRejectionReasons = new ArrayList<>();
    private boolean parentFallbackUsed;
    private boolean parentFinalAnswerPreserved;
    private boolean parentCampaignOutcomeReused;
    private boolean parentCampaignOutcomeMissing;
    private int parentSelectedFinalAnswerLength;
    private String parentFinalAnswerHash = "";
    private String selectedFinalAnswerHash = "";
    private String parentMatcherVisibleAnswerHash = "";
    private String selectedMatcherVisibleAnswerHash = "";
    private String parentAnswerMismatchReason = "";
    private boolean monotonicRegressionPrevented;
    private boolean toolCandidateRejected;
    private boolean functionCandidateRejected;
    private boolean retrievalCandidateRejected;
    private double routeConfidence;
    private String constraintCoverageStatus = "";
    private boolean baselineFloorApplied;
    private String baselineFloorReason = "";
    private String baselineCandidateSource = "";
    private String baselineCandidatePresetCode = "";
    private boolean baselineCandidateSelected;
    private boolean baselineOverrideAttempted;
    private boolean baselineOverrideAccepted;
    private String baselineOverrideRejectedReason = "";
    private boolean monotonicFloorApplied;
    private boolean monotonicFloorPreventedRegression;
    private boolean toolNegativeFallbackApplied;

    public static MonotonicSafetyTelemetry create() {
        return new MonotonicSafetyTelemetry();
    }

    public MonotonicSafetyTelemetry candidateToolConsidered(boolean v) {
        candidateToolConsidered = v;
        return this;
    }

    public MonotonicSafetyTelemetry candidateFunctionConsidered(boolean v) {
        candidateFunctionConsidered = v;
        return this;
    }

    public MonotonicSafetyTelemetry candidateRetrievalConsidered(boolean v) {
        candidateRetrievalConsidered = v;
        return this;
    }

    public MonotonicSafetyTelemetry parentCandidateConsidered(boolean v) {
        parentCandidateConsidered = v;
        return this;
    }

    public MonotonicSafetyTelemetry selectedCandidateSource(String v) {
        selectedCandidateSource = v == null ? "" : v;
        return this;
    }

    public MonotonicSafetyTelemetry selectedParentPreset(String v) {
        selectedParentPreset = v == null ? "" : v;
        return this;
    }

    public MonotonicSafetyTelemetry rejectCandidate(String source, String reason) {
        if (source != null && !source.isBlank()) {
            rejectedCandidateSources.add(source);
        }
        if (reason != null && !reason.isBlank()) {
            candidateRejectionReasons.add(source + ":" + reason);
        }
        return this;
    }

    public MonotonicSafetyTelemetry parentFallbackUsed(boolean v) {
        parentFallbackUsed = v;
        return this;
    }

    public MonotonicSafetyTelemetry parentFinalAnswerPreserved(boolean v) {
        parentFinalAnswerPreserved = v;
        return this;
    }

    public MonotonicSafetyTelemetry parentCampaignOutcomeReused(boolean v) {
        parentCampaignOutcomeReused = v;
        return this;
    }

    public MonotonicSafetyTelemetry parentCampaignOutcomeMissing(boolean v) {
        parentCampaignOutcomeMissing = v;
        return this;
    }

    public MonotonicSafetyTelemetry parentSelectedFinalAnswerLength(int v) {
        parentSelectedFinalAnswerLength = Math.max(0, v);
        return this;
    }

    public MonotonicSafetyTelemetry parentFinalAnswerHash(String v) {
        parentFinalAnswerHash = v == null ? "" : v;
        return this;
    }

    public MonotonicSafetyTelemetry selectedFinalAnswerHash(String v) {
        selectedFinalAnswerHash = v == null ? "" : v;
        return this;
    }

    public MonotonicSafetyTelemetry parentMatcherVisibleAnswerHash(String v) {
        parentMatcherVisibleAnswerHash = v == null ? "" : v;
        return this;
    }

    public MonotonicSafetyTelemetry selectedMatcherVisibleAnswerHash(String v) {
        selectedMatcherVisibleAnswerHash = v == null ? "" : v;
        return this;
    }

    public MonotonicSafetyTelemetry parentAnswerMismatchReason(String v) {
        parentAnswerMismatchReason = v == null ? "" : v;
        return this;
    }

    public MonotonicSafetyTelemetry monotonicRegressionPrevented(boolean v) {
        monotonicRegressionPrevented = v;
        return this;
    }

    public MonotonicSafetyTelemetry toolCandidateRejected(boolean v) {
        toolCandidateRejected = v;
        return this;
    }

    public MonotonicSafetyTelemetry functionCandidateRejected(boolean v) {
        functionCandidateRejected = v;
        return this;
    }

    public MonotonicSafetyTelemetry retrievalCandidateRejected(boolean v) {
        retrievalCandidateRejected = v;
        return this;
    }

    public MonotonicSafetyTelemetry routeConfidence(double v) {
        routeConfidence = v;
        return this;
    }

    public MonotonicSafetyTelemetry constraintCoverageStatus(String v) {
        constraintCoverageStatus = v == null ? "" : v;
        return this;
    }

    public MonotonicSafetyTelemetry baselineFloorApplied(boolean v) {
        baselineFloorApplied = v;
        return this;
    }

    public MonotonicSafetyTelemetry baselineFloorReason(String v) {
        baselineFloorReason = v == null ? "" : v;
        return this;
    }

    public MonotonicSafetyTelemetry baselineCandidateSource(String v) {
        baselineCandidateSource = v == null ? "" : v;
        return this;
    }

    public MonotonicSafetyTelemetry baselineCandidatePresetCode(String v) {
        baselineCandidatePresetCode = v == null ? "" : v;
        return this;
    }

    public MonotonicSafetyTelemetry baselineCandidateSelected(boolean v) {
        baselineCandidateSelected = v;
        return this;
    }

    public MonotonicSafetyTelemetry baselineOverrideAttempted(boolean v) {
        baselineOverrideAttempted = v;
        return this;
    }

    public MonotonicSafetyTelemetry baselineOverrideAccepted(boolean v) {
        baselineOverrideAccepted = v;
        return this;
    }

    public MonotonicSafetyTelemetry baselineOverrideRejectedReason(String v) {
        baselineOverrideRejectedReason = v == null ? "" : v;
        return this;
    }

    public MonotonicSafetyTelemetry monotonicFloorApplied(boolean v) {
        monotonicFloorApplied = v;
        baselineFloorApplied = v;
        return this;
    }

    public MonotonicSafetyTelemetry monotonicFloorPreventedRegression(boolean v) {
        monotonicFloorPreventedRegression = v;
        return this;
    }

    public MonotonicSafetyTelemetry toolNegativeFallbackApplied(boolean v) {
        toolNegativeFallbackApplied = v;
        return this;
    }

    public MonotonicSafetyTelemetry augmentFunctionRejectionWhenRetrievalSupported(
            RouteCandidateValidationResult functionValidation) {
        return augmentFunctionRejectionWhenAlternateSupported(functionValidation, "function_abstention_despite_supported_retrieval");
    }

    public MonotonicSafetyTelemetry augmentFunctionRejectionWhenParentSupported(
            RouteCandidateValidationResult functionValidation) {
        return augmentFunctionRejectionWhenAlternateSupported(functionValidation, "function_abstention_despite_supported_parent");
    }

    private MonotonicSafetyTelemetry augmentFunctionRejectionWhenAlternateSupported(
            RouteCandidateValidationResult functionValidation, String augmentationReason) {
        if (functionValidation == null || functionValidation.safe()) {
            return this;
        }
        boolean abstention =
                functionValidation.rejectionReasons().stream()
                        .anyMatch(
                                reason ->
                                        reason.equals("filter_list_unsupported_abstention")
                                                || reason.equals("function_sentinel_abstention")
                                                || reason.equals("filter_list_vague_absence")
                                                || reason.equals("function_abstention_despite_supported_retrieval")
                                                || reason.equals("function_abstention_despite_supported_parent"));
        if (abstention
                && functionValidation.rejectionReasons().stream().noneMatch(augmentationReason::equals)) {
            rejectCandidate("FUNCTION", augmentationReason);
        }
        return this;
    }

    public boolean candidateToolConsidered() {
        return candidateToolConsidered;
    }

    public boolean candidateFunctionConsidered() {
        return candidateFunctionConsidered;
    }

    public boolean candidateRetrievalConsidered() {
        return candidateRetrievalConsidered;
    }

    public boolean parentCandidateConsidered() {
        return parentCandidateConsidered;
    }

    public String selectedCandidateSource() {
        return selectedCandidateSource;
    }

    public String selectedParentPreset() {
        return selectedParentPreset;
    }

    public List<String> rejectedCandidateSources() {
        return List.copyOf(rejectedCandidateSources);
    }

    public List<String> candidateRejectionReasons() {
        return List.copyOf(candidateRejectionReasons);
    }

    public boolean parentFallbackUsed() {
        return parentFallbackUsed;
    }

    public boolean parentFinalAnswerPreserved() {
        return parentFinalAnswerPreserved;
    }

    public boolean parentCampaignOutcomeReused() {
        return parentCampaignOutcomeReused;
    }

    public boolean parentCampaignOutcomeMissing() {
        return parentCampaignOutcomeMissing;
    }

    public int parentSelectedFinalAnswerLength() {
        return parentSelectedFinalAnswerLength;
    }

    public String parentFinalAnswerHash() {
        return parentFinalAnswerHash;
    }

    public String selectedFinalAnswerHash() {
        return selectedFinalAnswerHash;
    }

    public String parentMatcherVisibleAnswerHash() {
        return parentMatcherVisibleAnswerHash;
    }

    public String selectedMatcherVisibleAnswerHash() {
        return selectedMatcherVisibleAnswerHash;
    }

    public String parentAnswerMismatchReason() {
        return parentAnswerMismatchReason;
    }

    public boolean monotonicRegressionPrevented() {
        return monotonicRegressionPrevented;
    }

    public boolean toolCandidateRejected() {
        return toolCandidateRejected;
    }

    public boolean functionCandidateRejected() {
        return functionCandidateRejected;
    }

    public boolean retrievalCandidateRejected() {
        return retrievalCandidateRejected;
    }

    public double routeConfidence() {
        return routeConfidence;
    }

    public String constraintCoverageStatus() {
        return constraintCoverageStatus;
    }

    public boolean baselineFloorApplied() {
        return baselineFloorApplied;
    }

    public String baselineFloorReason() {
        return baselineFloorReason;
    }

    public String baselineCandidateSource() {
        return baselineCandidateSource;
    }

    public String baselineCandidatePresetCode() {
        return baselineCandidatePresetCode;
    }

    public boolean baselineCandidateSelected() {
        return baselineCandidateSelected;
    }

    public boolean baselineOverrideAttempted() {
        return baselineOverrideAttempted;
    }

    public boolean baselineOverrideAccepted() {
        return baselineOverrideAccepted;
    }

    public String baselineOverrideRejectedReason() {
        return baselineOverrideRejectedReason;
    }

    public boolean monotonicFloorApplied() {
        return monotonicFloorApplied;
    }

    public boolean monotonicFloorPreventedRegression() {
        return monotonicFloorPreventedRegression;
    }

    public boolean toolNegativeFallbackApplied() {
        return toolNegativeFallbackApplied;
    }
}
