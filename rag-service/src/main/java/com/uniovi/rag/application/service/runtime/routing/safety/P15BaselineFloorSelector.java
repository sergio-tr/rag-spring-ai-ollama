package com.uniovi.rag.application.service.runtime.routing.safety;

import com.uniovi.rag.domain.evaluation.workbook.RagExperimentalPresetCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** P15 integrated-route policy: safe P7 campaign parent is the default floor unless native is demonstrably stronger. */
public final class P15BaselineFloorSelector {

    public enum WinnerKind {
        PARENT_P7,
        PARENT_P6,
        PARENT_P3,
        FUNCTION,
        TOOL,
        RETRIEVAL,
        ABSTENTION
    }

    public record ParentBaseline(
            RagExperimentalPresetCode preset,
            String source,
            RouteCandidateValidationResult validation) {}

    public record Decision(
            WinnerKind winner,
            Optional<ParentBaseline> baseline,
            Optional<MonotonicRouteSafetyService.CandidateScore> nativeWinner,
            boolean baselineCandidateSelected,
            boolean baselineOverrideAttempted,
            boolean baselineOverrideAccepted,
            String baselineOverrideRejectedReason,
            boolean monotonicFloorApplied,
            boolean monotonicFloorPreventedRegression) {}

    private P15BaselineFloorSelector() {}

    public static Decision resolve(
            Optional<ParentBaseline> p7Baseline,
            Optional<ParentBaseline> p6Baseline,
            Optional<MonotonicRouteSafetyService.CandidateScore> function,
            Optional<MonotonicRouteSafetyService.CandidateScore> tool,
            Optional<MonotonicRouteSafetyService.CandidateScore> retrieval,
            boolean abstentionRequired) {
        Optional<ParentBaseline> baseline = selectBaseline(p7Baseline, p6Baseline);
        Optional<MonotonicRouteSafetyService.CandidateScore> strongestNative =
                strongestSafeNative(tool, function, retrieval);

        if (baseline.isPresent()) {
            ParentBaseline floor = baseline.get();
            if (function.filter(candidate -> candidate.validation().safe()).isPresent()) {
                return parentDecision(
                        floor,
                        function,
                        true,
                        false,
                        "function_superseded_by_supported_parent",
                        true);
            }
            if (strongestNative.isPresent()) {
                Optional<String> rejection = overrideRejectionReason(strongestNative.get(), floor.validation());
                if (rejection.isEmpty()) {
                    return nativeDecision(strongestNative.get(), baseline, true, true, "", false);
                }
                return parentDecision(floor, strongestNative, true, false, rejection.get(), true);
            }
            if (abstentionRequired) {
                return parentDecision(floor, Optional.empty(), false, false, "", true);
            }
            return parentDecision(floor, Optional.empty(), false, false, "", false);
        }

        if (strongestNative.isPresent()) {
            return nativeDecision(strongestNative.get(), Optional.empty(), false, false, "", false);
        }
        return new Decision(
                WinnerKind.ABSTENTION,
                Optional.empty(),
                Optional.empty(),
                false,
                false,
                false,
                "",
                false,
                false);
    }

    private static Optional<ParentBaseline> selectBaseline(
            Optional<ParentBaseline> p7Baseline, Optional<ParentBaseline> p6Baseline) {
        if (p7Baseline.isPresent()) {
            return p7Baseline;
        }
        return p6Baseline;
    }

    public static boolean nativeMayOverrideBaseline(
            MonotonicRouteSafetyService.CandidateScore nativeCandidate,
            RouteCandidateValidationResult baselineValidation) {
        return overrideRejectionReason(nativeCandidate, baselineValidation).isEmpty();
    }

    public static boolean isDemonstrablyStrongNative(
            MonotonicRouteSafetyService.CandidateScore nativeCandidate) {
        if (nativeCandidate == null || !nativeCandidate.validation().safe()) {
            return false;
        }
        if (isAbstentionLike(nativeCandidate)) {
            return false;
        }
        if (!isConstraintComplete(nativeCandidate.validation())) {
            return false;
        }
        if (isPartialList(nativeCandidate)) {
            return false;
        }
        return !hasNegativeEvidenceRisk(nativeCandidate);
    }

    public static Optional<String> overrideRejectionReason(
            MonotonicRouteSafetyService.CandidateScore nativeCandidate,
            RouteCandidateValidationResult baselineValidation) {
        if (baselineValidation == null || !baselineValidation.safe()) {
            return Optional.empty();
        }
        if (nativeCandidate == null || !nativeCandidate.validation().safe()) {
            return Optional.of("native_unsafe");
        }
        if (isAbstentionLike(nativeCandidate)) {
            return Optional.of("native_abstention_over_supported_parent");
        }
        if (!isConstraintComplete(nativeCandidate.validation())) {
            return Optional.of("native_not_constraint_complete");
        }
        if (isPartialList(nativeCandidate)) {
            return Optional.of("native_partial_list");
        }
        if (hasNegativeEvidenceRisk(nativeCandidate)) {
            return Optional.of("native_negative_evidence_risk");
        }
        if (nativeCandidate.validation().confidence() <= baselineValidation.confidence()) {
            return Optional.of("native_not_stronger_than_baseline");
        }
        return Optional.empty();
    }

    public static Optional<MonotonicRouteSafetyService.CandidateScore> strongestSafeNative(
            Optional<MonotonicRouteSafetyService.CandidateScore> tool,
            Optional<MonotonicRouteSafetyService.CandidateScore> function,
            Optional<MonotonicRouteSafetyService.CandidateScore> retrieval) {
        List<MonotonicRouteSafetyService.CandidateScore> safe = new ArrayList<>();
        tool.filter(c -> c.validation().safe()).ifPresent(safe::add);
        function.filter(c -> c.validation().safe()).ifPresent(safe::add);
        retrieval.filter(c -> c.validation().safe()).ifPresent(safe::add);
        if (safe.isEmpty()) {
            return Optional.empty();
        }
        return safe.stream()
                .max((a, b) -> Double.compare(a.validation().confidence(), b.validation().confidence()));
    }

    public static Optional<ParentBaseline> toBaselineFromValidation(
            RagExperimentalPresetCode preset, String source, RouteCandidateValidationResult validation) {
        if (preset == null || validation == null) {
            return Optional.empty();
        }
        return Optional.of(new ParentBaseline(preset, source, validation));
    }

    private static Decision parentDecision(
            ParentBaseline floor,
            Optional<MonotonicRouteSafetyService.CandidateScore> nativeAttempt,
            boolean overrideAttempted,
            boolean overrideAccepted,
            String overrideRejectedReason,
            boolean preventedRegression) {
        WinnerKind kind = winnerKindForPreset(floor.preset());
        return new Decision(
                kind,
                Optional.of(floor),
                nativeAttempt,
                true,
                overrideAttempted,
                overrideAccepted,
                overrideRejectedReason == null ? "" : overrideRejectedReason,
                true,
                preventedRegression || overrideAttempted);
    }

    private static Decision nativeDecision(
            MonotonicRouteSafetyService.CandidateScore nativeWinner,
            Optional<ParentBaseline> baseline,
            boolean overrideAttempted,
            boolean overrideAccepted,
            String overrideRejectedReason,
            boolean preventedRegression) {
        WinnerKind kind =
                switch (nativeWinner.source()) {
                    case "FUNCTION" -> WinnerKind.FUNCTION;
                    case "TOOL" -> WinnerKind.TOOL;
                    default -> WinnerKind.RETRIEVAL;
                };
        return new Decision(
                kind,
                baseline,
                Optional.of(nativeWinner),
                false,
                overrideAttempted,
                overrideAccepted,
                overrideRejectedReason == null ? "" : overrideRejectedReason,
                false,
                preventedRegression);
    }

    private static boolean isConstraintComplete(RouteCandidateValidationResult validation) {
        String coverage = validation.constraintCoverageStatus();
        if (coverage == null || coverage.isBlank()) {
            return false;
        }
        String normalized = coverage.toUpperCase(Locale.ROOT);
        return "COMPLETE".equals(normalized) || "TOPIC_COVERED".equals(normalized);
    }

    private static boolean isAbstentionLike(MonotonicRouteSafetyService.CandidateScore candidate) {
        String coverage = candidate.validation().constraintCoverageStatus();
        return "ABSTENTION".equalsIgnoreCase(coverage)
                || "NEGATIVE_OR_ABSTENTION".equalsIgnoreCase(coverage);
    }

    private static boolean isPartialList(MonotonicRouteSafetyService.CandidateScore candidate) {
        return "PARTIAL".equalsIgnoreCase(candidate.validation().constraintCoverageStatus());
    }

    private static WinnerKind winnerKindForPreset(RagExperimentalPresetCode preset) {
        if (preset == RagExperimentalPresetCode.P6) {
            return WinnerKind.PARENT_P6;
        }
        if (preset == RagExperimentalPresetCode.P3) {
            return WinnerKind.PARENT_P3;
        }
        return WinnerKind.PARENT_P7;
    }

    private static boolean hasNegativeEvidenceRisk(MonotonicRouteSafetyService.CandidateScore candidate) {
        return candidate.validation().rejectionReasons().stream()
                .anyMatch(
                        reason ->
                                reason.contains("absence_query_concrete_affirmation")
                                        || reason.contains("unsupported")
                                        || reason.contains("negative"));
    }
}
