package com.uniovi.rag.application.model;

import com.uniovi.rag.domain.model.QueryType;
import java.util.List;
import java.util.Map;

/**
 * Represents a query response with metadata about how it was generated.
 */
public class QueryResponse {
    private final String answer;
    private final String toolUsed;
    private final QueryType queryType;
    private final boolean usedTool;
    private final List<Map<String, Object>> sources;

    public QueryResponse(
            String answer,
            String toolUsed,
            QueryType queryType,
            boolean usedTool,
            List<Map<String, Object>> sources) {
        this.answer = answer;
        this.toolUsed = toolUsed;
        this.queryType = queryType;
        this.usedTool = usedTool;
        this.sources = sources != null ? List.copyOf(sources) : List.of();
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

    public List<Map<String, Object>> getSources() {
        return sources;
    }

    /**
     * Creates a QueryResponse for a tool-based answer.
     */
    public static QueryResponse fromTool(String answer, String toolName, QueryType queryType) {
        return new QueryResponse(answer, toolName, queryType, true, List.of());
    }

    /**
     * Creates a QueryResponse for a direct LLM answer (no tool used).
     */
    public static QueryResponse fromLLM(String answer, QueryType queryType) {
        return new QueryResponse(answer, null, queryType, false, List.of());
    }

    /**
     * Creates a QueryResponse for a direct LLM answer with no query type.
     */
    public static QueryResponse fromLLM(String answer) {
        return new QueryResponse(answer, null, null, false, List.of());
    }

    public static QueryResponse fromLLMWithSources(
            String answer, QueryType queryType, List<Map<String, Object>> sources) {
        return new QueryResponse(answer, null, queryType, false, sources);
    }

    public static QueryResponse fromToolWithSources(
            String answer, String toolName, QueryType queryType, List<Map<String, Object>> sources) {
        return new QueryResponse(answer, toolName, queryType, true, sources);
    }
}


