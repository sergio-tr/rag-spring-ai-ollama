package com.uniovi.rag.domain.runtime.tool;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Canonical result of deterministic tool resolution / execution (orchestrator-facing).
 */
public record DeterministicToolExecutionResult(
        Optional<DeterministicToolKind> toolKind,
        DeterministicToolOutcome outcome,
        boolean success,
        String answerText,
        Map<String, Object> normalizedPayload,
        List<String> traceNotes) {

    public DeterministicToolExecutionResult {
        toolKind = toolKind == null ? Optional.empty() : toolKind;
        answerText = answerText != null ? answerText : "";
        normalizedPayload = Map.copyOf(Objects.requireNonNull(normalizedPayload, "normalizedPayload"));
        traceNotes = List.copyOf(Objects.requireNonNull(traceNotes, "traceNotes"));
    }

    public static DeterministicToolExecutionResult skipped(
            DeterministicToolOutcome outcome, List<String> reasons, Optional<DeterministicToolKind> kind) {
        return new DeterministicToolExecutionResult(
                kind,
                outcome,
                false,
                "",
                Map.of(),
                List.copyOf(reasons));
    }
}
