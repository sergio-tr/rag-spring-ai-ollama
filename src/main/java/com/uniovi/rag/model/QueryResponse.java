package com.uniovi.rag.model;

import com.uniovi.rag.services.classifier.QueryType;

/**
 * Represents a query response with metadata about how it was generated.
 */
public class QueryResponse {
    private final String answer;
    private final String toolUsed;
    private final QueryType queryType;
    private final boolean usedTool;

    public QueryResponse(String answer, String toolUsed, QueryType queryType, boolean usedTool) {
        this.answer = answer;
        this.toolUsed = toolUsed;
        this.queryType = queryType;
        this.usedTool = usedTool;
    }

    public String getAnswer() {
        return answer;
    }

    public String getToolUsed() {
        return toolUsed;
    }

    public QueryType getQueryType() {
        return queryType;
    }

    public boolean isUsedTool() {
        return usedTool;
    }

    /**
     * Creates a QueryResponse for a tool-based answer.
     */
    public static QueryResponse fromTool(String answer, String toolName, QueryType queryType) {
        return new QueryResponse(answer, toolName, queryType, true);
    }

    /**
     * Creates a QueryResponse for a direct LLM answer (no tool used).
     */
    public static QueryResponse fromLLM(String answer, QueryType queryType) {
        return new QueryResponse(answer, null, queryType, false);
    }

    /**
     * Creates a QueryResponse for a direct LLM answer with no query type.
     */
    public static QueryResponse fromLLM(String answer) {
        return new QueryResponse(answer, null, null, false);
    }
}

