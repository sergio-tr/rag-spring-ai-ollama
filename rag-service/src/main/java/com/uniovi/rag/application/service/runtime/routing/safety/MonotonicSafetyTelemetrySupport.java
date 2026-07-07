package com.uniovi.rag.application.service.runtime.routing.safety;

import com.uniovi.rag.domain.runtime.engine.ExecutionStageOutcome;
import com.uniovi.rag.domain.runtime.engine.ExecutionStageTrace;
import com.uniovi.rag.domain.runtime.engine.ExecutionTrace;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Serializes monotonic-safety telemetry into trace stages and export maps. */
public final class MonotonicSafetyTelemetrySupport {

    public static final String STAGE_NAME = "monotonic_safety";

    private MonotonicSafetyTelemetrySupport() {}

    public static String stageMessage(MonotonicSafetyTelemetry telemetry) {
        List<String> parts = new ArrayList<>();
        put(parts, "candidateToolConsidered", telemetry.candidateToolConsidered());
        put(parts, "candidateFunctionConsidered", telemetry.candidateFunctionConsidered());
        put(parts, "candidateRetrievalConsidered", telemetry.candidateRetrievalConsidered());
        put(parts, "parentCandidateConsidered", telemetry.parentCandidateConsidered());
        put(parts, "selectedCandidateSource", telemetry.selectedCandidateSource());
        put(parts, "selectedParentPreset", telemetry.selectedParentPreset());
        put(parts, "rejectedCandidateSources", String.join(",", telemetry.rejectedCandidateSources()));
        put(parts, "candidateRejectionReasons", String.join("|", telemetry.candidateRejectionReasons()));
        put(parts, "parentFallbackUsed", telemetry.parentFallbackUsed());
        put(parts, "parentFinalAnswerPreserved", telemetry.parentFinalAnswerPreserved());
        put(parts, "parentCampaignOutcomeReused", telemetry.parentCampaignOutcomeReused());
        put(parts, "parentCampaignOutcomeMissing", telemetry.parentCampaignOutcomeMissing());
        put(parts, "parentSelectedFinalAnswerLength", telemetry.parentSelectedFinalAnswerLength());
        put(parts, "parentFinalAnswerHash", telemetry.parentFinalAnswerHash());
        put(parts, "selectedFinalAnswerHash", telemetry.selectedFinalAnswerHash());
        put(parts, "parentMatcherVisibleAnswerHash", telemetry.parentMatcherVisibleAnswerHash());
        put(parts, "selectedMatcherVisibleAnswerHash", telemetry.selectedMatcherVisibleAnswerHash());
        put(parts, "parentAnswerMismatchReason", telemetry.parentAnswerMismatchReason());
        put(parts, "parentPresetCode", telemetry.selectedParentPreset());
        put(parts, "monotonicRegressionPrevented", telemetry.monotonicRegressionPrevented());
        put(parts, "toolCandidateRejected", telemetry.toolCandidateRejected());
        put(parts, "functionCandidateRejected", telemetry.functionCandidateRejected());
        put(parts, "retrievalCandidateRejected", telemetry.retrievalCandidateRejected());
        put(parts, "routeConfidence", telemetry.routeConfidence());
        put(parts, "constraintCoverageStatus", telemetry.constraintCoverageStatus());
        put(parts, "baselineFloorApplied", telemetry.baselineFloorApplied());
        put(parts, "baselineFloorReason", telemetry.baselineFloorReason());
        put(parts, "baselineCandidateSource", telemetry.baselineCandidateSource());
        put(parts, "baselineCandidatePresetCode", telemetry.baselineCandidatePresetCode());
        put(parts, "baselineCandidateSelected", telemetry.baselineCandidateSelected());
        put(parts, "baselineOverrideAttempted", telemetry.baselineOverrideAttempted());
        put(parts, "baselineOverrideAccepted", telemetry.baselineOverrideAccepted());
        put(parts, "baselineOverrideRejectedReason", telemetry.baselineOverrideRejectedReason());
        put(parts, "monotonicFloorApplied", telemetry.monotonicFloorApplied());
        put(parts, "monotonicFloorPreventedRegression", telemetry.monotonicFloorPreventedRegression());
        put(parts, "toolNegativeFallbackApplied", telemetry.toolNegativeFallbackApplied());
        return String.join(" ", parts);
    }

    public static ExecutionTrace withLeadingMonotonicSafetyStage(
            ExecutionTrace trace, MonotonicSafetyTelemetry telemetry) {
        ExecutionStageTrace stage =
                new ExecutionStageTrace(
                        STAGE_NAME,
                        0L,
                        ExecutionStageOutcome.SUCCESS,
                        stageMessage(telemetry));
        List<ExecutionStageTrace> merged = new ArrayList<>();
        merged.add(stage);
        if (trace.stages() != null) {
            trace.stages().stream()
                    .filter(s -> !STAGE_NAME.equals(s.stageName()))
                    .forEach(merged::add);
        }
        return trace.replacingStages(merged);
    }

    public static void enrichFromStageMessage(Map<String, Object> target, String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        for (String token : message.split("\\s+")) {
            int eq = token.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = token.substring(0, eq);
            String value = token.substring(eq + 1);
            switch (key) {
                case "parentFallbackUsed",
                        "parentFinalAnswerPreserved",
                        "parentCampaignOutcomeReused",
                        "parentCampaignOutcomeMissing",
                        "monotonicRegressionPrevented",
                        "toolCandidateRejected",
                        "functionCandidateRejected",
                        "retrievalCandidateRejected",
                        "candidateToolConsidered",
                        "candidateFunctionConsidered",
                        "candidateRetrievalConsidered",
                        "parentCandidateConsidered",
                        "baselineFloorApplied",
                        "baselineCandidateSelected",
                        "baselineOverrideAttempted",
                        "baselineOverrideAccepted",
                        "monotonicFloorApplied",
                        "monotonicFloorPreventedRegression",
                        "toolNegativeFallbackApplied" ->
                        target.put(key, Boolean.parseBoolean(value));
                case "routeConfidence" -> {
                    try {
                        target.put(key, Double.parseDouble(value));
                    } catch (NumberFormatException ignored) {
                        target.put(key, value);
                    }
                }
                case "parentSelectedFinalAnswerLength" -> {
                    try {
                        target.put(key, Integer.parseInt(value));
                    } catch (NumberFormatException ignored) {
                        target.put(key, value);
                    }
                }
                default -> target.put(key, value);
            }
        }
    }

    public static Map<String, Object> exportMap(MonotonicSafetyTelemetry telemetry) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("candidateToolConsidered", telemetry.candidateToolConsidered());
        m.put("candidateFunctionConsidered", telemetry.candidateFunctionConsidered());
        m.put("candidateRetrievalConsidered", telemetry.candidateRetrievalConsidered());
        m.put("parentCandidateConsidered", telemetry.parentCandidateConsidered());
        m.put("selectedCandidateSource", telemetry.selectedCandidateSource());
        m.put("selectedParentPreset", telemetry.selectedParentPreset());
        m.put("rejectedCandidateSources", telemetry.rejectedCandidateSources());
        m.put("candidateRejectionReasons", telemetry.candidateRejectionReasons());
        m.put("parentFallbackUsed", telemetry.parentFallbackUsed());
        m.put("parentFinalAnswerPreserved", telemetry.parentFinalAnswerPreserved());
        m.put("parentCampaignOutcomeReused", telemetry.parentCampaignOutcomeReused());
        m.put("parentCampaignOutcomeMissing", telemetry.parentCampaignOutcomeMissing());
        m.put("parentSelectedFinalAnswerLength", telemetry.parentSelectedFinalAnswerLength());
        m.put("parentFinalAnswerHash", telemetry.parentFinalAnswerHash());
        m.put("selectedFinalAnswerHash", telemetry.selectedFinalAnswerHash());
        m.put("parentMatcherVisibleAnswerHash", telemetry.parentMatcherVisibleAnswerHash());
        m.put("selectedMatcherVisibleAnswerHash", telemetry.selectedMatcherVisibleAnswerHash());
        m.put("parentAnswerMismatchReason", telemetry.parentAnswerMismatchReason());
        m.put("parentPresetCode", telemetry.selectedParentPreset());
        m.put("monotonicRegressionPrevented", telemetry.monotonicRegressionPrevented());
        m.put("toolCandidateRejected", telemetry.toolCandidateRejected());
        m.put("functionCandidateRejected", telemetry.functionCandidateRejected());
        m.put("retrievalCandidateRejected", telemetry.retrievalCandidateRejected());
        m.put("routeConfidence", telemetry.routeConfidence());
        m.put("constraintCoverageStatus", telemetry.constraintCoverageStatus());
        m.put("baselineFloorApplied", telemetry.baselineFloorApplied());
        m.put("baselineFloorReason", telemetry.baselineFloorReason());
        m.put("baselineCandidateSource", telemetry.baselineCandidateSource());
        m.put("baselineCandidatePresetCode", telemetry.baselineCandidatePresetCode());
        m.put("baselineCandidateSelected", telemetry.baselineCandidateSelected());
        m.put("baselineOverrideAttempted", telemetry.baselineOverrideAttempted());
        m.put("baselineOverrideAccepted", telemetry.baselineOverrideAccepted());
        m.put("baselineOverrideRejectedReason", telemetry.baselineOverrideRejectedReason());
        m.put("monotonicFloorApplied", telemetry.monotonicFloorApplied());
        m.put("monotonicFloorPreventedRegression", telemetry.monotonicFloorPreventedRegression());
        m.put("toolNegativeFallbackApplied", telemetry.toolNegativeFallbackApplied());
        return Map.copyOf(m);
    }

    private static void put(List<String> parts, String key, Object value) {
        if (value == null) {
            return;
        }
        String s = String.valueOf(value);
        if (!s.isBlank()) {
            parts.add(key + "=" + s);
        }
    }
}
