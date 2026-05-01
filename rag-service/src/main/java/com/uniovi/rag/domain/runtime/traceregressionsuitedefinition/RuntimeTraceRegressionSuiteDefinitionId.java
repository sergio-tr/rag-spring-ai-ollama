package com.uniovi.rag.domain.runtime.traceregressionsuitedefinition;

import java.util.UUID;

/**
 * Strongly typed regression suite definition id (P33).
 */
public record RuntimeTraceRegressionSuiteDefinitionId(UUID value) {

    public RuntimeTraceRegressionSuiteDefinitionId {
        if (value == null) {
            throw new IllegalArgumentException("definition id must not be null");
        }
    }
}
