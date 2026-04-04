package com.uniovi.rag.configuration;

import com.uniovi.rag.domain.model.QueryType;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Name and description for each tool, for function-calling prompts and Spring AI @Tool metadata.
 */
public final class ToolDescriptor {

    private static final Map<QueryType, Descriptor> DESCRIPTORS = new EnumMap<>(QueryType.class);

    static {
        DESCRIPTORS.put(QueryType.COUNT_DOCUMENTS, new Descriptor("countDocuments",
                "Count how many documents or meeting minutes fulfil a given condition (e.g. by date, topic, or criteria)."));
        DESCRIPTORS.put(QueryType.COUNT_AND_EXPLAIN, new Descriptor("countAndExplain",
                "Count how many documents deal with a topic and briefly explain what was said in them (e.g. topics, dates, content)."));
        DESCRIPTORS.put(QueryType.EXTRACT_ENTITIES, new Descriptor("extractEntities",
                "Extract people, entities, roles, attendees and other named items mentioned in meeting minutes for the query."));
        DESCRIPTORS.put(QueryType.FIND_PARAGRAPH, new Descriptor("findParagraph",
                "Locate literal paragraphs or fragments in meeting minutes that refer to a specific topic or question."));
        DESCRIPTORS.put(QueryType.GET_FIELD, new Descriptor("getField",
                "Get a literal value directly from the minutes (e.g. date, place, president, secretary)."));
        DESCRIPTORS.put(QueryType.BOOLEAN_QUERY, new Descriptor("booleanQuery",
                "Confirm whether something occurred (e.g. was mentioned, was approved, did someone attend). Answer yes/no."));
        DESCRIPTORS.put(QueryType.COMPARE, new Descriptor("compare",
                "Compare values between meetings or periods (e.g. attendees, duration, number of mentions)."));
        DESCRIPTORS.put(QueryType.SUMMARIZE_TOPIC, new Descriptor("summarizeTopic",
                "Summarize what was said about a specific topic in the meeting minutes."));
        DESCRIPTORS.put(QueryType.SUMMARIZE_MEETING, new Descriptor("summarizeMeeting",
                "Provide an overall summary of one or more complete meetings."));
        DESCRIPTORS.put(QueryType.DECISION_EXTRACTION, new Descriptor("extractDecisions",
                "Extract the decisions or agreements recorded in the meeting minutes."));
        DESCRIPTORS.put(QueryType.FILTER_AND_LIST, new Descriptor("filterAndList",
                "Apply multiple filters (e.g. date, topic, criteria) and list the matching results."));
        DESCRIPTORS.put(QueryType.GET_DURATION, new Descriptor("getDuration",
                "Get the duration of a meeting or session from the minutes."));
    }

    public static String getName(QueryType queryType) {
        Descriptor d = DESCRIPTORS.get(queryType);
        return d == null ? queryType.name() : d.name;
    }

    public static String getDescription(QueryType queryType) {
        Descriptor d = DESCRIPTORS.get(queryType);
        return d == null ? "" : d.description;
    }

    public static Map<QueryType, Descriptor> getAll() {
        return Collections.unmodifiableMap(DESCRIPTORS);
    }

    public record Descriptor(String name, String description) {}
}
