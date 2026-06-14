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
        Map<String, Object> proposal = FunctionCallingTelemetrySupport.proposalFieldsFromTrace(trace);

        boolean backendAttempted = bool(proposal.get("backendFunctionCallAttempted"));
        boolean nativeAttempted = bool(proposal.get("nativeProviderFunctionCallAttempted"));
        boolean attempted = trace.functionCallingAttempted() || backendAttempted || nativeAttempted;

        String outcomeRaw = safe(trace.functionCallingOutcome());
        Optional<FunctionCallingOutcome> outcome = parseOutcome(outcomeRaw);
        boolean succeeded = outcome.filter(o -> o == FunctionCallingOutcome.EXECUTED_SUCCESS).isPresent();
        String routeKind = safe(trace.routingRouteKind());
        boolean fcPrimary = AdaptiveRouteKind.FUNCTION_CALLING_ROUTE.name().equals(routeKind);

        m.put("functionCallAttempted", attempted);
        m.put("functionCallingUsed", succeeded);
        m.put("functionCallSucceeded", succeeded);
        m.put("functionCallArgumentsValid", argumentsValid(outcome, proposal));
        m.put("functionCallName", resolveCallName(trace, proposal));
        String fallbackReason = fallbackReason(outcome, attempted, succeeded, trace.routingFallbackApplied());
        if (!fallbackReason.isBlank()) {
            m.put("functionCallFallbackReason", fallbackReason);
        }
        m.put("functionResultUsedAsFinal", succeeded && trace.functionCallingShortCircuited());
        m.put("functionResultUsedAsContext", succeeded);
        m.put("functionCallRoute", fcPrimary ? routeKind : "");
        m.put("executionRoute", routeKind);

        copyStringIfPresent(m, proposal, "functionProposalMode");
        copyStringIfPresent(m, proposal, "functionProposalSource");
        copyBoolIfPresent(m, proposal, "functionProposalValid");
        copyBoolIfPresent(m, proposal, "functionProposalRepairAttempted");
        copyBoolIfPresent(m, proposal, "functionProposalRepairSucceeded");
        m.put("backendFunctionCallAttempted", backendAttempted);
        m.put("nativeProviderFunctionCallAttempted", nativeAttempted);
        copyStringIfPresent(m, proposal, "functionToolKind");
        if (!m.containsKey("functionToolKind") && !safe(trace.functionCallingToolKind()).isBlank()) {
            m.put("functionToolKind", safe(trace.functionCallingToolKind()));
        }

        return Map.copyOf(m);
    }

    private static boolean argumentsValid(Optional<FunctionCallingOutcome> outcome, Map<String, Object> proposal) {
        if (proposal.containsKey("functionProposalValid")) {
            return bool(proposal.get("functionProposalValid"));
        }
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

    private static String resolveCallName(ExecutionTrace trace, Map<String, Object> proposal) {
        Object fromProposal = proposal.get("functionCallName");
        if (fromProposal != null && !String.valueOf(fromProposal).isBlank()) {
            return String.valueOf(fromProposal);
        }
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

    private static void copyStringIfPresent(Map<String, Object> target, Map<String, Object> source, String key) {
        Object raw = source.get(key);
        if (raw != null && !String.valueOf(raw).isBlank()) {
            target.put(key, String.valueOf(raw));
        }
    }

    private static void copyBoolIfPresent(Map<String, Object> target, Map<String, Object> source, String key) {
        if (source.containsKey(key)) {
            target.put(key, bool(source.get(key)));
        }
    }

    private static boolean bool(Object raw) {
        if (raw instanceof Boolean b) {
            return b;
        }
        if (raw instanceof String s) {
            return Boolean.parseBoolean(s);
        }
        return false;
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
