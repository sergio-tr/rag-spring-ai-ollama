package com.uniovi.rag.application.service.runtime.tool;

import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolKind;

public final class DeterministicToolKindMappings {

    private DeterministicToolKindMappings() {
    }

    public static QueryType toQueryType(DeterministicToolKind kind) {
        return switch (kind) {
            case COUNT_DOCUMENTS_TOOL -> QueryType.COUNT_DOCUMENTS;
            case FIND_PARAGRAPH_TOOL -> QueryType.FIND_PARAGRAPH;
            case GET_FIELD_TOOL -> QueryType.GET_FIELD;
            case BOOLEAN_QUERY_TOOL -> QueryType.BOOLEAN_QUERY;
            case COUNT_AND_EXPLAIN_TOOL -> QueryType.COUNT_AND_EXPLAIN;
        };
    }
}
