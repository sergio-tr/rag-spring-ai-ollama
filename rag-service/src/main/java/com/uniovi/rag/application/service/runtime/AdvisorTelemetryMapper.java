package com.uniovi.rag.application.service.runtime;

import com.uniovi.rag.domain.runtime.advisor.AdvisorOutcome;
import com.uniovi.rag.domain.runtime.engine.ExecutionTrace;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRouteKind;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Maps advisor trace fields to Lab-safe telemetry. */
public final class AdvisorTelemetryMapper {

    private AdvisorTelemetryMapper() {}

    public static Map<String, Object> fromTrace(ExecutionTrace trace) {
        if (trace == null) {
            return Map.of();
        }
        Map<String, Object> m = new LinkedHashMap<>();
        String routeKind = safe(trace.routingRouteKind());
        boolean advisorPrimary = AdaptiveRouteKind.ADVISOR_ROUTE.name().equals(routeKind);
        boolean attempted = trace.advisorAttempted();
        Optional<AdvisorOutcome> outcome = parseOutcome(trace.advisorOutcome());
        boolean applied =
                outcome.filter(o -> o == AdvisorOutcome.EXECUTED_SUCCESS).isPresent();
        boolean resultUsed = applied && trace.advisorShortCircuitedContextPrep();
        boolean changedContext = trace.packedContextBlockCount() > 0;
        boolean changedPrompt = applied && trace.packedContextSourceCount() > 0;

        m.put("advisorAttempted", attempted);
        m.put("advisorApplied", applied);
        m.put("advisorRoute", advisorPrimary);
        m.put("advisorName", resolveAdvisorName(trace));
        m.put("advisorType", resolveAdvisorType(trace));
        m.put("advisorContributionType", contributionType(outcome, applied, changedContext, changedPrompt));
        m.put("advisorChangedQuery", false);
        m.put("advisorChangedContext", changedContext);
        m.put("advisorChangedPrompt", changedPrompt);
        m.put("advisorValidatedAnswer", false);
        m.put("advisorResultUsed", resultUsed);
        String fallback = fallbackReason(outcome, attempted, applied, trace.routingFallbackApplied());
        if (!fallback.isBlank()) {
            m.put("advisorFallbackReason", fallback);
        }
        if (advisorPrimary) {
            m.put("executionRoute", routeKind);
        }
        return Map.copyOf(m);
    }

    private static String contributionType(
            Optional<AdvisorOutcome> outcome,
            boolean applied,
            boolean changedContext,
            boolean changedPrompt) {
        if (!applied) {
            return outcome.isEmpty() ? "" : "none";
        }
        if (changedContext && changedPrompt) {
            return "retrieval_guidance,context_pack";
        }
        if (changedContext) {
            return "context_pack";
        }
        return "retrieval_guidance";
    }

    private static String fallbackReason(
            Optional<AdvisorOutcome> outcome,
            boolean attempted,
            boolean applied,
            boolean routingFallbackApplied) {
        if (applied) {
            return "";
        }
        if (!attempted) {
            return outcome
                    .filter(o -> o == AdvisorOutcome.SUPPRESSED_BY_POLICY)
                    .map(o -> "policy_suppressed")
                    .orElse("");
        }
        if (outcome.isPresent()) {
            return switch (outcome.get()) {
                case SUPPRESSED_BY_POLICY -> "policy_suppressed";
                case EXECUTED_FAILED_RETRIEVAL -> "retrieval_failed";
                case EXECUTED_FAILED_PACKING -> "packing_failed";
                case FAILED_RESERVED_KIND -> "reserved_kind";
                default -> routingFallbackApplied ? "fallback_to_workflow" : "";
            };
        }
        return routingFallbackApplied ? "fallback_to_workflow" : "";
    }

    private static String resolveAdvisorName(ExecutionTrace trace) {
        String kinds = safe(trace.advisorKindsExecuted());
        if (kinds.isBlank()) {
            return "";
        }
        return kinds.replace("RETRIEVAL_ADVISOR", "retrievalAdvisor")
                .replace("CONTEXT_PACKING_ADVISOR", "contextPackingAdvisor");
    }

    private static String resolveAdvisorType(ExecutionTrace trace) {
        String kinds = safe(trace.advisorKindsExecuted());
        if (kinds.isBlank()) {
            return "";
        }
        if (kinds.contains("RETRIEVAL_ADVISOR") && kinds.contains("CONTEXT_PACKING_ADVISOR")) {
            return "RETRIEVAL_AND_CONTEXT_PACKING";
        }
        if (kinds.contains("RETRIEVAL_ADVISOR")) {
            return "RETRIEVAL";
        }
        if (kinds.contains("CONTEXT_PACKING_ADVISOR")) {
            return "CONTEXT_PACKING";
        }
        return "";
    }

    private static Optional<AdvisorOutcome> parseOutcome(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(AdvisorOutcome.valueOf(raw.trim()));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
