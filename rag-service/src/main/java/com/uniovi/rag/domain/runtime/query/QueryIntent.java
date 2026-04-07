package com.uniovi.rag.domain.runtime.query;

/**
 * Canonical query intent for deterministic planning signals.
 */
public enum QueryIntent {
    COUNT,
    LIST,
    FIND,
    EXPLAIN,
    SUMMARIZE,
    COMPARE,
    EXTRACT_FIELD,
    BOOLEAN_CHECK,
    UNKNOWN
}

