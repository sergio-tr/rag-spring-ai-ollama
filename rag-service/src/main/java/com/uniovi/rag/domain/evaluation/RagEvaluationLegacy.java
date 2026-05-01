package com.uniovi.rag.domain.evaluation;

/**
 * Marker for legacy combinatorial evaluation ({@code evaluateAllConfigurations}, {@code GET /evaluate/all}).
 * Not part of the primary scientific benchmark flow.
 */
public final class RagEvaluationLegacy {

    public static final String LEGACY_COMBINATORIAL = "LEGACY_COMBINATORIAL";

    /** JSON field on {@code GET /evaluate/all} wrapping the configuration map. */
    public static final String RESPONSE_KEY_LEGACY_MODE = "legacyEvaluationMode";

    private RagEvaluationLegacy() {
    }
}
