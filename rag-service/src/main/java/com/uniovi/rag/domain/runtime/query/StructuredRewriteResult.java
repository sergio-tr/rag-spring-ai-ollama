package com.uniovi.rag.domain.runtime.query;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record StructuredRewriteResult(
        String rewrittenQueryText,
        boolean rewriteApplied,
        List<String> rewriteNotes,
        String rewriteStrategyId,
        List<String> targetEntities,
        List<String> targetAttributes,
        Optional<String> targetAction,
        Map<String, String> slotFilling,
        List<String> constraints) {

    public static final String STRATEGY_STRUCTURED_V1 = "STRUCTURED_V1";

    public StructuredRewriteResult {
        rewrittenQueryText = Objects.requireNonNull(rewrittenQueryText, "rewrittenQueryText");
        rewriteNotes = List.copyOf(Objects.requireNonNull(rewriteNotes, "rewriteNotes"));
        rewriteStrategyId = Objects.requireNonNull(rewriteStrategyId, "rewriteStrategyId");
        targetEntities = List.copyOf(Objects.requireNonNull(targetEntities, "targetEntities"));
        targetAttributes = List.copyOf(Objects.requireNonNull(targetAttributes, "targetAttributes"));
        targetAction = Objects.requireNonNullElseGet(targetAction, Optional::empty);
        slotFilling = Map.copyOf(Objects.requireNonNull(slotFilling, "slotFilling"));
        constraints = List.copyOf(Objects.requireNonNull(constraints, "constraints"));
    }

    public static StructuredRewriteResult identityDisabled(String normalizedText, String note) {
        return new StructuredRewriteResult(
                normalizedText,
                false,
                note == null ? List.of("DISABLED") : List.of("DISABLED: " + note),
                STRATEGY_STRUCTURED_V1,
                List.of(),
                List.of(),
                Optional.empty(),
                Map.of(),
                List.of());
    }

    public static StructuredRewriteResult identityFallback(String normalizedText, String note) {
        return new StructuredRewriteResult(
                normalizedText,
                false,
                note == null ? List.of("FALLBACK") : List.of("FALLBACK: " + note),
                STRATEGY_STRUCTURED_V1,
                List.of(),
                List.of(),
                Optional.empty(),
                Map.of(),
                List.of());
    }
}

