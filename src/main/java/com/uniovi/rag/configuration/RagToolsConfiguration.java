package com.uniovi.rag.configuration;

import com.uniovi.rag.model.QueryType;
import com.uniovi.rag.tool.Tool;

import java.util.Map;

public class RagToolsConfiguration {

    private final Map<QueryType, Tool> tools;

    public RagToolsConfiguration(Map<QueryType, Tool> tools) {
        this.tools = tools;
    }

    public Tool getTool(QueryType queryType) {
        return tools.get(queryType);
    }
}
