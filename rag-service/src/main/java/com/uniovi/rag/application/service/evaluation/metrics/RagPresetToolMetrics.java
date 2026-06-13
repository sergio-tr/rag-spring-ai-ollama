package com.uniovi.rag.application.service.evaluation.metrics;

import com.uniovi.rag.application.service.runtime.tool.DeterministicToolApplicability;
import com.uniovi.rag.domain.model.QueryType;
import java.util.LinkedHashMap;
import java.util.Map;

/** Computes tool routing telemetry and evaluation coverage fields for preset benchmark rows. */
public final class RagPresetToolMetrics {

    public static final String KEY_TOOL_APPLICABLE = "toolApplicable";
    public static final String KEY_TOOL_SELECTED = "toolSelected";
    public static final String KEY_TOOL_NAME = "toolName";
    public static final String KEY_TOOL_EXECUTED = "toolExecuted";
    public static final String KEY_TOOL_SUCCEEDED = "toolSucceeded";
    public static final String KEY_TOOL_FALLBACK_REASON = "toolFallbackReason";
    public static final String KEY_TOOL_RESULT_USED_AS_FINAL = "toolResultUsedAsFinal";
    public static final String KEY_DETERMINISTIC_TOOL_ROUTE = "deterministicToolRoute";
    public static final String KEY_FUNCTION_CALLING_USED = "functionCallingUsed";
    public static final String KEY_FUNCTION_CALL_ATTEMPTED = "functionCallAttempted";
    public static final String KEY_FUNCTION_CALL_NAME = "functionCallName";
    public static final String KEY_FUNCTION_CALL_ARGUMENTS_VALID = "functionCallArgumentsValid";
    public static final String KEY_FUNCTION_CALL_SUCCEEDED = "functionCallSucceeded";
    public static final String KEY_FUNCTION_CALL_FALLBACK_REASON = "functionCallFallbackReason";
    public static final String KEY_FUNCTION_RESULT_USED_AS_FINAL = "functionResultUsedAsFinal";
    public static final String KEY_FUNCTION_RESULT_USED_AS_CONTEXT = "functionResultUsedAsContext";
    public static final String KEY_FUNCTION_CALL_ROUTE = "functionCallRoute";
    public static final String KEY_EXECUTION_ROUTE = "executionRoute";
    public static final String KEY_QUERY_TYPE_SOURCE = "queryTypeSource";
    public static final String KEY_TOOL_COVERAGE_STATUS = "toolCoverageStatus";
    public static final String KEY_ROUTING_ROUTE_KIND = "routingRouteKind";

    private RagPresetToolMetrics() {}

    public static void computeAndMerge(Map<String, Object> metrics) {
        if (metrics == null) {
            return;
        }
        metrics.putAll(compute(metrics));
    }

    public static Map<String, Object> compute(Map<String, Object> metrics) {
        Map<String, Object> out = new LinkedHashMap<>();
        Map<String, Object> mp = metrics != null ? metrics : Map.of();

        QueryType expected = parseQueryType(str(mp.get(DatasetMetricContract.KEY_QUERY_TYPE_EXPECTED)));
        if (expected == null) {
            expected = parseQueryType(str(mp.get("query_type")));
        }
        ToolCoverageStatus coverageStatus =
                expected == null
                        ? ToolCoverageStatus.UNKNOWN
                        : DeterministicToolApplicability.isApplicableQueryType(expected)
                                ? ToolCoverageStatus.APPLICABLE
                                : ToolCoverageStatus.NOT_APPLICABLE;
        out.put(KEY_TOOL_COVERAGE_STATUS, coverageStatus.name());

        out.put(KEY_QUERY_TYPE_SOURCE, deriveQueryTypeSource(mp).name());

        copyBool(out, mp, KEY_TOOL_APPLICABLE);
        copyBool(out, mp, KEY_TOOL_SELECTED);
        copyBool(out, mp, KEY_TOOL_EXECUTED);
        copyBool(out, mp, KEY_TOOL_SUCCEEDED);
        copyBool(out, mp, KEY_TOOL_RESULT_USED_AS_FINAL);
        copyBool(out, mp, KEY_DETERMINISTIC_TOOL_ROUTE);
        copyBool(out, mp, KEY_FUNCTION_CALLING_USED);
        copyBool(out, mp, KEY_FUNCTION_CALL_ATTEMPTED);
        copyBool(out, mp, KEY_FUNCTION_CALL_ARGUMENTS_VALID);
        copyBool(out, mp, KEY_FUNCTION_CALL_SUCCEEDED);
        copyBool(out, mp, KEY_FUNCTION_RESULT_USED_AS_FINAL);
        copyBool(out, mp, KEY_FUNCTION_RESULT_USED_AS_CONTEXT);

        copyString(out, mp, KEY_FUNCTION_CALL_NAME);
        copyString(out, mp, KEY_FUNCTION_CALL_FALLBACK_REASON);
        copyString(out, mp, KEY_FUNCTION_CALL_ROUTE);
        copyString(out, mp, KEY_EXECUTION_ROUTE);
        copyString(out, mp, KEY_ROUTING_ROUTE_KIND);
        if (!out.containsKey(KEY_ROUTING_ROUTE_KIND) && out.containsKey(KEY_EXECUTION_ROUTE)) {
            out.put(KEY_ROUTING_ROUTE_KIND, out.get(KEY_EXECUTION_ROUTE));
        }

        copyString(out, mp, KEY_TOOL_NAME);
        copyString(out, mp, KEY_TOOL_FALLBACK_REASON);
        copyString(out, mp, KEY_ROUTING_ROUTE_KIND);
        copyBool(out, mp, RagPresetClassifierMetrics.KEY_ROUTE_SUPPRESSED_BY_CLASSIFIER);
        copyString(out, mp, RagPresetClassifierMetrics.KEY_ROUTE_SUPPRESSED_REASON);
        copyBool(out, mp, RagPresetClassifierMetrics.KEY_HEURISTIC_ROUTE_USED);
        copyString(out, mp, "toolResultSummary");
        copyString(out, mp, "deterministicToolOutcome");
        copyString(out, mp, "deterministicToolKind");

        if (mp.containsKey("used_tool")) {
            out.putIfAbsent("used_tool", mp.get("used_tool"));
        }
        if (mp.containsKey("tool_used")) {
            out.putIfAbsent("tool_used", mp.get("tool_used"));
        }

        return out;
    }

    private static QueryTypeSource deriveQueryTypeSource(Map<String, Object> mp) {
        String classifierStatus = str(mp.get("classifierStatus"));
        String predicted = firstNonBlank(str(mp.get("queryTypePredicted")), str(mp.get("classifierLabel")));
        if ("OK".equalsIgnoreCase(classifierStatus)
                && !predicted.isBlank()
                && !"UNCLASSIFIED".equalsIgnoreCase(predicted)) {
            return QueryTypeSource.CLASSIFIER;
        }
        String expected = str(mp.get(DatasetMetricContract.KEY_QUERY_TYPE_EXPECTED));
        if (!expected.isBlank()) {
            return QueryTypeSource.DATASET_EXPECTED;
        }
        return QueryTypeSource.UNKNOWN;
    }

    private static void copyBool(Map<String, Object> out, Map<String, Object> mp, String key) {
        if (mp.containsKey(key)) {
            out.put(key, bool(mp.get(key)));
        }
    }

    private static void copyString(Map<String, Object> out, Map<String, Object> mp, String key) {
        String v = str(mp.get(key));
        if (!v.isBlank()) {
            out.put(key, v);
        }
    }

    private static QueryType parseQueryType(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return QueryType.valueOf(raw.trim());
        } catch (IllegalArgumentException ex) {
            return null;
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

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b != null ? b : "";
    }
}
