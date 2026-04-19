package com.uniovi.rag.application.service.runtime.functioncalling;

import com.uniovi.rag.configuration.ToolDescriptor;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolKind;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Production whitelist: exactly five {@link DeterministicToolKind} values.
 * Tool names and descriptions match {@link ToolDescriptor} / {@link com.uniovi.rag.tool.MeetingMinutesToolsAdapter} {@code @Tool} metadata.
 */
@Component
public class DefaultFunctionCallingToolRegistry implements FunctionCallingToolRegistry {

    private final Map<DeterministicToolKind, ToolCallback> byKind = new EnumMap<>(DeterministicToolKind.class);

    public DefaultFunctionCallingToolRegistry() {
        byKind.put(
                DeterministicToolKind.COUNT_DOCUMENTS_TOOL,
                stubCallback(DeterministicToolKind.COUNT_DOCUMENTS_TOOL, schemaSingleQuery()));
        byKind.put(
                DeterministicToolKind.FIND_PARAGRAPH_TOOL,
                stubCallback(DeterministicToolKind.FIND_PARAGRAPH_TOOL, schemaSingleQuery()));
        byKind.put(
                DeterministicToolKind.GET_FIELD_TOOL,
                stubCallback(DeterministicToolKind.GET_FIELD_TOOL, schemaGetField()));
        byKind.put(
                DeterministicToolKind.BOOLEAN_QUERY_TOOL,
                stubCallback(DeterministicToolKind.BOOLEAN_QUERY_TOOL, schemaSingleQuery()));
        byKind.put(
                DeterministicToolKind.COUNT_AND_EXPLAIN_TOOL,
                stubCallback(DeterministicToolKind.COUNT_AND_EXPLAIN_TOOL, schemaSingleQuery()));
        if (byKind.size() != 5) {
            throw new IllegalStateException("FC registry must contain exactly five tools");
        }
    }

    @Override
    public List<ToolCallback> callbacksFor(Iterable<DeterministicToolKind> kinds) {
        List<ToolCallback> out = new ArrayList<>();
        for (DeterministicToolKind k : kinds) {
            ToolCallback cb = byKind.get(k);
            if (cb == null) {
                throw new IllegalArgumentException("Unknown tool kind for FC registry: " + k);
            }
            out.add(cb);
        }
        return List.copyOf(out);
    }

    private static ToolCallback stubCallback(DeterministicToolKind kind, String inputSchema) {
        var qt = kind.toQueryType();
        return FunctionToolCallback.builder(ToolDescriptor.getName(qt), (String args) -> "")
                .description(ToolDescriptor.getDescription(qt))
                .inputSchema(inputSchema)
                .inputType(String.class)
                .build();
    }

    private static String schemaSingleQuery() {
        return "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"}},\"required\":[\"query\"],\"additionalProperties\":false}";
    }

    private static String schemaGetField() {
        return "{\"type\":\"object\",\"properties\":{"
                + "\"query\":{\"type\":\"string\"},"
                + "\"field\":{\"type\":\"string\"}"
                + "},\"required\":[\"query\",\"field\"],\"additionalProperties\":false}";
    }
}
