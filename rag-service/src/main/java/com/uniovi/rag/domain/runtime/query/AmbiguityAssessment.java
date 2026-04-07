package com.uniovi.rag.domain.runtime.query;

import java.util.List;
import java.util.Objects;

public record AmbiguityAssessment(
        AmbiguityStatus status,
        List<String> reasons,
        List<String> missingFields) {

    public AmbiguityAssessment {
        status = Objects.requireNonNull(status, "status");
        reasons = List.copyOf(Objects.requireNonNull(reasons, "reasons"));
        missingFields = List.copyOf(Objects.requireNonNull(missingFields, "missingFields"));
    }

    public static AmbiguityAssessment sufficient() {
        return new AmbiguityAssessment(AmbiguityStatus.SUFFICIENT, List.of(), List.of());
    }
}

