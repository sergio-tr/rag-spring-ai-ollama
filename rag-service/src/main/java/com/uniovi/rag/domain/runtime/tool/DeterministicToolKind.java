package com.uniovi.rag.domain.runtime.tool;

import com.uniovi.rag.domain.model.QueryType;

/**
 * Closed set of deterministic tool kinds for P7 (meeting-minutes structured tools).
 * Each kind maps to exactly one {@link QueryType} for shared {@code @Tool} / {@link com.uniovi.rag.configuration.ToolDescriptor} metadata.
 */
public enum DeterministicToolKind {
    COUNT_DOCUMENTS_TOOL,
    FIND_PARAGRAPH_TOOL,
    GET_FIELD_TOOL,
    BOOLEAN_QUERY_TOOL,
    COUNT_AND_EXPLAIN_TOOL,
    GET_DURATION_TOOL,
    FILTER_AND_LIST_TOOL,
    SUMMARIZE_MEETING_TOOL;

    /**
     * Canonical {@link QueryType} used by {@link com.uniovi.rag.configuration.ToolDescriptor} for tool name and description.
     */
    public QueryType toQueryType() {
        return switch (this) {
            case COUNT_DOCUMENTS_TOOL -> QueryType.COUNT_DOCUMENTS;
            case FIND_PARAGRAPH_TOOL -> QueryType.FIND_PARAGRAPH;
            case GET_FIELD_TOOL -> QueryType.GET_FIELD;
            case BOOLEAN_QUERY_TOOL -> QueryType.BOOLEAN_QUERY;
            case COUNT_AND_EXPLAIN_TOOL -> QueryType.COUNT_AND_EXPLAIN;
            case GET_DURATION_TOOL -> QueryType.GET_DURATION;
            case FILTER_AND_LIST_TOOL -> QueryType.FILTER_AND_LIST;
            case SUMMARIZE_MEETING_TOOL -> QueryType.SUMMARIZE_MEETING;
        };
    }
}

