package com.uniovi.rag.application.service.runtime;

import com.uniovi.rag.application.service.runtime.tool.DeterministicToolKindMappings;
import com.uniovi.rag.configuration.ToolDescriptor;
import com.uniovi.rag.domain.runtime.engine.ExecutionTrace;
import com.uniovi.rag.domain.runtime.functioncalling.FunctionCallingOutcome;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRouteKind;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolKind;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Maps function-calling trace fields to Lab-safe telemetry. */
public final class FunctionCallingTelemetryMapper {

    private FunctionCallingTelemetryMapper() {}

    public static Map<String, Object> fromTrace(ExecutionTrace trace) {
        if (trace == null) {
            return Map.of();
        }
        Map<String, Object> m = new LinkedHashMap<>();
        boolean attempted = trace.functionCallingAttempted();
        String outcomeRaw = safe(trace.functionCallingOutcome());
        Optional<FunctionCallingOutcome> outcome = parseOutcome(outcomeRaw);
        boolean succeeded = outcome.filter(o -> o == FunctionCallingOutcome.EXECUTED_SUCCESS).isPresent();
        String routeKind = safe(trace.routingRouteKind());
        boolean fcPrimary = AdaptiveRouteKind.FUNCTION_CALLING_ROUTE.name().equals(routeKind);

        m.put("functionCallAttempted", attempted);
        m.put("functionCallingUsed", succeeded);
        m.put("functionCallSucceeded", succeeded);
        m.put("functionCallArgumentsValid", argumentsValid(outcome));
        m.put("functionCallName", resolveCallName(trace));
        String fallbackReason = fallbackReason(outcome, attempted, succeeded, trace.routingFallbackApplied());
        if (!fallbackReason.isBlank()) {
            m.put("functionCallFallbackReason", fallbackReason);
        }
        m.put("functionResultUsedAsFinal", succeeded && trace.functionCallingShortCircuited());
        m.put("functionResultUsedAsContext", succeeded);
        m.put("functionCallRoute", fcPrimary ? routeKind : "");
        m.put("executionRoute", routeKind);
        return Map.copyOf(m);
    }

    private static boolean argumentsValid(Optional<FunctionCallingOutcome> outcome) {
        if (outcome.isEmpty()) {
            return false;
        }
        return switch (outcome.get()) {
            case EXECUTED_SUCCESS -> true;
            case INVALID_MODEL_OUTPUT -> false;
            default -> false;
        };
    }

    private static String fallbackReason(
            Optional<FunctionCallingOutcome> outcome,
            boolean attempted,
            boolean succeeded,
            boolean routingFallbackApplied) {
        if (!attempted) {
            return outcome
                    .filter(o -> o == FunctionCallingOutcome.NOT_APPLICABLE)
                    .map(o -> "function_not_applicable")
                    .orElse("");
        }
        if (succeeded) {
            return "";
        }
        if (outcome.isPresent()) {
            return switch (outcome.get()) {
                case NOT_APPLICABLE -> "function_not_applicable";
                case MODEL_DECLINED -> "model_declined";
                case INVALID_MODEL_OUTPUT -> "invalid_model_output";
                case EXECUTED_FAILED_INFRA -> "function_execution_failed";
                case SUPPRESSED_BY_AMBIGUITY -> "suppressed_by_ambiguity";
                case DISABLED_BY_CONFIG -> "disabled_by_config";
                default -> routingFallbackApplied ? "fallback_to_workflow" : "";
            };
        }
        return routingFallbackApplied ? "fallback_to_workflow" : "";
    }

    private static String resolveCallName(ExecutionTrace trace) {
        String kindRaw = safe(trace.functionCallingToolKind());
        if (kindRaw.isBlank()) {
            return "";
        }
        try {
            DeterministicToolKind kind = DeterministicToolKind.valueOf(kindRaw.trim());
            return ToolDescriptor.getName(DeterministicToolKindMappings.toQueryType(kind));
        } catch (IllegalArgumentException ex) {
            return "";
        }
    }

    private static Optional<FunctionCallingOutcome> parseOutcome(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(FunctionCallingOutcome.valueOf(raw.trim()));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
