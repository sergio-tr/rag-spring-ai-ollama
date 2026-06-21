package com.uniovi.rag.application.service.runtime.routing.safety;

import com.uniovi.rag.application.service.evaluation.preset.CampaignParentOutcome;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** Runtime-safe eligibility checks for campaign-recorded P6/P7 parent baselines. */
public final class P15ParentCandidateSafetyPolicy {

    private static final Set<String> VALID_FINAL_SOURCES =
            Set.of(
                    "TOOL_FINAL",
                    "FUNCTION_FINAL",
                    "GENERATED",
                    "DATE_GUARD_ABSTENTION",
                    "FORCED_ABSTENTION",
                    "DIRECT_LLM");

    private P15ParentCandidateSafetyPolicy() {}

    public static Optional<RouteCandidateValidationResult> trustedCampaignValidation(
            CampaignParentOutcome record) {
        if (record == null || record.answerText().isBlank()) {
            return Optional.empty();
        }
        RouteCandidateValidationResult validation =
                RouteCandidateValidationResult.accepted(
                        confidenceFromCoverage(record.constraintCoverageStatus()),
                        coverageLabel(record.constraintCoverageStatus()));
        if (!isBaselineEligible(Optional.of(record), validation)) {
            return Optional.empty();
        }
        return Optional.of(validation);
    }

    public static boolean isBaselineEligible(
            Optional<CampaignParentOutcome> campaignRecord, RouteCandidateValidationResult validation) {
        if (validation == null || !validation.safe()) {
            return false;
        }
        if (campaignRecord.isEmpty()) {
            return true;
        }
        CampaignParentOutcome record = campaignRecord.get();
        if (record.answerText() == null || record.answerText().isBlank()) {
            return false;
        }
        if ("UNSAFE".equalsIgnoreCase(record.parentSafetyStatus())) {
            return false;
        }
        if (isVerifierRejected(record.parentVerifierStatus())) {
            return false;
        }
        if (record.abstentionTriggered() && !isAbstentionFinalSource(record.finalAnswerSource())) {
            return false;
        }
        if (!hasValidFinalAnswerSource(record.finalAnswerSource())) {
            return false;
        }
        return !isUnsupportedPositive(record);
    }

    private static boolean isVerifierRejected(String verifierStatus) {
        if (verifierStatus == null || verifierStatus.isBlank()) {
            return false;
        }
        String normalized = verifierStatus.trim().toLowerCase(Locale.ROOT);
        return "false".equals(normalized) || "failed".equals(normalized) || "rejected".equals(normalized);
    }

    private static boolean hasValidFinalAnswerSource(String finalAnswerSource) {
        if (finalAnswerSource == null || finalAnswerSource.isBlank()) {
            return true;
        }
        return VALID_FINAL_SOURCES.contains(finalAnswerSource)
                || finalAnswerSource.startsWith("PARENT_P");
    }

    private static boolean isAbstentionFinalSource(String finalAnswerSource) {
        if (finalAnswerSource == null || finalAnswerSource.isBlank()) {
            return false;
        }
        return "DATE_GUARD_ABSTENTION".equals(finalAnswerSource)
                || "FORCED_ABSTENTION".equals(finalAnswerSource);
    }

    private static boolean isUnsupportedPositive(CampaignParentOutcome record) {
        if (!"NEGATIVE_OR_ABSTENTION".equalsIgnoreCase(record.constraintCoverageStatus())) {
            return false;
        }
        return !record.abstentionTriggered();
    }

    private static double confidenceFromCoverage(String coverage) {
        if (coverage == null || coverage.isBlank()) {
            return 0.75;
        }
        String normalized = coverage.toUpperCase(Locale.ROOT);
        if ("COMPLETE".equals(normalized) || "TOPIC_COVERED".equals(normalized)) {
            return 0.9;
        }
        if ("PARTIAL".equals(normalized)) {
            return 0.7;
        }
        return 0.75;
    }

    private static String coverageLabel(String coverage) {
        if (coverage == null || coverage.isBlank()) {
            return "COMPLETE";
        }
        return coverage.toUpperCase(Locale.ROOT);
    }
}
