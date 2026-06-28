package com.uniovi.rag.application.service.evaluation.metrics.matching;

/** Deterministic expected-answer match classification for calibrated evaluation. */
public enum ExpectedAnswerMatchType {
    RAW_CONTAINS,
    NORMALIZED_CONTAINS,
    NEGATIVE_EQUIVALENCE,
    CORRECT_ABSTENTION,
    NUMERIC_VALUE_MATCH,
    DATE_VALUE_MATCH,
    ENTITY_SET_MATCH,
    STRUCTURED_TOOL_MATCH,
    SEMANTIC_SUPPORT_ONLY,
    NO_MATCH,
    UNSAFE_TO_JUDGE
}
