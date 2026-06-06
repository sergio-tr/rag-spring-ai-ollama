package com.uniovi.rag.application.service.evaluation.config;

/**
 * Stable reason codes for Lab runtime configuration / preset preflight (M4).
 *
 * <p>Distinct from {@link com.uniovi.rag.application.service.evaluation.corpus.LabCorpusReasonCodes}:
 * never use corpus codes for config-only failures.
 */
public final class LabRuntimeConfigReasonCodes {

    private LabRuntimeConfigReasonCodes() {}

    public static final String EXPERIMENTAL_PRESET_CODES_EMPTY = "EXPERIMENTAL_PRESET_CODES_EMPTY";

    public static final String UNSUPPORTED_PRESET = "UNSUPPORTED_PRESET";

    /** P13/P14 or other presets not allowed in single-turn Lab benchmark harness. */
    public static final String PRESET_NOT_LAB_SELECTABLE = "PRESET_NOT_LAB_SELECTABLE";

    /** Alias retained for harness-specific messages. */
    public static final String PRESET_NOT_SINGLE_TURN_BENCHMARK = PRESET_NOT_LAB_SELECTABLE;

    public static final String PRESET_CLARIFICATION_BENCHMARK_NOT_SUPPORTED =
            "PRESET_CLARIFICATION_BENCHMARK_NOT_SUPPORTED";

    public static final String PRESET_CONVERSATIONAL_MEMORY_BENCHMARK_NOT_SUPPORTED =
            "PRESET_CONVERSATIONAL_MEMORY_BENCHMARK_NOT_SUPPORTED";

    public static final String PRESET_NOT_CHAT_SELECTABLE = "PRESET_NOT_CHAT_SELECTABLE";

    public static final String INVALID_RUNTIME_CONFIG = "INVALID_RUNTIME_CONFIG";

    public static final String INCOMPATIBLE_FEATURES = "INCOMPATIBLE_FEATURES";

    public static final String USE_ADVISOR_REQUIRES_RETRIEVAL = "USE_ADVISOR_REQUIRES_RETRIEVAL";

    public static final String STRUCTURED_SEARCH_WITH_RETRIEVAL_NOT_SUPPORTED =
            "STRUCTURED_SEARCH_WITH_RETRIEVAL_NOT_SUPPORTED";

    public static final String FEATURE_REQUIRES_INDEX = "FEATURE_REQUIRES_INDEX";

    public static final String FEATURE_REQUIRES_SNAPSHOT = "FEATURE_REQUIRES_SNAPSHOT";

    public static final String FEATURE_REQUIRES_REINDEX = "FEATURE_REQUIRES_REINDEX";

    /** Alias for index rebuild required by preset tier. */
    public static final String REINDEX_REQUIRED = FEATURE_REQUIRES_REINDEX;

    public static final String SNAPSHOT_CONFIG_MISMATCH = "SNAPSHOT_CONFIG_MISMATCH";

    /** Alias used in job rows and legacy copy. */
    public static final String NO_COMPATIBLE_SNAPSHOT = SNAPSHOT_CONFIG_MISMATCH;

    public static final String EMBEDDING_DIMENSION_MISMATCH = "EMBEDDING_DIMENSION_MISMATCH";

    public static final String RUNTIME_FEATURE_NOT_IMPLEMENTED = "RUNTIME_FEATURE_NOT_IMPLEMENTED";

    public static final String BLOCKED_BY_MODEL_AVAILABILITY = "BLOCKED_BY_MODEL_AVAILABILITY";

    public static final String CONFIG_VALIDATION_ERROR = "CONFIG_VALIDATION_ERROR";

    public static boolean isConfigReasonCode(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String code = value.contains(":") ? value.substring(0, value.indexOf(':')).trim() : value.trim();
        return code.matches("^[A-Z][A-Z0-9_]+$");
    }
}
