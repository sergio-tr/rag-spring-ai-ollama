package com.uniovi.rag.application.service.runtime.routing.safety;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniovi.rag.domain.evaluation.workbook.RagExperimentalPresetCode;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AdvancedPresetBaselineFloorSelectorTest {

    @Test
    void keepsP3WhenNativeUnsafe() {
        P15BaselineFloorSelector.ParentBaseline p3 =
                new P15BaselineFloorSelector.ParentBaseline(
                        RagExperimentalPresetCode.P3,
                        "PARENT_P3",
                        RouteCandidateValidationResult.accepted(0.9, "COMPLETE"));

        var decision =
                AdvancedPresetBaselineFloorSelector.resolveRetrievalFloor(
                        Optional.of(p3), Optional.empty(), Optional.empty(), true);

        assertThat(decision.winner()).isEqualTo(AdvancedPresetBaselineFloorSelector.WinnerKind.PARENT_P3);
        assertThat(decision.baselineCandidateSelected()).isTrue();
        assertThat(decision.monotonicFloorPreventedRegression()).isTrue();
    }

    @Test
    void nativeOverridesP3WhenDemonstrablyStronger() {
        P15BaselineFloorSelector.ParentBaseline p3 =
                new P15BaselineFloorSelector.ParentBaseline(
                        RagExperimentalPresetCode.P3,
                        "PARENT_P3",
                        RouteCandidateValidationResult.accepted(0.7, "COMPLETE"));
        MonotonicRouteSafetyService.CandidateScore nativeWinner =
                new MonotonicRouteSafetyService.CandidateScore(
                        "RETRIEVAL",
                        RouteCandidateValidationResult.accepted(0.95, "COMPLETE"),
                        "answer with full grounding");

        var decision =
                AdvancedPresetBaselineFloorSelector.resolveRetrievalFloor(
                        Optional.of(p3), Optional.empty(), Optional.of(nativeWinner), false);

        assertThat(decision.winner()).isEqualTo(AdvancedPresetBaselineFloorSelector.WinnerKind.NATIVE_RETRIEVAL);
        assertThat(decision.baselineOverrideAccepted()).isTrue();
    }

    @Test
    void prefersP5OverP3WhenBothPresentAndNativeAbstains() {
        P15BaselineFloorSelector.ParentBaseline p3 =
                new P15BaselineFloorSelector.ParentBaseline(
                        RagExperimentalPresetCode.P3,
                        "PARENT_P3",
                        RouteCandidateValidationResult.accepted(0.8, "COMPLETE"));
        P15BaselineFloorSelector.ParentBaseline p5 =
                new P15BaselineFloorSelector.ParentBaseline(
                        RagExperimentalPresetCode.P5,
                        "PARENT_P5",
                        RouteCandidateValidationResult.accepted(0.85, "COMPLETE"));

        var decision =
                AdvancedPresetBaselineFloorSelector.resolveRetrievalFloor(
                        Optional.of(p3), Optional.of(p5), Optional.empty(), true);

        assertThat(decision.winner()).isEqualTo(AdvancedPresetBaselineFloorSelector.WinnerKind.PARENT_P5);
    }
}
