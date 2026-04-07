package com.uniovi.rag.domain.runtime.tool;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Structured deterministic tool decision produced only by {@link com.uniovi.rag.application.service.runtime.tool.DeterministicToolResolver}.
 */
public record DeterministicToolDecision(
        ToolExecutionMode executionMode,
        DeterministicToolOutcome outcome,
        boolean selected,
        Optional<DeterministicToolKind> selectedToolKind,
        List<String> reasons,
        Map<String, String> normalizedInputs,
        Optional<String> suppressionReason,
        Optional<String> fallbackPolicy) {

    public DeterministicToolDecision {
        reasons = List.copyOf(Objects.requireNonNull(reasons, "reasons"));
        normalizedInputs = Map.copyOf(Objects.requireNonNull(normalizedInputs, "normalizedInputs"));
        selectedToolKind = selectedToolKind == null ? Optional.empty() : selectedToolKind;
        suppressionReason = suppressionReason == null ? Optional.empty() : suppressionReason;
        fallbackPolicy = fallbackPolicy == null ? Optional.empty() : fallbackPolicy;
    }
}
