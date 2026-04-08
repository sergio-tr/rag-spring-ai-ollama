package com.uniovi.rag.domain.runtime.retrieval;

import java.util.List;
import java.util.Objects;

public record CompressionOutcome(
        int charsBefore, int charsAfter, int droppedCandidateCount, List<String> rulesApplied) {

    public CompressionOutcome {
        rulesApplied = List.copyOf(Objects.requireNonNull(rulesApplied, "rulesApplied"));
    }
}
