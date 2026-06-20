package com.uniovi.rag.application.service.runtime.routing.safety;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniovi.rag.application.service.runtime.routing.safety.P15BaselineFloorSelector.Decision;
import com.uniovi.rag.application.service.runtime.routing.safety.P15BaselineFloorSelector.ParentBaseline;
import com.uniovi.rag.application.service.runtime.routing.safety.P15BaselineFloorSelector.WinnerKind;
import com.uniovi.rag.domain.evaluation.workbook.RagExperimentalPresetCode;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class P15BaselineFloorSelectorTest {

    @Test
    void keepsSafeP7WhenNativeRetrievalIsWeaker() {
        ParentBaseline p7 =
                new ParentBaseline(
                        RagExperimentalPresetCode.P7,
                        "PARENT_P7",
                        RouteCandidateValidationResult.accepted(0.85, "TOPIC_COVERED"));
        var weakerRetrieval =
                new MonotonicRouteSafetyService.CandidateScore(
                        "RETRIEVAL",
                        RouteCandidateValidationResult.accepted(0.7, "PARTIAL"),
                        "weak");

        Decision decision =
                P15BaselineFloorSelector.resolve(
                        Optional.of(p7),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.of(weakerRetrieval),
                        false);

        assertThat(decision.winner()).isEqualTo(WinnerKind.PARENT_P7);
        assertThat(decision.baselineCandidateSelected()).isTrue();
        assertThat(decision.baselineOverrideAttempted()).isTrue();
        assertThat(decision.baselineOverrideAccepted()).isFalse();
        assertThat(decision.monotonicFloorApplied()).isTrue();
    }

    @Test
    void selectsSafeP7OverAbstentionWhenBaselineExists() {
        ParentBaseline p7 =
                new ParentBaseline(
                        RagExperimentalPresetCode.P7,
                        "PARENT_P7",
                        RouteCandidateValidationResult.accepted(0.8, "TOPIC_COVERED"));

        Decision decision =
                P15BaselineFloorSelector.resolve(
                        Optional.of(p7),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        true);

        assertThat(decision.winner()).isEqualTo(WinnerKind.PARENT_P7);
        assertThat(decision.baselineCandidateSelected()).isTrue();
        assertThat(decision.monotonicFloorApplied()).isTrue();
    }

    @Test
    void rejectsAbstentionLikeNativeOverSupportedParent() {
        ParentBaseline p7 =
                new ParentBaseline(
                        RagExperimentalPresetCode.P7,
                        "PARENT_P7",
                        RouteCandidateValidationResult.accepted(0.8, "TOPIC_COVERED"));
        var abstentionNative =
                new MonotonicRouteSafetyService.CandidateScore(
                        "RETRIEVAL",
                        RouteCandidateValidationResult.accepted(0.85, "ABSTENTION"),
                        "No consta");

        Decision decision =
                P15BaselineFloorSelector.resolve(
                        Optional.of(p7),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.of(abstentionNative),
                        false);

        assertThat(decision.winner()).isEqualTo(WinnerKind.PARENT_P7);
        assertThat(decision.baselineOverrideRejectedReason()).contains("abstention");
    }

    @Test
    void keepsSafeP7WhenSafeFunctionIsPresentButNotStronger() {
        ParentBaseline p7 =
                new ParentBaseline(
                        RagExperimentalPresetCode.P7,
                        "PARENT_P7",
                        RouteCandidateValidationResult.accepted(0.7, "PARTIAL"));
        var function =
                new MonotonicRouteSafetyService.CandidateScore(
                        "FUNCTION",
                        RouteCandidateValidationResult.accepted(0.9, "TOPIC_COVERED"),
                        "weaker function");

        Decision decision =
                P15BaselineFloorSelector.resolve(
                        Optional.of(p7),
                        Optional.empty(),
                        Optional.of(function),
                        Optional.empty(),
                        Optional.empty(),
                        false);

        assertThat(decision.winner()).isEqualTo(WinnerKind.PARENT_P7);
        assertThat(decision.baselineOverrideRejectedReason())
                .isEqualTo("function_superseded_by_supported_parent");
    }

    @Test
    void allowsConstraintCompleteFunctionOverrideWithoutBaseline() {
        var function =
                new MonotonicRouteSafetyService.CandidateScore(
                        "FUNCTION",
                        RouteCandidateValidationResult.accepted(0.9, "TOPIC_COVERED"),
                        "complete");

        Decision decision =
                P15BaselineFloorSelector.resolve(
                        Optional.empty(),
                        Optional.empty(),
                        Optional.of(function),
                        Optional.empty(),
                        Optional.empty(),
                        false);

        assertThat(decision.winner()).isEqualTo(WinnerKind.FUNCTION);
    }

    @Test
    void allowsConstraintCompleteToolOverride() {
        ParentBaseline p7 =
                new ParentBaseline(
                        RagExperimentalPresetCode.P7,
                        "PARENT_P7",
                        RouteCandidateValidationResult.accepted(0.7, "PARTIAL"));
        var tool =
                new MonotonicRouteSafetyService.CandidateScore(
                        "TOOL",
                        RouteCandidateValidationResult.accepted(0.9, "TOPIC_COVERED"),
                        "complete");

        Decision decision =
                P15BaselineFloorSelector.resolve(
                        Optional.of(p7),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.of(tool),
                        Optional.empty(),
                        false);

        assertThat(decision.winner()).isEqualTo(WinnerKind.TOOL);
        assertThat(decision.baselineOverrideAccepted()).isTrue();
    }

    @Test
    void fallsBackToSafeP6WhenP7Unavailable() {
        ParentBaseline p6 =
                new ParentBaseline(
                        RagExperimentalPresetCode.P6,
                        "PARENT_P6",
                        RouteCandidateValidationResult.accepted(0.8, "TOPIC_COVERED"));

        Decision decision =
                P15BaselineFloorSelector.resolve(
                        Optional.empty(),
                        Optional.of(p6),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        true);

        assertThat(decision.winner()).isEqualTo(WinnerKind.PARENT_P6);
    }

    @Test
    void abstainsWhenNoBaselineAndNoSafeNative() {
        Decision decision =
                P15BaselineFloorSelector.resolve(
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        true);

        assertThat(decision.winner()).isEqualTo(WinnerKind.ABSTENTION);
    }
}
