package com.uniovi.rag.application.service.runtime.factual;

import java.util.List;

public record FactualVerifierResult(boolean passed, List<FactualVerifierFailureReason> failures) {

    public FactualVerifierResult {
        failures = List.copyOf(failures != null ? failures : List.of());
    }

    public static FactualVerifierResult pass() {
        return new FactualVerifierResult(true, List.of());
    }

    public static FactualVerifierResult fail(List<FactualVerifierFailureReason> reasons) {
        return new FactualVerifierResult(false, reasons);
    }

    public String primaryFailureCode() {
        return failures.isEmpty() ? "" : failures.get(0).name();
    }
}
