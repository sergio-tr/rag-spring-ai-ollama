package com.uniovi.rag.application.service.evaluation.metrics;

import com.uniovi.rag.domain.model.QueryType;
import java.util.LinkedHashMap;
import java.util.Map;

/** Promotes classifier reliability fields onto preset benchmark metrics rows. */
public final class RagPresetClassifierMetrics {

    public static final String KEY_CLASSIFIER_CONFIDENCE = "classifierConfidence";
    public static final String KEY_CLASSIFIER_LABEL_SET_HASH = "classifierLabelSetHash";
    public static final String KEY_CLASSIFIER_FALLBACK_REASON = "classifierFallbackReason";
    public static final String KEY_ROUTE_SUPPRESSED_BY_CLASSIFIER = "routeSuppressedByClassifier";
    public static final String KEY_ROUTE_SUPPRESSED_REASON = "routeSuppressedReason";
    public static final String KEY_HEURISTIC_ROUTE_USED = "heuristicRouteUsed";

    private RagPresetClassifierMetrics() {}

    public static void computeAndMerge(Map<String, Object> metrics) {
        if (metrics == null) {
            return;
        }
        metrics.putAll(compute(metrics));
    }

    public static Map<String, Object> compute(Map<String, Object> metrics) {
        Map<String, Object> out = new LinkedHashMap<>();
        Map<String, Object> mp = metrics != null ? metrics : Map.of();

        copyString(out, mp, "classifierStatus");
        copyString(out, mp, KEY_CLASSIFIER_CONFIDENCE);
        copyString(out, mp, "classifierModelId");
        copyString(out, mp, "classifierModelIdUsed");
        copyString(out, mp, KEY_CLASSIFIER_LABEL_SET_HASH);
        copyString(out, mp, KEY_CLASSIFIER_FALLBACK_REASON);
        copyBool(out, mp, "classifierFallback");
        copyBool(out, mp, KEY_ROUTE_SUPPRESSED_BY_CLASSIFIER);
        copyString(out, mp, KEY_ROUTE_SUPPRESSED_REASON);
        copyBool(out, mp, KEY_HEURISTIC_ROUTE_USED);

        String classifierStatus = str(mp.get("classifierStatus"));
        String predicted = firstNonBlank(str(mp.get("queryTypePredicted")), str(mp.get("predictedQueryType")));
        if (predicted.isBlank()) {
            predicted = str(mp.get("classifierLabel"));
        }
        if ("OK".equalsIgnoreCase(classifierStatus)
                && !predicted.isBlank()
                && !"UNCLASSIFIED".equalsIgnoreCase(predicted)) {
            out.put("queryTypePredicted", predicted);
        }

        out.put(RagPresetToolMetrics.KEY_QUERY_TYPE_SOURCE, deriveQueryTypeSource(mp).name());

        QueryType expected = parseQueryType(str(mp.get(DatasetMetricContract.KEY_QUERY_TYPE_EXPECTED)));
        if (expected == null) {
            expected = parseQueryType(str(mp.get("queryTypeExpected")));
        }
        if (expected != null && !predicted.isBlank() && "OK".equalsIgnoreCase(classifierStatus)) {
            out.put("queryTypeMatch", queryTypeMatch(expected, predicted).name());
        }

        return out;
    }

    private static QueryTypeSource deriveQueryTypeSource(Map<String, Object> mp) {
        String classifierStatus = str(mp.get("classifierStatus"));
        String predicted = firstNonBlank(
                str(mp.get("queryTypePredicted")),
                str(mp.get("predictedQueryType")),
                str(mp.get("classifierLabel")));
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

    private static RagPresetAnalysisMetrics.QueryTypeMatch queryTypeMatch(QueryType expected, String predictedLabel) {
        QueryType predicted = parseQueryType(predictedLabel);
        if (predicted == null) {
            return RagPresetAnalysisMetrics.QueryTypeMatch.UNKNOWN;
        }
        return expected == predicted
                ? RagPresetAnalysisMetrics.QueryTypeMatch.MATCH
                : RagPresetAnalysisMetrics.QueryTypeMatch.MISMATCH;
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
        if (raw instanceof Number n) {
            return n.doubleValue() != 0.0;
        }
        return false;
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return "";
    }
}
