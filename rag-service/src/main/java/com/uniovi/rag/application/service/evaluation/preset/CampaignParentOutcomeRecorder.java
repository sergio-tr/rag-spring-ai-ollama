package com.uniovi.rag.application.service.evaluation.preset;

import com.uniovi.rag.application.result.chat.QueryResponse;
import com.uniovi.rag.domain.evaluation.workbook.RagExperimentalPresetCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Records P6/P7 campaign answers for verbatim P15 parent replay during Lab benchmarks. */
public final class CampaignParentOutcomeRecorder {

    private CampaignParentOutcomeRecorder() {}

    public static void maybeRecord(String datasetQuestionId, QueryResponse queryResponse, String answerText) {
        if (datasetQuestionId == null
                || datasetQuestionId.isBlank()
                || answerText == null
                || queryResponse == null) {
            return;
        }
        String presetCode =
                LabBenchmarkExecutionContext.currentLabRuntimeContext()
                        .map(LabBenchmarkExecutionContext.LabRuntimeContext::presetCode)
                        .orElse(null);
        RagExperimentalPresetCode parentPreset = parentPresetFor(presetCode);
        if (parentPreset == null) {
            return;
        }
        Map<String, Object> telemetry = queryResponse.getChatTelemetry();
        boolean retrievalUsed = bool(telemetry.get("retrievalUsed"));
        boolean abstentionTriggered = bool(telemetry.get("abstentionTriggered"));
        boolean usedTool = bool(telemetry.get("toolExecuted"));
        String workflowName = str(telemetry.get("workflowName"));
        String routingRouteKind = str(telemetry.get("routingRouteKind"));
        String toolUsedLabel = str(telemetry.get("toolUsedLabel"));
        String finalAnswerSource = str(telemetry.get("finalAnswerSource"));
        String constraintCoverageStatus = str(telemetry.get("constraintCoverageStatus"));
        String parentSafetyStatus =
                bool(telemetry.get("monotonicRegressionPrevented")) ? "SAFE" : "";
        String parentVerifierStatus = str(telemetry.get("verifierPassed"));
        LabBenchmarkExecutionContext.recordCampaignParentOutcome(
                parentPreset.name(),
                datasetQuestionId,
                CampaignParentOutcome.capture(
                        parentPreset.name(),
                        datasetQuestionId,
                        answerText,
                        workflowName,
                        retrievalUsed,
                        routingRouteKind,
                        abstentionTriggered,
                        usedTool,
                        toolUsedLabel,
                        finalAnswerSource,
                        constraintCoverageStatus,
                        parentSafetyStatus,
                        parentVerifierStatus,
                        evidenceRefs(telemetry)));
    }

    private static List<String> evidenceRefs(Map<String, Object> telemetry) {
        if (telemetry == null || telemetry.isEmpty()) {
            return List.of();
        }
        List<String> refs = new ArrayList<>();
        Object raw = telemetry.get("usedKnowledgeSnapshotIds");
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                if (item != null) {
                    String s = String.valueOf(item).trim();
                    if (!s.isBlank()) {
                        refs.add(s);
                    }
                }
            }
        }
        return List.copyOf(refs);
    }

    private static RagExperimentalPresetCode parentPresetFor(String presetCode) {
        if (Objects.equals("P6", presetCode)) {
            return RagExperimentalPresetCode.P6;
        }
        if (Objects.equals("P7", presetCode)) {
            return RagExperimentalPresetCode.P7;
        }
        return null;
    }

    private static boolean bool(Object value) {
        return value instanceof Boolean b && b;
    }

    private static String str(Object value) {
        return value != null ? String.valueOf(value) : "";
    }
}
