package com.uniovi.rag.domain.runtime.traceregressionsuite;

import java.util.List;
import java.util.UUID;

/**
 * P30 suite request - ordered entries (max size enforced by suite service).
 */
public record RuntimeTraceRegressionSuiteRequest(UUID userId, List<RuntimeTraceRegressionSuiteEntry> entries) {

    public RuntimeTraceRegressionSuiteRequest {
        if (entries != null) {
            entries = List.copyOf(entries);
        }
    }
}
