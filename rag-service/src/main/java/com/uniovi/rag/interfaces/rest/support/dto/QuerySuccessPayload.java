package com.uniovi.rag.interfaces.rest.support.dto;

import com.uniovi.rag.application.model.QueryResponse;

/**
 * Successful {@code GET /api/v4/query} body under {@link ApiResponse#data()}.
 */
public record QuerySuccessPayload(
        String answer,
        String queryType,
        boolean usedTool,
        String toolUsed
) {
    public static QuerySuccessPayload from(QueryResponse r) {
        return new QuerySuccessPayload(
                r.getAnswer(),
                r.getQueryType() != null ? r.getQueryType().name() : null,
                r.isUsedTool(),
                r.getToolUsed()
        );
    }
}
