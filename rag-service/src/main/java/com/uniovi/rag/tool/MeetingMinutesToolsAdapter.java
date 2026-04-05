package com.uniovi.rag.tool;

import com.uniovi.rag.configuration.RagToolsConfiguration;
import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.service.analyser.QueryAnalyser;

import org.json.JSONObject;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * Facade over RAG tools for deterministic execution by query type.
 * Annotated with Spring AI @Tool for ChatClient.tools() function calling.
 */
public class MeetingMinutesToolsAdapter {

    private final RagToolsConfiguration toolsConfig;
    private final QueryAnalyser analyser;

    public MeetingMinutesToolsAdapter(RagToolsConfiguration toolsConfig, QueryAnalyser analyser) {
        this.toolsConfig = toolsConfig;
        this.analyser = analyser;
    }

    public ToolResult execute(QueryType queryType, String query) {
        com.uniovi.rag.tool.Tool ragTool = toolsConfig.getTool(queryType);
        if (ragTool == null) {
            return new ToolResult("No tool available for query type: " + queryType, "adapter");
        }
        JSONObject ner = (analyser != null) ? analyser.analyse(query) : null;
        ToolExecutionContext ctx = ToolExecutionContext.of(query, queryType, ner);
        return ragTool.execute(ctx);
    }

    @Tool(name = "countDocuments", description = "Count how many documents or meeting minutes fulfil a given condition (e.g. by date, topic, or criteria).")
    public String countDocuments(@ToolParam(description = "User question or search criteria") String query) {
        return run(QueryType.COUNT_DOCUMENTS, query);
    }

    @Tool(name = "findParagraph", description = "Locate literal paragraphs or fragments in meeting minutes that refer to a specific topic or question.")
    public String findParagraph(@ToolParam(description = "User question or search criteria") String query) {
        return run(QueryType.FIND_PARAGRAPH, query);
    }

    @Tool(name = "countAndExplain", description = "Count how many documents deal with a topic and briefly explain what was said in them (e.g. topics, dates, content).")
    public String countAndExplain(@ToolParam(description = "User question or search criteria") String query) {
        return run(QueryType.COUNT_AND_EXPLAIN, query);
    }

    @Tool(name = "extractEntities", description = "Extract people, entities, roles, attendees and other named items mentioned in meeting minutes for the query.")
    public String extractEntities(@ToolParam(description = "User question or search criteria") String query) {
        return run(QueryType.EXTRACT_ENTITIES, query);
    }

    @Tool(name = "summarizeTopic", description = "Summarize what was said about a specific topic in the meeting minutes.")
    public String summarizeTopic(@ToolParam(description = "User question or search criteria") String query) {
        return run(QueryType.SUMMARIZE_TOPIC, query);
    }

    @Tool(name = "summarizeMeeting", description = "Provide an overall summary of one or more complete meetings.")
    public String summarizeMeeting(@ToolParam(description = "User question or search criteria") String query) {
        return run(QueryType.SUMMARIZE_MEETING, query);
    }

    @Tool(name = "booleanQuery", description = "Confirm whether something occurred (e.g. was mentioned, was approved, did someone attend). Answer yes/no.")
    public String booleanQuery(@ToolParam(description = "User question or search criteria") String query) {
        return run(QueryType.BOOLEAN_QUERY, query);
    }

    @Tool(name = "compare", description = "Compare values between meetings or periods (e.g. attendees, duration, number of mentions).")
    public String compare(@ToolParam(description = "User question or search criteria") String query) {
        return run(QueryType.COMPARE, query);
    }

    @Tool(name = "getDuration", description = "Get the duration of a meeting or session from the minutes.")
    public String getDuration(@ToolParam(description = "User question or search criteria") String query) {
        return run(QueryType.GET_DURATION, query);
    }

    @Tool(name = "getField", description = "Get a literal value directly from the minutes (e.g. date, place, president, secretary).")
    public String getField(@ToolParam(description = "User question or search criteria") String query) {
        return run(QueryType.GET_FIELD, query);
    }

    @Tool(name = "filterAndList", description = "Apply multiple filters (e.g. date, topic, criteria) and list the matching results.")
    public String filterAndList(@ToolParam(description = "User question or search criteria") String query) {
        return run(QueryType.FILTER_AND_LIST, query);
    }

    @Tool(name = "extractDecisions", description = "Extract the decisions or agreements recorded in the meeting minutes.")
    public String extractDecisions(@ToolParam(description = "User question or search criteria") String query) {
        return run(QueryType.DECISION_EXTRACTION, query);
    }

    private String run(QueryType queryType, String query) {
        ToolResult result = execute(queryType, query);
        return result != null && result.result() != null ? result.result() : "";
    }
}
