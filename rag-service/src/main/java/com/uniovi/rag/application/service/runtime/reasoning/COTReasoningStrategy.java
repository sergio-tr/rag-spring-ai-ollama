package com.uniovi.rag.application.service.runtime.reasoning;

import com.uniovi.rag.application.result.reasoning.PostStepOutput;
import com.uniovi.rag.application.result.reasoning.ReasoningPreOutput;
import com.uniovi.rag.application.service.llm.ProviderAwareSecondaryLlmExecutor;
import com.uniovi.rag.domain.model.QueryType;
import org.json.JSONObject;

/**
 * Chain-of-Thought: pre-step produces short reasoning; optional post-step checks coherence with context.
 */
public class COTReasoningStrategy implements ReasoningStrategy {

    public static final String OPERATION_COT_PRE = "reasoning-cot-pre";
    public static final String OPERATION_COT_POST = "reasoning-cot-post";

    private static final String PRE_PROMPT = """
        Query: %s
        Classified as: %s
        In 1-3 short sentences, state what information is needed and which kind of tool or search fits.
        Reply only with that reasoning, no preamble.
        """;
    private static final String POST_PROMPT = """
        Question: %s
        Context (excerpt): %s
        Draft response: %s
        Is this response coherent with the context? Answer only: Yes or No.
        """;

    private final ProviderAwareSecondaryLlmExecutor secondaryLlmExecutor;

    public COTReasoningStrategy(ProviderAwareSecondaryLlmExecutor secondaryLlmExecutor) {
        this.secondaryLlmExecutor = secondaryLlmExecutor;
    }

    @Override
    public ReasoningPreOutput runPreStep(String query, QueryType classification, JSONObject ner, String expandedQuery) {
        try {
            String prompt = String.format(PRE_PROMPT, expandedQuery, classification != null ? classification.name() : "UNKNOWN");
            String thought = secondaryLlmExecutor.complete(OPERATION_COT_PRE, null, prompt);
            return ReasoningPreOutput.of(thought != null ? thought.trim() : "");
        } catch (Exception e) {
            return ReasoningPreOutput.of("");
        }
    }

    @Override
    public PostStepOutput runPostStep(String query, String context, String draftResponse) {
        if (context == null || draftResponse == null) {
            return null;
        }
        try {
            String excerpt = context.length() > 500 ? context.substring(0, 500) + "..." : context;
            String prompt = String.format(POST_PROMPT, query, excerpt, draftResponse);
            String answer = secondaryLlmExecutor.complete(OPERATION_COT_POST, null, prompt);
            boolean verified = answer != null && answer.trim().toLowerCase().startsWith("yes");
            return new PostStepOutput(draftResponse, verified);
        } catch (Exception e) {
            return PostStepOutput.refined(draftResponse);
        }
    }
}
