package com.uniovi.rag.application.service.evaluation.export;

import com.uniovi.rag.application.service.evaluation.BenchmarkResultRowKeys;
import com.uniovi.rag.domain.evaluation.BenchmarkItemOutcome;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Stable error buckets for evaluation exports (does not affect scoring). */
public final class EvaluationDerivedErrorClassifier {

    public static final String INDEX_PREPARATION = "INDEX_PREPARATION";
    public static final String EMBEDDING_COMPATIBILITY = "EMBEDDING_COMPATIBILITY";
    public static final String CORPUS_UNAVAILABLE = "CORPUS_UNAVAILABLE";
    public static final String MODEL_UNAVAILABLE = "MODEL_UNAVAILABLE";
    public static final String PRESET_UNSUPPORTED = "PRESET_UNSUPPORTED";
    public static final String CONFIGURATION = "CONFIGURATION";
    public static final String RUNTIME_FAILURE = "RUNTIME_FAILURE";
    public static final String SKIPPED = "SKIPPED";

    private EvaluationDerivedErrorClassifier() {}

    public static Optional<String> classify(String outcome, Map<String, Object> metricsPayload, Map<String, Object> operational) {
        if (outcome == null || outcome.isBlank() || BenchmarkItemOutcome.EXECUTED.name().equals(outcome)) {
            return Optional.empty();
        }
        String code =
                firstNonBlank(
                        read(metricsPayload, BenchmarkResultRowKeys.ERROR_CODE),
                        read(operational, "failureCode"),
                        read(operational, "skipReasonCode"),
                        read(metricsPayload, "skippedReasonCode"),
                        read(metricsPayload, "embedding_compatibility_error_code"));
        if (code.isBlank()) {
            return Optional.of(
                    switch (outcome) {
                        case "NOT_SUPPORTED" -> PRESET_UNSUPPORTED;
                        case "SKIPPED" -> SKIPPED;
                        case "FAILED" -> RUNTIME_FAILURE;
                        default -> RUNTIME_FAILURE;
                    });
        }
        return Optional.of(bucketForCode(code));
    }

    private static String bucketForCode(String code) {
        String upper = code.toUpperCase(Locale.ROOT);
        if (upper.contains("EMBEDDING") || upper.contains("DIMENSION")) {
            return EMBEDDING_COMPATIBILITY;
        }
        if (upper.contains("REINDEX")
                || upper.contains("INDEX")
                || upper.contains("SNAPSHOT")
                || upper.contains("VECTOR")) {
            return INDEX_PREPARATION;
        }
        if (upper.contains("CORPUS")
                || upper.contains("DOCUMENT")
                || upper.contains("KB_")
                || upper.contains("NO_READY")
                || upper.contains("NO_DOCUMENT")) {
            return CORPUS_UNAVAILABLE;
        }
        if (upper.contains("MODEL_UNAVAILABLE") || upper.contains("MODEL")) {
            return MODEL_UNAVAILABLE;
        }
        if (upper.contains("PRESET") || upper.contains("NOT_SUPPORTED") || upper.contains("UNSUPPORTED")) {
            return PRESET_UNSUPPORTED;
        }
        if (upper.contains("CONFIG")
                || upper.contains("FEATURE")
                || upper.contains("COMPATIB")
                || upper.contains("INVALID_RUNTIME")) {
            return CONFIGURATION;
        }
        return RUNTIME_FAILURE;
    }

    private static String read(Map<String, Object> map, String key) {
        if (map == null || key == null) {
            return "";
        }
        Object v = map.get(key);
        return v == null ? "" : String.valueOf(v).trim();
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return "";
    }
}
