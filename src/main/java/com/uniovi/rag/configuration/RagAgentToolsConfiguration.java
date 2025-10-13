package com.uniovi.rag.configuration;

import com.uniovi.rag.services.classifier.QueryType;
import com.uniovi.rag.services.tools.Tool;
import com.uniovi.rag.services.tools.agentic.AgenticTool;

import java.util.List;
import java.util.stream.Collectors;
import java.util.Map;

public class RagAgentToolsConfiguration {

    private final Map<QueryType, AgenticTool> tools;

    public RagAgentToolsConfiguration(Map<QueryType, AgenticTool> tools) {
        this.tools = tools;
    }

    public Tool getTool(QueryType queryType) {
        return tools.get(queryType);
    }

    public List<String> getToolFunctionNames() {
        return tools.values().stream()
            .map(AgenticTool::getFunctionName)
            .collect(Collectors.toList());
    }
}
