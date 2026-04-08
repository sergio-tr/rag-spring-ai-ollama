package com.uniovi.rag.application.service.runtime.functioncalling;

import com.uniovi.rag.domain.runtime.tool.DeterministicToolKind;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Production whitelist: exactly five {@link DeterministicToolKind} values (§9.3).
 */
@Component
public class DefaultFunctionCallingToolRegistry implements FunctionCallingToolRegistry {

    private final Map<DeterministicToolKind, ToolCallback> byKind = new EnumMap<>(DeterministicToolKind.class);

    public DefaultFunctionCallingToolRegistry() {
        byKind.put(
                DeterministicToolKind.COUNT_DOCUMENTS_TOOL,
                stubCallback(
                        "COUNT_DOCUMENTS_TOOL",
                        "Count documents matching the query.",
                        schemaSingleQuery()));
        byKind.put(
                DeterministicToolKind.FIND_PARAGRAPH_TOOL,
                stubCallback(
                        "FIND_PARAGRAPH_TOOL",
                        "Find a relevant paragraph for the query.",
                        schemaSingleQuery()));
        byKind.put(
                DeterministicToolKind.GET_FIELD_TOOL,
                stubCallback(
                        "GET_FIELD_TOOL",
                        "Extract a field value for the query.",
                        schemaGetField()));
        byKind.put(
                DeterministicToolKind.BOOLEAN_QUERY_TOOL,
                stubCallback(
                        "BOOLEAN_QUERY_TOOL",
                        "Answer a boolean question about the corpus.",
                        schemaSingleQuery()));
        byKind.put(
                DeterministicToolKind.COUNT_AND_EXPLAIN_TOOL,
                stubCallback(
                        "COUNT_AND_EXPLAIN_TOOL",
                        "Count matching documents and explain briefly.",
                        schemaSingleQuery()));
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

    private static ToolCallback stubCallback(String name, String description, String inputSchema) {
        return FunctionToolCallback.builder(name, (String args) -> "")
                .description(description)
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
