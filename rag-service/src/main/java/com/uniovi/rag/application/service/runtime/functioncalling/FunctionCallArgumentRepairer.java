package com.uniovi.rag.application.service.runtime.functioncalling;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolKind;

/** Applies a single deterministic repair pass to model-supplied function-call JSON. */
public final class FunctionCallArgumentRepairer {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private FunctionCallArgumentRepairer() {}

    public static String repairOnce(String argumentsJson, DeterministicToolKind kind, QueryPlan plan) {
        if (argumentsJson == null || argumentsJson.isBlank()) {
            return null;
        }
        JsonNode root;
        try {
            root = MAPPER.readTree(argumentsJson);
        } catch (Exception e) {
            return null;
        }
        if (root == null || !root.isObject()) {
            return null;
        }
        ObjectNode obj = ((ObjectNode) root).deepCopy();
        String rewritten = plan.rewrittenQueryText().trim();
        if (rewritten.isBlank()) {
            return null;
        }
        obj.put("query", rewritten);
        if (kind == DeterministicToolKind.GET_FIELD_TOOL) {
            String field =
                    plan.targetAttributes().stream()
                            .map(String::trim)
                            .filter(s -> !s.isBlank())
                            .findFirst()
                            .orElseGet(() -> plan.slots().getOrDefault("field", "").trim());
            if (field.isBlank()) {
                return null;
            }
            obj.put("field", field);
        }
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            return null;
        }
    }
}
