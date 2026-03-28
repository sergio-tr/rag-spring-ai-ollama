package com.uniovi.rag.service.reasoning;

import com.uniovi.rag.model.ReasoningPreOutput;
import com.uniovi.rag.model.QueryType;
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
