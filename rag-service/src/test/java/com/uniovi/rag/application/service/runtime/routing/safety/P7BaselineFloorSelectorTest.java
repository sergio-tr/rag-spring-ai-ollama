package com.uniovi.rag.application.service.runtime.routing.safety;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniovi.rag.application.service.runtime.routing.safety.P7BaselineFloorSelector.P7Decision;
import com.uniovi.rag.application.service.runtime.routing.safety.P7BaselineFloorSelector.P7WinnerKind;
import com.uniovi.rag.domain.evaluation.workbook.RagExperimentalPresetCode;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class P7BaselineFloorSelectorTest {

    @Test
    void keepsSafeP6WhenNativeToolIsNotDemonstrablyStronger() {
        var p6 =
                new P15BaselineFloorSelector.ParentBaseline(
                        RagExperimentalPresetCode.P6,
                        "PARENT_P6",
                        RouteCandidateValidationResult.accepted(0.9, "COMPLETE"));
        var weakerTool =
                new MonotonicRouteSafetyService.CandidateScore(
                        "TOOL",
                        RouteCandidateValidationResult.accepted(0.9, "COMPLETE"),
                        "wrong tool answer");

        P7Decision decision =
                P7BaselineFloorSelector.resolve(
                        Optional.of(p6), Optional.of(weakerTool), Optional.empty(), false);

        assertThat(decision.winner()).isEqualTo(P7WinnerKind.PARENT_P6);
        assertThat(decision.baselineCandidateSelected()).isTrue();
        assertThat(decision.baselineOverrideRejectedReason()).isEqualTo("native_not_stronger_than_baseline");
        assertThat(decision.monotonicFloorApplied()).isTrue();
    }

    @Test
    void keepsSafeP6WhenNativeRetrievalIsWeaker() {
        var p6 =
                new P15BaselineFloorSelector.ParentBaseline(
                        RagExperimentalPresetCode.P6,
                        "PARENT_P6",
                        RouteCandidateValidationResult.accepted(0.85, "TOPIC_COVERED"));
        var weakerRetrieval =
                new MonotonicRouteSafetyService.CandidateScore(
                        "RETRIEVAL",
                        RouteCandidateValidationResult.accepted(0.7, "PARTIAL"),
                        "weak");

        P7Decision decision =
                P7BaselineFloorSelector.resolve(
                        Optional.of(p6), Optional.empty(), Optional.of(weakerRetrieval), false);

        assertThat(decision.winner()).isEqualTo(P7WinnerKind.PARENT_P6);
        assertThat(decision.monotonicFloorPreventedRegression()).isTrue();
    }

    @Test
    void allowsDemonstrablyStrongerToolOverride() {
        var p6 =
                new P15BaselineFloorSelector.ParentBaseline(
                        RagExperimentalPresetCode.P6,
                        "PARENT_P6",
                        RouteCandidateValidationResult.accepted(0.7, "PARTIAL"));
        var tool =
                new MonotonicRouteSafetyService.CandidateScore(
                        "TOOL",
                        RouteCandidateValidationResult.accepted(0.9, "COMPLETE"),
                        "complete");

        P7Decision decision =
                P7BaselineFloorSelector.resolve(
                        Optional.of(p6), Optional.of(tool), Optional.empty(), false);

        assertThat(decision.winner()).isEqualTo(P7WinnerKind.TOOL);
        assertThat(decision.baselineOverrideAccepted()).isTrue();
    }

    @Test
    void selectsSafeP6OverAbstentionWhenBaselineExists() {
        var p6 =
                new P15BaselineFloorSelector.ParentBaseline(
                        RagExperimentalPresetCode.P6,
                        "PARENT_P6",
                        RouteCandidateValidationResult.accepted(0.8, "TOPIC_COVERED"));

        P7Decision decision =
                P7BaselineFloorSelector.resolve(Optional.of(p6), Optional.empty(), Optional.empty(), true);

        assertThat(decision.winner()).isEqualTo(P7WinnerKind.PARENT_P6);
        assertThat(decision.monotonicFloorApplied()).isTrue();
    }

    @Test
    void keepsSafeP6WhenNativeToolHasPartialListCoverage() {
        var p6 =
                new P15BaselineFloorSelector.ParentBaseline(
                        RagExperimentalPresetCode.P6,
                        "PARENT_P6",
                        RouteCandidateValidationResult.accepted(0.85, "TOPIC_COVERED"));
        var partialTool =
                new MonotonicRouteSafetyService.CandidateScore(
                        "TOOL",
                        RouteCandidateValidationResult.accepted(0.9, "PARTIAL"),
                        "partial list");

        P7Decision decision =
                P7BaselineFloorSelector.resolve(
                        Optional.of(p6), Optional.of(partialTool), Optional.empty(), false);

        assertThat(decision.winner()).isEqualTo(P7WinnerKind.PARENT_P6);
        assertThat(decision.baselineOverrideRejectedReason()).contains("native_not_constraint_complete");
        assertThat(decision.monotonicFloorPreventedRegression()).isTrue();
    }

    @Test
    void usesNativeWhenNoBaselineExists() {
        var tool =
                new MonotonicRouteSafetyService.CandidateScore(
                        "TOOL",
                        RouteCandidateValidationResult.accepted(0.9, "COMPLETE"),
                        "complete");

        P7Decision decision =
                P7BaselineFloorSelector.resolve(Optional.empty(), Optional.of(tool), Optional.empty(), false);

        assertThat(decision.winner()).isEqualTo(P7WinnerKind.TOOL);
    }
}
