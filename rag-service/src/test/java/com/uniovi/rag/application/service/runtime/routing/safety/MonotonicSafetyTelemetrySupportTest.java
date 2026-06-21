package com.uniovi.rag.application.service.runtime.routing.safety;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MonotonicSafetyTelemetrySupportTest {

    @Test
    void exportMap_populatesFallbackTelemetryFields() {
        MonotonicSafetyTelemetry telemetry =
                MonotonicSafetyTelemetry.create()
                        .candidateToolConsidered(true)
                        .toolCandidateRejected(true)
                        .parentFallbackUsed(true)
                        .parentFinalAnswerPreserved(true)
                        .selectedParentPreset("P7")
                        .monotonicRegressionPrevented(true)
                        .selectedCandidateSource("PARENT_P7")
                        .parentSelectedFinalAnswerLength(42)
                        .rejectCandidate("TOOL", "find_paragraph_hedged_answer")
                        .constraintCoverageStatus("FAILED");

        Map<String, Object> exported = MonotonicSafetyTelemetrySupport.exportMap(telemetry);

        assertThat(exported.get("parentFallbackUsed")).isEqualTo(true);
        assertThat(exported.get("parentFinalAnswerPreserved")).isEqualTo(true);
        assertThat(exported.get("parentPresetCode")).isEqualTo("P7");
        assertThat(exported.get("selectedCandidateSource")).isEqualTo("PARENT_P7");
        assertThat(exported.get("parentSelectedFinalAnswerLength")).isEqualTo(42);
        assertThat(exported.get("candidateRejectionReasons")).asString().contains("TOOL:find_paragraph_hedged_answer");
    }

    @Test
    void enrichFromStageMessage_restoresBooleanFlags() {
        String message =
                MonotonicSafetyTelemetrySupport.stageMessage(
                        MonotonicSafetyTelemetry.create()
                                .toolCandidateRejected(true)
                                .parentFallbackUsed(true)
                                .parentFinalAnswerPreserved(true)
                                .monotonicRegressionPrevented(true)
                                .rejectCandidate("TOOL", "filter_list_unsupported_abstention"));

        Map<String, Object> target = new LinkedHashMap<>();
        MonotonicSafetyTelemetrySupport.enrichFromStageMessage(target, message);

        assertThat(target.get("toolCandidateRejected")).isEqualTo(true);
        assertThat(target.get("parentFallbackUsed")).isEqualTo(true);
        assertThat(target.get("parentFinalAnswerPreserved")).isEqualTo(true);
        assertThat(target.get("monotonicRegressionPrevented")).isEqualTo(true);
        assertThat(target.get("candidateRejectionReasons")).asString().contains("filter_list_unsupported_abstention");
    }

    @Test
    void exportMap_populatesBaselineFloorTelemetryFields() {
        MonotonicSafetyTelemetry telemetry =
                MonotonicSafetyTelemetry.create()
                        .baselineCandidateSource("PARENT_P7")
                        .baselineCandidatePresetCode("P7")
                        .baselineCandidateSelected(true)
                        .baselineOverrideAttempted(true)
                        .baselineOverrideAccepted(false)
                        .baselineOverrideRejectedReason("native_not_stronger_than_baseline")
                        .monotonicFloorApplied(true)
                        .monotonicFloorPreventedRegression(true)
                        .baselineFloorReason("baseline_floor_kept_parent:native_not_stronger_than_baseline");

        Map<String, Object> exported = MonotonicSafetyTelemetrySupport.exportMap(telemetry);

        assertThat(exported.get("baselineCandidateSelected")).isEqualTo(true);
        assertThat(exported.get("monotonicFloorApplied")).isEqualTo(true);
        assertThat(exported.get("monotonicFloorPreventedRegression")).isEqualTo(true);
        assertThat(exported.get("baselineCandidateSource")).isEqualTo("PARENT_P7");
    }

    @Test
    void enrichFromStageMessage_restoresBaselineFloorFlags() {
        String message =
                MonotonicSafetyTelemetrySupport.stageMessage(
                        MonotonicSafetyTelemetry.create()
                                .baselineCandidateSelected(true)
                                .monotonicFloorApplied(true)
                                .monotonicFloorPreventedRegression(true)
                                .baselineCandidateSource("PARENT_P7")
                                .baselineFloorReason("baseline_floor_parent_selected"));

        Map<String, Object> target = new LinkedHashMap<>();
        MonotonicSafetyTelemetrySupport.enrichFromStageMessage(target, message);

        assertThat(target.get("baselineCandidateSelected")).isEqualTo(true);
        assertThat(target.get("monotonicFloorApplied")).isEqualTo(true);
        assertThat(target.get("monotonicFloorPreventedRegression")).isEqualTo(true);
        assertThat(target.get("baselineCandidateSource")).isEqualTo("PARENT_P7");
        assertThat(target.get("baselineFloorReason")).isEqualTo("baseline_floor_parent_selected");
    }
}
