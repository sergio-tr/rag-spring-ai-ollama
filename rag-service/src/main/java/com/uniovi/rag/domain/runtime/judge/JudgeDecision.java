package com.uniovi.rag.domain.runtime.judge;

import java.util.List;
import java.util.Objects;

public record JudgeDecision(
        JudgeMode mode,
        JudgeKind kind,
        JudgeCandidateSource candidateSource,
        boolean eligible,
        boolean retryAllowed,
        List<String> reasons,
        List<String> policyNotes
) {
    public JudgeDecision {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(candidateSource, "candidateSource");
        reasons = List.copyOf(reasons == null ? List.of() : reasons);
        policyNotes = List.copyOf(policyNotes == null ? List.of() : policyNotes);
    }
}

