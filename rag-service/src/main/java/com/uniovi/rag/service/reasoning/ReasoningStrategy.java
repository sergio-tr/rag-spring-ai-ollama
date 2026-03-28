package com.uniovi.rag.service.reasoning;

import com.uniovi.rag.model.PostStepOutput;
import com.uniovi.rag.model.ReasoningPreOutput;
import com.uniovi.rag.model.QueryType;
import org.json.JSONObject;

/**
 * Strategy for pre- and optional post-reasoning around tool/model execution.
 */
public interface ReasoningStrategy {

    /**
     * Run before execution; may produce thought/plan to feed into context.
     */
    ReasoningPreOutput runPreStep(String query, QueryType classification, JSONObject ner, String expandedQuery);

    /**
     * Run after a draft response is produced; may verify or refine. Default: no post-step.
     */
    default PostStepOutput runPostStep(String query, String context, String draftResponse) {
        return null;
    }
}
