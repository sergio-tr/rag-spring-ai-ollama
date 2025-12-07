package com.uniovi.rag.services.tools;

import com.uniovi.rag.services.classifier.QueryType;
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
