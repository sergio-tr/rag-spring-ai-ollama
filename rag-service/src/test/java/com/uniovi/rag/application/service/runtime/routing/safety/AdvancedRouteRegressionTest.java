package com.uniovi.rag.application.service.runtime.routing.safety;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniovi.rag.application.service.runtime.routing.safety.P15BaselineFloorSelector.Decision;
import com.uniovi.rag.application.service.runtime.routing.safety.P15BaselineFloorSelector.ParentBaseline;
import com.uniovi.rag.application.service.runtime.routing.safety.P15BaselineFloorSelector.WinnerKind;
import com.uniovi.rag.domain.evaluation.workbook.RagExperimentalPresetCode;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Regression tests for advanced-route baseline floors (P8/P15 retrieval safety nets). */
class AdvancedRouteRegressionTest {

    @Test
    void p3SafetyNetKeepsParentWhenNativeRetrievalAbstains() {
        ParentBaseline p3 =
                new ParentBaseline(
                        RagExperimentalPresetCode.P3,
                        "PARENT_P3",
                        RouteCandidateValidationResult.accepted(0.82, "TOPIC_COVERED"));
        var abstainingNative =
                new MonotonicRouteSafetyService.CandidateScore(
                        "RETRIEVAL",
                        RouteCandidateValidationResult.accepted(0.9, "ABSTENTION"),
                        "No consta en el acta");

        AdvancedPresetBaselineFloorSelector.Decision advancedFloor =
                AdvancedPresetBaselineFloorSelector.resolveRetrievalFloor(
                        Optional.of(p3), Optional.empty(), Optional.of(abstainingNative), false);

        assertThat(advancedFloor.winner()).isEqualTo(AdvancedPresetBaselineFloorSelector.WinnerKind.PARENT_P3);
        assertThat(advancedFloor.monotonicFloorPreventedRegression()).isTrue();
    }

    @Test
    void p15FloorStillPrefersP7OverWeakerHybridRetrieval() {
        ParentBaseline p7 =
                new ParentBaseline(
                        RagExperimentalPresetCode.P7,
                        "PARENT_P7",
                        RouteCandidateValidationResult.accepted(0.88, "COMPLETE"));
        var weakerHybrid =
                new MonotonicRouteSafetyService.CandidateScore(
                        "RETRIEVAL",
                        RouteCandidateValidationResult.accepted(0.75, "PARTIAL"),
                        "respuesta parcial");

        Decision decision =
                P15BaselineFloorSelector.resolve(
                        Optional.of(p7),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.of(weakerHybrid),
                        false);

        assertThat(decision.winner()).isEqualTo(WinnerKind.PARENT_P7);
        assertThat(decision.baselineOverrideRejectedReason()).contains("native_not_constraint_complete");
    }
}
