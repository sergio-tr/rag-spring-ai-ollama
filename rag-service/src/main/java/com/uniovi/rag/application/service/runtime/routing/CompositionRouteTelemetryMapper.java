package com.uniovi.rag.application.service.runtime.routing;

import com.uniovi.rag.domain.runtime.routing.AdaptiveRouteKind;
import java.util.Locale;
import java.util.Map;

/** Derives route-composition telemetry from persisted execution hints (no runtime side effects). */
public final class CompositionRouteTelemetryMapper {

    public static final String KEY_COMPONENT_ROUTE_DECISION = "componentRouteDecision";
    public static final String KEY_COMPONENT_ROUTE_PRECEDENCE = "componentRoutePrecedence";
    public static final String KEY_DETERMINISTIC_TOOL_CONSIDERED = "deterministicToolConsidered";
    public static final String KEY_BACKEND_FUNCTION_CONSIDERED = "backendFunctionConsidered";
    public static final String KEY_SPARSE_HYBRID_CONSIDERED = "sparseHybridConsidered";
    public static final String KEY_FACTUAL_VERIFIER_CONSIDERED = "factualVerifierConsidered";
    public static final String KEY_COMPOSITION_FALLBACK_REASON = "compositionFallbackReason";

    public static final String PRECEDENCE_CHAIN = "TOOL>FC>RETRIEVAL>FACTUAL>EVAL";

    private CompositionRouteTelemetryMapper() {}

    public static void enrich(Map<String, Object> telemetry) {
        if (telemetry == null || telemetry.isEmpty()) {
            return;
        }
        String routeKind = str(telemetry.get("routingRouteKind"));
        String finalSource = str(telemetry.get("finalAnswerSource"));
        boolean routingAttempted =
                bool(telemetry.get("routingAttempted")) || bool(telemetry.get("adaptiveRoutingApplied"));

        boolean toolConsidered =
                routingAttempted
                        || bool(telemetry.get("toolApplicable"))
                        || bool(telemetry.get("toolExecuted"))
                        || bool(telemetry.get("deterministicToolRoute"))
                        || AdaptiveRouteKind.DETERMINISTIC_TOOL_ROUTE.name().equals(routeKind);
        boolean fcConsidered =
                routingAttempted
                        || bool(telemetry.get("functionCallAttempted"))
                        || bool(telemetry.get("functionCallingUsed"))
                        || AdaptiveRouteKind.FUNCTION_CALLING_ROUTE.name().equals(routeKind);
        boolean retrievalPath = isRetrievalPath(routeKind, finalSource, telemetry);
        boolean sparseHybridConsidered =
                retrievalPath
                        && (bool(telemetry.get("hybridApplied"))
                                || bool(telemetry.get("advancedRetrievalApplied"))
                                || "HYBRID".equalsIgnoreCase(str(telemetry.get("materializationStrategy"))));
        boolean factualVerifierConsidered =
                retrievalPath && !"TOOL_FINAL".equals(finalSource) && !"FUNCTION_FINAL".equals(finalSource);

        telemetry.put(KEY_COMPONENT_ROUTE_PRECEDENCE, PRECEDENCE_CHAIN);
        telemetry.put(KEY_COMPONENT_ROUTE_DECISION, componentRouteDecision(finalSource, routeKind));
        telemetry.put(KEY_DETERMINISTIC_TOOL_CONSIDERED, toolConsidered);
        telemetry.put(KEY_BACKEND_FUNCTION_CONSIDERED, fcConsidered);
        telemetry.put(KEY_SPARSE_HYBRID_CONSIDERED, sparseHybridConsidered);
        telemetry.put(KEY_FACTUAL_VERIFIER_CONSIDERED, factualVerifierConsidered);
        telemetry.put(KEY_COMPOSITION_FALLBACK_REASON, compositionFallbackReason(telemetry, finalSource));
    }

    private static boolean isRetrievalPath(String routeKind, String finalSource, Map<String, Object> telemetry) {
        if (AdaptiveRouteKind.RETRIEVAL_WORKFLOW_ROUTE.name().equals(routeKind)) {
            return true;
        }
        if (bool(telemetry.get("routingFallbackApplied"))) {
            return true;
        }
        if ("GENERATED".equals(finalSource)
                || "FORCED_ABSTENTION".equals(finalSource)
                || "DATE_GUARD_ABSTENTION".equals(finalSource)) {
            return true;
        }
        return bool(telemetry.get("useRetrieval")) && !"TOOL_FINAL".equals(finalSource) && !"FUNCTION_FINAL".equals(finalSource);
    }

    private static String componentRouteDecision(String finalSource, String routeKind) {
        if ("TOOL_FINAL".equals(finalSource)) {
            return "TOOL";
        }
        if ("FUNCTION_FINAL".equals(finalSource)) {
            return "FUNCTION";
        }
        if ("FORCED_ABSTENTION".equals(finalSource) || "DATE_GUARD_ABSTENTION".equals(finalSource)) {
            return "ABSTENTION";
        }
        if ("GENERATED".equals(finalSource)) {
            return "RETRIEVAL";
        }
        if (AdaptiveRouteKind.DETERMINISTIC_TOOL_ROUTE.name().equals(routeKind)) {
            return "TOOL";
        }
        if (AdaptiveRouteKind.FUNCTION_CALLING_ROUTE.name().equals(routeKind)) {
            return "FUNCTION";
        }
        if (AdaptiveRouteKind.RETRIEVAL_WORKFLOW_ROUTE.name().equals(routeKind)) {
            return "RETRIEVAL";
        }
        if (AdaptiveRouteKind.DIRECT_WORKFLOW_ROUTE.name().equals(routeKind)) {
            return "DIRECT";
        }
        return routeKind.isBlank() ? "UNKNOWN" : routeKind;
    }

    private static String compositionFallbackReason(Map<String, Object> telemetry, String finalSource) {
        if ("TOOL_FINAL".equals(finalSource) || "FUNCTION_FINAL".equals(finalSource)) {
            return "";
        }
        if ("FORCED_ABSTENTION".equals(finalSource) || "DATE_GUARD_ABSTENTION".equals(finalSource)) {
            return "VERIFIER_FORCED_ABSTENTION";
        }
        String toolFallback = str(telemetry.get("toolFallbackReason"));
        if (!toolFallback.isBlank()) {
            if (toolFallback.contains("not_applicable")) {
                return "TOOL_NOT_APPLICABLE";
            }
            return "TOOL_INCOMPLETE_OR_FAILED";
        }
        String functionFallback = str(telemetry.get("functionCallFallbackReason"));
        if (!functionFallback.isBlank()) {
            if (functionFallback.contains("not_applicable")) {
                return "FUNCTION_NOT_APPLICABLE";
            }
            return "FUNCTION_INCOMPLETE_OR_FAILED";
        }
        if ("ZERO_MATCHES".equals(str(telemetry.get("sparseRetrievalStatus")))) {
            return "SPARSE_ZERO_MATCHES";
        }
        if (bool(telemetry.get("hybridApplied")) && !bool(telemetry.get("sparseHit"))) {
            return "HYBRID_DENSE_ONLY_FALLBACK";
        }
        if ("GENERATED".equals(finalSource)) {
            return "GENERATED_FINAL";
        }
        if (AdaptiveRouteKind.RETRIEVAL_WORKFLOW_ROUTE.name().equals(str(telemetry.get("routingRouteKind")))) {
            return "RETRIEVAL_PRIMARY";
        }
        return "";
    }

    private static String str(Object raw) {
        if (raw == null) {
            return "";
        }
        String s = String.valueOf(raw).trim();
        return "null".equalsIgnoreCase(s) ? "" : s;
    }

    private static boolean bool(Object raw) {
        if (raw instanceof Boolean b) {
            return b;
        }
        if (raw == null) {
            return false;
        }
        return Boolean.parseBoolean(String.valueOf(raw).trim().toLowerCase(Locale.ROOT));
    }
}
