package com.uniovi.rag.application.service.evaluation.metrics;

import java.util.LinkedHashMap;
import java.util.Map;

/** Computes advisor telemetry fields for preset benchmark rows. */
public final class RagPresetAdvisorMetrics {

    public static final String KEY_ADVISOR_ENABLED = "advisorEnabled";
    public static final String KEY_ADVISOR_ROUTE = "advisorRoute";
    public static final String KEY_ADVISOR_ATTEMPTED = "advisorAttempted";
    public static final String KEY_ADVISOR_APPLIED = "advisorApplied";
    public static final String KEY_ADVISOR_NAME = "advisorName";
    public static final String KEY_ADVISOR_TYPE = "advisorType";
    public static final String KEY_ADVISOR_CONTRIBUTION_TYPE = "advisorContributionType";
    public static final String KEY_ADVISOR_CHANGED_QUERY = "advisorChangedQuery";
    public static final String KEY_ADVISOR_CHANGED_CONTEXT = "advisorChangedContext";
    public static final String KEY_ADVISOR_CHANGED_PROMPT = "advisorChangedPrompt";
    public static final String KEY_ADVISOR_VALIDATED_ANSWER = "advisorValidatedAnswer";
    public static final String KEY_ADVISOR_FALLBACK_REASON = "advisorFallbackReason";
    public static final String KEY_ADVISOR_RESULT_USED = "advisorResultUsed";

    private RagPresetAdvisorMetrics() {}

    public static void computeAndMerge(Map<String, Object> metrics) {
        if (metrics == null) {
            return;
        }
        metrics.putAll(compute(metrics));
    }

    public static Map<String, Object> compute(Map<String, Object> metrics) {
        Map<String, Object> out = new LinkedHashMap<>();
        Map<String, Object> mp = metrics != null ? metrics : Map.of();

        if (mp.containsKey("useAdvisor")) {
            out.put(KEY_ADVISOR_ENABLED, bool(mp.get("useAdvisor")));
        } else if (mp.containsKey(KEY_ADVISOR_ENABLED)) {
            copyBool(out, mp, KEY_ADVISOR_ENABLED);
        }

        copyBool(out, mp, KEY_ADVISOR_ROUTE);
        copyBool(out, mp, KEY_ADVISOR_ATTEMPTED);
        copyBool(out, mp, KEY_ADVISOR_APPLIED);
        copyBool(out, mp, KEY_ADVISOR_CHANGED_QUERY);
        copyBool(out, mp, KEY_ADVISOR_CHANGED_CONTEXT);
        copyBool(out, mp, KEY_ADVISOR_CHANGED_PROMPT);
        copyBool(out, mp, KEY_ADVISOR_VALIDATED_ANSWER);
        copyBool(out, mp, KEY_ADVISOR_RESULT_USED);

        copyString(out, mp, KEY_ADVISOR_NAME);
        copyString(out, mp, KEY_ADVISOR_TYPE);
        copyString(out, mp, KEY_ADVISOR_CONTRIBUTION_TYPE);
        copyString(out, mp, KEY_ADVISOR_FALLBACK_REASON);

        return out;
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
}
