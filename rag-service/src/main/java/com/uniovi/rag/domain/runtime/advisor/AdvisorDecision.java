package com.uniovi.rag.domain.runtime.advisor;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Policy output from {@code AdvisorPolicyResolver}.
 */
public record AdvisorDecision(
        AdvisorMode mode,
        boolean selected,
        List<AdvisorKind> executableKinds,
        String canonicalQueryText,
        List<String> reasons,
        Optional<AdvisorSuppressionReason> suppressionReason) {

    public static final List<AdvisorKind> EXECUTABLE_KINDS_5_2 =
            List.of(AdvisorKind.RETRIEVAL_ADVISOR, AdvisorKind.CONTEXT_PACKING_ADVISOR);

    public AdvisorDecision {
        executableKinds = List.copyOf(Objects.requireNonNull(executableKinds, "executableKinds"));
        canonicalQueryText = canonicalQueryText != null ? canonicalQueryText : "";
        reasons = List.copyOf(Objects.requireNonNull(reasons, "reasons"));
        suppressionReason = suppressionReason == null ? Optional.empty() : suppressionReason;
        if (selected && !executableKinds.equals(EXECUTABLE_KINDS_5_2)) {
            throw new IllegalArgumentException("When selected, executableKinds must be [RETRIEVAL_ADVISOR, CONTEXT_PACKING_ADVISOR]");
        }
        if (!selected && !executableKinds.isEmpty()) {
            throw new IllegalArgumentException("When not selected, executableKinds must be empty");
        }
    }
}
