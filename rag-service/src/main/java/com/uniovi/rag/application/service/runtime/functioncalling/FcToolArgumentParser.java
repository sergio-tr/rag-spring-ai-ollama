package com.uniovi.rag.application.service.runtime.functioncalling;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolKind;
import java.util.Iterator;
import java.util.Set;

/**
 * Strict JSON argument validation for FC (§10.9).
 */
public final class FcToolArgumentParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String FIELD_QUERY = "query";

    private FcToolArgumentParser() {
    }

    public static ParsedArgs parseOrThrow(String argumentsJson, DeterministicToolKind kind, QueryPlan plan)
            throws IllegalArgumentException {
        if (argumentsJson == null || argumentsJson.isBlank()) {
            throw new IllegalArgumentException("empty_arguments");
        }
        JsonNode root;
        try {
            root = MAPPER.readTree(argumentsJson);
        } catch (Exception e) {
            throw new IllegalArgumentException("json_parse_failed");
        }
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("root_not_object");
        }
        ObjectNode obj = (ObjectNode) root;
        String rewritten = plan.rewrittenQueryText().trim();
        return switch (kind) {
            case COUNT_DOCUMENTS_TOOL, FIND_PARAGRAPH_TOOL, BOOLEAN_QUERY_TOOL, COUNT_AND_EXPLAIN_TOOL -> parseSingleQuery(
                    obj, rewritten);
            case GET_FIELD_TOOL -> parseGetField(obj, rewritten, plan);
        };
    }

    private static ParsedArgs parseSingleQuery(ObjectNode obj, String rewritten) {
        assertNoExtraKeys(obj, FIELD_QUERY);
        String q = requiredText(obj, FIELD_QUERY);
        if (!q.equals(rewritten)) {
            throw new IllegalArgumentException("query_mismatch");
        }
        return new ParsedArgs(q, null);
    }

    private static ParsedArgs parseGetField(ObjectNode obj, String rewritten, QueryPlan plan) {
        assertNoExtraKeys(obj, FIELD_QUERY, "field");
        String q = requiredText(obj, FIELD_QUERY);
        if (!q.equals(rewritten)) {
            throw new IllegalArgumentException("query_mismatch");
        }
        String field = requiredText(obj, "field");
        String expectedField =
                plan.targetAttributes().stream()
                        .map(String::trim)
                        .filter(s -> !s.isBlank())
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("missing_target_attributes"));
        if (!field.equals(expectedField)) {
            throw new IllegalArgumentException("field_mismatch");
        }
        return new ParsedArgs(q, field);
    }

    private static void assertNoExtraKeys(ObjectNode obj, String... allowed) {
        Set<String> allow = Set.of(allowed);
        Iterator<String> it = obj.fieldNames();
        while (it.hasNext()) {
            String n = it.next();
            if (!allow.contains(n)) {
                throw new IllegalArgumentException("extra_field:" + n);
            }
        }
    }

    private static String requiredText(ObjectNode obj, String field) {
        if (!obj.has(field) || obj.get(field).isNull() || !obj.get(field).isTextual()) {
            throw new IllegalArgumentException("missing_or_bad_" + field);
        }
        return obj.get(field).asText();
    }

    public record ParsedArgs(String query, String field) {}
}
