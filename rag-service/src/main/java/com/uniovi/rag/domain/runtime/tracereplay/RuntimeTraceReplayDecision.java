package com.uniovi.rag.domain.runtime.tracereplay;

import java.util.Optional;

/**
 * Eligibility decision before linked input loading (P18).
 */
public record RuntimeTraceReplayDecision(boolean eligible, Optional<RuntimeTraceReplayOutcome> unsupportedOutcome, Optional<String> reasonDetail) {

    public RuntimeTraceReplayDecision {
        unsupportedOutcome = unsupportedOutcome == null ? Optional.empty() : unsupportedOutcome;
        reasonDetail = reasonDetail == null ? Optional.empty() : reasonDetail;
    }

    public static RuntimeTraceReplayDecision ok() {
        return new RuntimeTraceReplayDecision(true, Optional.empty(), Optional.empty());
    }

    public static RuntimeTraceReplayDecision reject(RuntimeTraceReplayOutcome unsupported, Optional<String> reasonDetail) {
        return new RuntimeTraceReplayDecision(false, Optional.of(unsupported), reasonDetail);
    }
}
