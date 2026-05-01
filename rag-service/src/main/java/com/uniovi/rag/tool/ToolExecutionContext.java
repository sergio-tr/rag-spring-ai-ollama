package com.uniovi.rag.tool;

import com.uniovi.rag.domain.model.QueryType;
import org.json.JSONObject;

public record ToolExecutionContext(String query, QueryType queryType, JSONObject nerEntities) {

    public static ToolExecutionContext of(String query) {
        return ToolExecutionContext.of(query, null, null);
    }

    public static ToolExecutionContext of(String query, QueryType queryType) {
        return ToolExecutionContext.of(query, queryType, null);
    }

    public static ToolExecutionContext of(String query, QueryType queryType, JSONObject nerEntities) {
        return new ToolExecutionContext(query, queryType, nerEntities);
    }


}
