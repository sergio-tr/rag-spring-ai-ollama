package com.uniovi.rag.domain.runtime.tool;

/**
 * Closed set of deterministic tool kinds for P7 (meeting-minutes structured tools).
 */
public enum DeterministicToolKind {
    COUNT_DOCUMENTS_TOOL,
    FIND_PARAGRAPH_TOOL,
    GET_FIELD_TOOL,
    BOOLEAN_QUERY_TOOL,
    COUNT_AND_EXPLAIN_TOOL
}
