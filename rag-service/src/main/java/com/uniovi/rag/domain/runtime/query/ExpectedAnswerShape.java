package com.uniovi.rag.domain.runtime.query;

/**
 * Planning metadata indicating the expected answer structure.
 */
public enum ExpectedAnswerShape {
    SCALAR_BOOLEAN,
    SCALAR_COUNT,
    LIST,
    PARAGRAPH,
    SUMMARY,
    DECISION_EXTRACTION,
    FIELD_VALUE,
    COMPARISON,
    UNKNOWN
}

