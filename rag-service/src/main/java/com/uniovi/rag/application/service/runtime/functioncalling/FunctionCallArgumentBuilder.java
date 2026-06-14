package com.uniovi.rag.application.service.runtime.functioncalling;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolKind;

/** Builds canonical JSON arguments for whitelisted meeting-minutes tools. */
public final class FunctionCallArgumentBuilder {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private FunctionCallArgumentBuilder() {}

    public static String buildJson(DeterministicToolKind kind, QueryPlan plan) {
        String query = plan.rewrittenQueryText().trim();
        ObjectNode root = MAPPER.createObjectNode();
        root.put("query", query);
        if (kind == DeterministicToolKind.GET_FIELD_TOOL) {
            String field =
                    plan.targetAttributes().stream()
                            .map(String::trim)
                            .filter(s -> !s.isBlank())
                            .findFirst()
                            .orElseGet(() -> plan.slots().getOrDefault("field", "").trim());
            if (field.isBlank()) {
                throw new IllegalArgumentException("missing_field");
            }
            root.put("field", field);
        }
        try {
            return MAPPER.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("json_build_failed", e);
        }
    }
}
