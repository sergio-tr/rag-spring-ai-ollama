package com.uniovi.rag.application.service.runtime;

import com.uniovi.rag.domain.runtime.engine.ExecutionStageOutcome;
import com.uniovi.rag.domain.runtime.engine.ExecutionStageTrace;
import com.uniovi.rag.domain.runtime.engine.ExecutionTrace;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Parses function-calling proposal stage messages into telemetry fields. */
public final class FunctionCallingTelemetrySupport {

    private FunctionCallingTelemetrySupport() {}

    static Map<String, Object> proposalFieldsFromTrace(ExecutionTrace trace) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (trace == null) {
            return m;
        }
        parseProposalStage(trace.stages(), m);
        return m;
    }

    static void parseProposalStage(List<ExecutionStageTrace> stages, Map<String, Object> target) {
        if (stages == null || stages.isEmpty()) {
            return;
        }
        for (ExecutionStageTrace stage : stages) {
            if (stage == null || !"function_calling_proposal".equals(stage.stageName())) {
                continue;
            }
            applyTokens(stage.message(), target);
        }
    }

    public static ExecutionStageTrace nativeProposalStage(boolean nativeAttempted, String functionName, String toolKind) {
        StringBuilder msg = new StringBuilder();
        msg.append("functionProposalMode=NATIVE_PROVIDER");
        msg.append(";functionProposalSource=NATIVE_PROVIDER");
        msg.append(";backendFunctionCallAttempted=false");
        msg.append(";nativeProviderFunctionCallAttempted=").append(nativeAttempted);
        if (functionName != null && !functionName.isBlank()) {
            msg.append(";functionCallName=").append(functionName);
        }
        if (toolKind != null && !toolKind.isBlank()) {
            msg.append(";functionToolKind=").append(toolKind);
        }
        return new ExecutionStageTrace("function_calling_proposal", 0L, ExecutionStageOutcome.SUCCESS, msg.toString());
    }

    private static void applyTokens(String message, Map<String, Object> target) {
        if (message == null || message.isBlank()) {
            return;
        }
        for (String part : message.split(";")) {
            int eq = part.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = part.substring(0, eq).trim();
            String value = part.substring(eq + 1).trim();
            if (key.isBlank()) {
                continue;
            }
            switch (key) {
                case "functionProposalValid" ->
                        target.put("functionProposalValid", Boolean.parseBoolean(value));
                case "functionProposalRepairAttempted" ->
                        target.put("functionProposalRepairAttempted", Boolean.parseBoolean(value));
                case "functionProposalRepairSucceeded" ->
                        target.put("functionProposalRepairSucceeded", Boolean.parseBoolean(value));
                case "backendFunctionCallAttempted" ->
                        target.put("backendFunctionCallAttempted", Boolean.parseBoolean(value));
                case "nativeProviderFunctionCallAttempted" ->
                        target.put("nativeProviderFunctionCallAttempted", Boolean.parseBoolean(value));
                case "functionProposalMode", "functionProposalSource", "functionCallName", "functionToolKind" ->
                        target.put(key, value);
                default -> {}
            }
        }
    }
}
