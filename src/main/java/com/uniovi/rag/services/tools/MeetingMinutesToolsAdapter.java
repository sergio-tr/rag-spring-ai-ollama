package com.uniovi.rag.services.tools;

import com.uniovi.rag.configuration.RagToolsConfiguration;
import com.uniovi.rag.services.analyser.QueryAnalyser;
import com.uniovi.rag.services.classifier.QueryType;
import org.json.JSONObject;

/**
 * Facade over RAG tools for deterministic execution by query type.
 * When Spring AI supports ChatClient.tools(), add @Tool/@ToolParam and use this as the tools bean.
 */
public class MeetingMinutesToolsAdapter {

    private final RagToolsConfiguration toolsConfig;
    private final QueryAnalyser analyser;

    public MeetingMinutesToolsAdapter(RagToolsConfiguration toolsConfig, QueryAnalyser analyser) {
        this.toolsConfig = toolsConfig;
        this.analyser = analyser;
    }

    public ToolResult execute(QueryType queryType, String query) {
        Tool tool = toolsConfig.getTool(queryType);
        if (tool == null) {
            return new ToolResult("No tool available for query type: " + queryType, "adapter");
        }
        JSONObject ner = (analyser != null) ? analyser.analyse(query) : null;
        ToolExecutionContext ctx = ToolExecutionContext.of(query, queryType, ner);
        return tool.execute(ctx);
    }

    public String countDocuments(String query) {
        return run(QueryType.COUNT_DOCUMENTS, query);
    }

    public String findParagraph(String query) {
        return run(QueryType.FIND_PARAGRAPH, query);
    }

    public String countAndExplain(String query) {
        return run(QueryType.COUNT_AND_EXPLAIN, query);
    }

    public String extractEntities(String query) {
        return run(QueryType.EXTRACT_ENTITIES, query);
    }

    public String summarizeTopic(String query) {
        return run(QueryType.SUMMARIZE_TOPIC, query);
    }

    public String summarizeMeeting(String query) {
        return run(QueryType.SUMMARIZE_MEETING, query);
    }

    public String booleanQuery(String query) {
        return run(QueryType.BOOLEAN_QUERY, query);
    }

    public String compare(String query) {
        return run(QueryType.COMPARE, query);
    }

    public String getDuration(String query) {
        return run(QueryType.GET_DURATION, query);
    }

    public String getField(String query) {
        return run(QueryType.GET_FIELD, query);
    }

    public String filterAndList(String query) {
        return run(QueryType.FILTER_AND_LIST, query);
    }

    public String extractDecisions(String query) {
        return run(QueryType.DECISION_EXTRACTION, query);
    }

    private String run(QueryType queryType, String query) {
        ToolResult result = execute(queryType, query);
        return result != null && result.result() != null ? result.result() : "";
    }
}
