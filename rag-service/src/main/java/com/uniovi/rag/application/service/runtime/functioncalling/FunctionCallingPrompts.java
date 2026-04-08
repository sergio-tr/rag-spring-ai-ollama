package com.uniovi.rag.application.service.runtime.functioncalling;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.domain.runtime.query.QueryPlan;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * FC user message text for the first tool-enabled round and the follow-up round (§10.5, §10.6a).
 */
public final class FunctionCallingPrompts {

    private static final ObjectMapper COMPACT_JSON = new ObjectMapper();

    private FunctionCallingPrompts() {
    }

    public static String buildFirstRoundUserMessage(QueryPlan plan) {
        StringBuilder sb = new StringBuilder();
        sb.append(plan.rewrittenQueryText().trim());
        sb.append("\n\n--- Structured context ---\n");
        sb.append("intent=").append(plan.queryIntent().name());
        sb.append("\nexpectedAnswerShape=").append(plan.expectedAnswerShape().name());
        sb.append("\nclassifierLabel=").append(plan.classifierLabel());
        plan.classifierQueryType().ifPresent(qt -> sb.append("\nclassifierQueryType=").append(qt.name()));
        if (!plan.slots().isEmpty()) {
            sb.append("\nslots=");
            sb.append(
                    plan.slots().entrySet().stream()
                            .map(e -> e.getKey() + "=" + e.getValue())
                            .collect(Collectors.joining(", ")));
        }
        if (!plan.targetEntities().isEmpty()) {
            sb.append("\ntargetEntities=").append(String.join(", ", plan.targetEntities()));
        }
        if (!plan.targetAttributes().isEmpty()) {
            sb.append("\ntargetAttributes=").append(String.join(", ", plan.targetAttributes()));
        }
        return sb.toString();
    }

    public static String buildFollowUpUserMessage(QueryPlan plan, Map<String, Object> normalizedPayload) {
        Map<String, Object> sorted = new LinkedHashMap<>(normalizedPayload);
        String payloadJson;
        try {
            payloadJson = COMPACT_JSON.writeValueAsString(sorted);
        } catch (JsonProcessingException e) {
            payloadJson = normalizedPayload.toString();
        }
        return plan.rewrittenQueryText().trim()
                + "\n\n--- Tool result ---\n"
                + payloadJson;
    }
}
