package com.uniovi.rag.domain.runtime.advisor;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Result of {@code AdvisorStrategy.execute}; not persisted on {@code ExecutionContext}.
 */
public record AdvisorExecutionResult(
        AdvisorOutcome outcome,
        boolean shortCircuitedContextPrep,
        Optional<PackedContextSet> packedContextSet,
        List<String> traceNotes) {

    public AdvisorExecutionResult {
        traceNotes = List.copyOf(Objects.requireNonNull(traceNotes, "traceNotes"));
        packedContextSet = Objects.requireNonNull(packedContextSet, "packedContextSet");
    }

    public static AdvisorExecutionResult success(PackedContextSet packed) {
        return new AdvisorExecutionResult(
                AdvisorOutcome.EXECUTED_SUCCESS, true, Optional.of(packed), List.of());
    }

    public static AdvisorExecutionResult failedRetrieval(List<String> notes) {
        return new AdvisorExecutionResult(AdvisorOutcome.EXECUTED_FAILED_RETRIEVAL, false, Optional.empty(), notes);
    }

    public static AdvisorExecutionResult failedPacking(List<String> notes) {
        return new AdvisorExecutionResult(AdvisorOutcome.EXECUTED_FAILED_PACKING, false, Optional.empty(), notes);
    }

    public static AdvisorExecutionResult failedReservedKind(List<String> notes) {
        return new AdvisorExecutionResult(AdvisorOutcome.FAILED_RESERVED_KIND, false, Optional.empty(), notes);
    }
}
