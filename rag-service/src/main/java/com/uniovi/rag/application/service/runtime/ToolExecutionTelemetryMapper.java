package com.uniovi.rag.application.service.runtime;

import com.uniovi.rag.application.service.runtime.tool.DeterministicToolKindMappings;
import com.uniovi.rag.application.service.runtime.routing.safety.MonotonicSafetyTelemetrySupport;
import com.uniovi.rag.application.service.runtime.tool.DeterministicToolNegativeFallbackPolicy;
import com.uniovi.rag.configuration.ToolDescriptor;
import com.uniovi.rag.domain.runtime.engine.ExecutionStageTrace;
import com.uniovi.rag.domain.runtime.engine.ExecutionTrace;
import com.uniovi.rag.domain.runtime.functioncalling.FunctionCallingOutcome;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRouteKind;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolKind;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolOutcome;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Maps execution trace tool fields to Lab-safe telemetry. */
public final class ToolExecutionTelemetryMapper {

    private static final int MAX_RESULT_SUMMARY_CHARS = 512;

    private ToolExecutionTelemetryMapper() {}

    public static Map<String, Object> fromTrace(ExecutionTrace trace) {
        if (trace == null) {
            return Map.of();
        }
        Map<String, Object> m = new LinkedHashMap<>();

        String routeKind = safe(trace.routingRouteKind());
        boolean primaryWasDeterministicRoute =
                AdaptiveRouteKind.DETERMINISTIC_TOOL_ROUTE.name().equals(routeKind);
        String toolOutcome = safe(trace.deterministicToolOutcome());
        boolean terminalDeterministicTool =
                DeterministicToolOutcome.EXECUTED_SUCCESS.name().equals(toolOutcome)
                        && !trace.routingFallbackApplied();
        m.put("deterministicToolRoute", terminalDeterministicTool);
        m.put("routingRouteKind", routeKind);

        String toolKind = safe(trace.deterministicToolKind());
        String toolDetail = safe(trace.deterministicToolDetail());

        ToolTelemetryState state = deriveState(toolOutcome, toolKind, primaryWasDeterministicRoute, toolDetail);

        m.put("toolApplicable", state.toolApplicable());
        m.put("toolSelected", state.toolSelected());
        m.put("toolExecuted", state.toolExecuted());
        m.put("toolSucceeded", state.toolSucceeded());
        boolean toolResultUsedAsFinal = state.toolResultUsedAsFinal();
        if (trace.routingFallbackApplied() && hasRetrievalFallbackFinalSource(trace)) {
            toolResultUsedAsFinal = false;
            m.put("toolNegativeFallbackApplied", true);
        }
        m.put("toolResultUsedAsFinal", toolResultUsedAsFinal);
        if (!state.toolFallbackReason().isBlank()) {
            m.put("toolFallbackReason", state.toolFallbackReason());
        }
        if (!state.toolName().isBlank()) {
            m.put("toolName", state.toolName());
            m.put("tool_used", state.toolName());
        }
        m.put("used_tool", state.toolExecuted());
        if (!state.toolResultSummary().isBlank()) {
            m.put("toolResultSummary", state.toolResultSummary());
        }
        if (!toolOutcome.isBlank()) {
            m.put("deterministicToolOutcome", toolOutcome);
        }
        if (!toolKind.isBlank()) {
            m.put("deterministicToolKind", toolKind);
        }

        parseRoutingTelemetry(toolDetail, m);
        copyDetailToken(toolDetail, m, "deterministicEvidenceLevel");
        copyDetailToken(toolDetail, m, "routingOracleUsed");
        copyDetailToken(toolDetail, m, "toolApplicabilityEligible");
        copyDetailToken(toolDetail, m, "toolFallbackReason");
        copyDetailToken(toolDetail, m, "toolInputSummary");
        copyDetailToken(toolDetail, m, "toolOutputHash");
        enrichMonotonicSafetyTelemetry(trace, m);
        m.putAll(FunctionCallingTelemetryMapper.fromTrace(trace));
        m.putAll(AdvisorTelemetryMapper.fromTrace(trace));
        putFinalAnswerSourceFromTrace(trace, m);

        return Map.copyOf(m);
    }

    private static void putFinalAnswerSourceFromTrace(ExecutionTrace trace, Map<String, Object> m) {
        if (m.containsKey("finalAnswerSource")) {
            return;
        }
        String fromStage = finalAnswerSourceFromStages(trace);
        if (!fromStage.isBlank()) {
            m.put("finalAnswerSource", fromStage);
            return;
        }
        String toolOutcome = safe(trace.deterministicToolOutcome());
        if (DeterministicToolOutcome.EXECUTED_SUCCESS.name().equals(toolOutcome) && !trace.routingFallbackApplied()) {
            m.put("finalAnswerSource", "TOOL_FINAL");
            return;
        }
        if (trace.functionCallingShortCircuited()
                && FunctionCallingOutcome.EXECUTED_SUCCESS.name().equals(safe(trace.functionCallingOutcome()))
                && !trace.routingFallbackApplied()) {
            m.put("finalAnswerSource", "FUNCTION_FINAL");
        }
    }

    private static void enrichMonotonicSafetyTelemetry(ExecutionTrace trace, Map<String, Object> m) {
        if (trace.stages() == null) {
            return;
        }
        for (ExecutionStageTrace stage : trace.stages()) {
            if (stage != null && MonotonicSafetyTelemetrySupport.STAGE_NAME.equals(stage.stageName())) {
                MonotonicSafetyTelemetrySupport.enrichFromStageMessage(m, stage.message());
                return;
            }
        }
    }

    private static boolean hasRetrievalFallbackFinalSource(ExecutionTrace trace) {
        return DeterministicToolNegativeFallbackPolicy.FINAL_ANSWER_SOURCE.equals(finalAnswerSourceFromStages(trace));
    }

    private static String finalAnswerSourceFromStages(ExecutionTrace trace) {
        if (trace.stages() == null) {
            return "";
        }
        for (ExecutionStageTrace stage : trace.stages()) {
            if (stage == null || !"final_answer_source".equals(stage.stageName())) {
                continue;
            }
            String source = tokenValue(stage.message() != null ? stage.message() : "", "finalAnswerSource=");
            if (!source.isBlank()) {
                return source;
            }
        }
        return "";
    }

    private static void parseRoutingTelemetry(String toolDetail, Map<String, Object> m) {
        if (toolDetail == null || toolDetail.isBlank()) {
            return;
        }
        String suppressed = tokenValue(toolDetail, "routeSuppressedByClassifier=");
        if (!suppressed.isBlank()) {
            m.put("routeSuppressedByClassifier", Boolean.parseBoolean(suppressed));
        }
        String reason = tokenValue(toolDetail, "routeSuppressedReason=");
        if (!reason.isBlank()) {
            m.put("routeSuppressedReason", reason);
        }
        String heuristic = tokenValue(toolDetail, "heuristicRouteUsed=");
        if (!heuristic.isBlank()) {
            m.put("heuristicRouteUsed", Boolean.parseBoolean(heuristic));
        }
    }

    private static void copyDetailToken(String toolDetail, Map<String, Object> m, String key) {
        String value = tokenValue(toolDetail, key + "=");
        if (value.isBlank()) {
            return;
        }
        if ("routingOracleUsed".equals(key) || "toolApplicabilityEligible".equals(key)) {
            m.put(key, Boolean.parseBoolean(value));
        } else {
            m.put(key, value);
        }
    }

    private static String tokenValue(String detail, String key) {
        int start = detail.indexOf(key);
        if (start < 0) {
            return "";
        }
        start += key.length();
        int end = detail.length();
        for (String next : List.of(
                "routeSuppressedByClassifier=",
                "routeSuppressedReason=",
                "heuristicRouteUsed=",
                "deterministicEvidenceLevel=",
                "routingOracleUsed=",
                "toolApplicabilityEligible=",
                "toolFallbackReason=",
                "toolInputSummary=",
                "toolOutputHash=",
                ";",
                " | ")) {
            int idx = detail.indexOf(next, start);
            if (idx > start && idx < end) {
                end = idx;
            }
        }
        return detail.substring(start, end).trim();
    }

    private static ToolTelemetryState deriveState(
            String toolOutcome, String toolKind, boolean primaryWasDeterministicRoute, String toolDetail) {
        if (toolOutcome.isBlank() && !primaryWasDeterministicRoute) {
            return ToolTelemetryState.inactive();
        }

        DeterministicToolOutcome outcome = parseOutcome(toolOutcome);
        Optional<DeterministicToolKind> kind = parseKind(toolKind);
        String toolName = kind.map(k -> ToolDescriptor.getName(DeterministicToolKindMappings.toQueryType(k))).orElse("");

        return switch (outcome) {
            case EXECUTED_SUCCESS -> new ToolTelemetryState(
                    true,
                    true,
                    true,
                    true,
                    true,
                    "",
                    toolName,
                    summarizeFromDetail(toolDetail));
            case EXECUTED_FAILED_INFRA -> new ToolTelemetryState(
                    true,
                    true,
                    true,
                    false,
                    false,
                    fallbackReason(toolDetail, "tool_execution_failed"),
                    toolName,
                    "");
            case SELECTED -> new ToolTelemetryState(
                    true,
                    true,
                    false,
                    false,
                    false,
                    "",
                    toolName,
                    "");
            case NOT_APPLICABLE -> {
                boolean applicable = parseBooleanDetail(toolDetail, "toolApplicabilityEligible");
                yield new ToolTelemetryState(
                        applicable,
                        false,
                        false,
                        false,
                        false,
                        explicitFallbackReason(toolDetail, "tool_not_applicable"),
                        "",
                        "");
            }
            case SUPPRESSED_BY_AMBIGUITY -> new ToolTelemetryState(
                    false,
                    false,
                    false,
                    false,
                    false,
                    "tool_suppressed_by_ambiguity",
                    "",
                    "");
            case DISABLED_BY_CONFIG -> new ToolTelemetryState(
                    false,
                    false,
                    false,
                    false,
                    false,
                    "tool_disabled_by_config",
                    "",
                    "");
            case NOT_ATTEMPTED, FALLBACK_TO_WORKFLOW -> primaryWasDeterministicRoute
                    ? new ToolTelemetryState(
                            false,
                            false,
                            false,
                            false,
                            false,
                            fallbackReason(toolDetail, "tool_not_attempted"),
                            "",
                            "")
                    : ToolTelemetryState.inactive();
        };
    }

    private static String fallbackReason(String detail, String defaultReason) {
        return explicitFallbackReason(detail, defaultReason);
    }

    private static String explicitFallbackReason(String detail, String defaultReason) {
        String fromDetail = tokenValue(detail, "toolFallbackReason=");
        if (!fromDetail.isBlank()) {
            return fromDetail;
        }
        if (detail != null && detail.contains("tool_fallback_to_workflow")) {
            return "tool_execution_failed";
        }
        if (detail != null && detail.contains("tool_not_applicable")) {
            return "tool_not_applicable";
        }
        if (detail != null && detail.contains("tool_ambiguous_match")) {
            return "tool_ambiguous_match";
        }
        if (detail != null && detail.contains("route_suppressed_by_classifier")) {
            return tokenValue(detail, "routeSuppressedReason=");
        }
        return defaultReason;
    }

    private static boolean parseBooleanDetail(String detail, String key) {
        return Boolean.parseBoolean(tokenValue(detail, key + "="));
    }

    private static String summarizeFromDetail(String detail) {
        if (detail == null || detail.isBlank()) {
            return "";
        }
        String trimmed = detail.strip();
        if (trimmed.length() <= MAX_RESULT_SUMMARY_CHARS) {
            return trimmed;
        }
        return trimmed.substring(0, MAX_RESULT_SUMMARY_CHARS) + "...";
    }

    private static DeterministicToolOutcome parseOutcome(String raw) {
        if (raw == null || raw.isBlank()) {
            return DeterministicToolOutcome.NOT_ATTEMPTED;
        }
        try {
            return DeterministicToolOutcome.valueOf(raw.trim());
        } catch (IllegalArgumentException ex) {
            return DeterministicToolOutcome.NOT_ATTEMPTED;
        }
    }

    private static Optional<DeterministicToolKind> parseKind(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(DeterministicToolKind.valueOf(raw.trim()));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private record ToolTelemetryState(
            boolean toolApplicable,
            boolean toolSelected,
            boolean toolExecuted,
            boolean toolSucceeded,
            boolean toolResultUsedAsFinal,
            String toolFallbackReason,
            String toolName,
            String toolResultSummary) {

        static ToolTelemetryState inactive() {
            return new ToolTelemetryState(false, false, false, false, false, "", "", "");
        }
    }
}
