package com.uniovi.rag.application.service.runtime.factual;

public enum FactualVerifierFailureReason {
    DATE_MISMATCH,
    TOPIC_NOT_IN_CONTEXT,
    ENTITY_NOT_IN_CONTEXT,
    NUMERIC_MISMATCH,
    UNRELATED_TOPIC,
    NEGATIVE_FALSE_POSITIVE,
    UNSUPPORTED_POSITIVE_CLAIM
}
