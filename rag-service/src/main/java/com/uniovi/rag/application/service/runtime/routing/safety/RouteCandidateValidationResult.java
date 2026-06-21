package com.uniovi.rag.application.service.runtime.routing.safety;

import java.util.List;

public record RouteCandidateValidationResult(
        boolean safe,
        double confidence,
        String constraintCoverageStatus,
        List<String> rejectionReasons) {

    public static RouteCandidateValidationResult accepted(double confidence, String coverage) {
        return new RouteCandidateValidationResult(true, confidence, coverage, List.of());
    }

    public static RouteCandidateValidationResult rejected(String reason) {
        return new RouteCandidateValidationResult(false, 0.0, "FAILED", List.of(reason));
    }

    public static RouteCandidateValidationResult rejected(List<String> reasons) {
        return new RouteCandidateValidationResult(false, 0.0, "FAILED", List.copyOf(reasons));
    }
}
