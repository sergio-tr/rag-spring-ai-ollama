package com.uniovi.rag.application.service.runtime.routing.safety;

import com.uniovi.rag.domain.evaluation.workbook.RagExperimentalPresetCode;
import java.util.Optional;

/**
 * Baseline floor for advanced retrieval presets (P6/P8–P10): prefer safe P3 chunk-dense parent
 * unless native retrieval is demonstrably stronger.
 */
public final class AdvancedPresetBaselineFloorSelector {

    public enum WinnerKind {
        PARENT_P3,
        PARENT_P5,
        NATIVE_RETRIEVAL,
        ABSTENTION
    }

    public record Decision(
            WinnerKind winner,
            Optional<P15BaselineFloorSelector.ParentBaseline> baseline,
            Optional<MonotonicRouteSafetyService.CandidateScore> nativeWinner,
            boolean baselineCandidateSelected,
            boolean baselineOverrideAttempted,
            boolean baselineOverrideAccepted,
            String baselineOverrideRejectedReason,
            boolean monotonicFloorApplied,
            boolean monotonicFloorPreventedRegression) {}

    private AdvancedPresetBaselineFloorSelector() {}

    public static Decision resolveRetrievalFloor(
            Optional<P15BaselineFloorSelector.ParentBaseline> p3Baseline,
            Optional<P15BaselineFloorSelector.ParentBaseline> p5Baseline,
            Optional<MonotonicRouteSafetyService.CandidateScore> nativeRetrieval,
            boolean abstentionRequired) {
        Optional<P15BaselineFloorSelector.ParentBaseline> baseline = selectBaseline(p3Baseline, p5Baseline);
        if (baseline.isPresent()) {
            P15BaselineFloorSelector.ParentBaseline floor = baseline.get();
            if (nativeRetrieval.isPresent()) {
                Optional<String> rejection =
                        P15BaselineFloorSelector.overrideRejectionReason(
                                nativeRetrieval.get(), floor.validation());
                if (rejection.isEmpty()) {
                    return nativeDecision(nativeRetrieval.get(), baseline, true, true, "", false);
                }
                return parentDecision(floor, nativeRetrieval, true, false, rejection.get(), true);
            }
            if (abstentionRequired) {
                return parentDecision(floor, Optional.empty(), false, false, "", true);
            }
            return parentDecision(floor, Optional.empty(), false, false, "", false);
        }
        if (nativeRetrieval.isPresent()) {
            return nativeDecision(nativeRetrieval.get(), Optional.empty(), false, false, "", false);
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

    public static Optional<P15BaselineFloorSelector.ParentBaseline> toP3Baseline(
            RouteCandidateValidationResult validation, String source) {
        return P15BaselineFloorSelector.toBaselineFromValidation(
                RagExperimentalPresetCode.P3, source != null ? source : "PARENT_P3", validation);
    }

    public static Optional<P15BaselineFloorSelector.ParentBaseline> toP5Baseline(
            RouteCandidateValidationResult validation, String source) {
        return P15BaselineFloorSelector.toBaselineFromValidation(
                RagExperimentalPresetCode.P5, source != null ? source : "PARENT_P5", validation);
    }

    private static Optional<P15BaselineFloorSelector.ParentBaseline> selectBaseline(
            Optional<P15BaselineFloorSelector.ParentBaseline> p3,
            Optional<P15BaselineFloorSelector.ParentBaseline> p5) {
        return p5.or(() -> p3);
    }

    private static Decision parentDecision(
            P15BaselineFloorSelector.ParentBaseline floor,
            Optional<MonotonicRouteSafetyService.CandidateScore> nativeAttempt,
            boolean overrideAttempted,
            boolean overrideAccepted,
            String overrideRejectedReason,
            boolean preventedRegression) {
        WinnerKind kind =
                floor.preset() == RagExperimentalPresetCode.P5 ? WinnerKind.PARENT_P5 : WinnerKind.PARENT_P3;
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
            Optional<P15BaselineFloorSelector.ParentBaseline> baseline,
            boolean overrideAttempted,
            boolean overrideAccepted,
            String overrideRejectedReason,
            boolean preventedRegression) {
        return new Decision(
                WinnerKind.NATIVE_RETRIEVAL,
                baseline,
                Optional.of(nativeWinner),
                false,
                overrideAttempted,
                overrideAccepted,
                overrideRejectedReason == null ? "" : overrideRejectedReason,
                false,
                preventedRegression);
    }
}
