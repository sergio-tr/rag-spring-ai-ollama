package com.uniovi.rag.application.service.runtime.reasoning;

import com.uniovi.rag.application.result.reasoning.ReasoningPreOutput;
import com.uniovi.rag.domain.model.QueryType;
import org.json.JSONObject;

/**
 * No-op reasoning: passes through without adding thought or verification.
 */
public class SimpleReasoningStrategy implements ReasoningStrategy {

    @Override
    public ReasoningPreOutput runPreStep(String query, QueryType classification, JSONObject ner, String expandedQuery) {
        return ReasoningPreOutput.of("");
    }
}
