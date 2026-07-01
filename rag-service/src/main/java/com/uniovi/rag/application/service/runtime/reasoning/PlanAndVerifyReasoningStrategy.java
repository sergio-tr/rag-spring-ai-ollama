package com.uniovi.rag.application.service.runtime.reasoning;

import com.uniovi.rag.application.result.reasoning.PostStepOutput;
import com.uniovi.rag.application.result.reasoning.ReasoningPreOutput;
import com.uniovi.rag.application.service.llm.ProviderAwareSecondaryLlmExecutor;
import com.uniovi.rag.domain.model.QueryType;
import org.json.JSONObject;

/**
 * Pre-step produces a short plan (steps 1-2-3); post-step verifies the response is supported by context.
 */
public class PlanAndVerifyReasoningStrategy implements ReasoningStrategy {

    public static final String OPERATION_PLAN_PRE = "reasoning-plan-pre";
    public static final String OPERATION_PLAN_POST = "reasoning-plan-post";

    private static final String PRE_PROMPT = """
        Query: %s
        Type: %s
        Give a very short plan as numbered steps (1. 2. 3.) for answering this query. One line per step.
        Reply only with the plan.
        """;
    private static final String POST_PROMPT = """
        Question: %s
        Context: %s
        Response: %s
        Is the response supported by the context? Answer only: Yes or No.
        """;

    private final ProviderAwareSecondaryLlmExecutor secondaryLlmExecutor;

    public PlanAndVerifyReasoningStrategy(ProviderAwareSecondaryLlmExecutor secondaryLlmExecutor) {
        this.secondaryLlmExecutor = secondaryLlmExecutor;
    }

    @Override
    public ReasoningPreOutput runPreStep(String query, QueryType classification, JSONObject ner, String expandedQuery) {
        try {
            String prompt = String.format(PRE_PROMPT, expandedQuery, classification != null ? classification.name() : "UNKNOWN");
            String plan = secondaryLlmExecutor.complete(OPERATION_PLAN_PRE, null, prompt);
            return ReasoningPreOutput.of(plan != null ? plan.trim() : "");
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
            String excerpt = context.length() > 600 ? context.substring(0, 600) + "..." : context;
            String prompt = String.format(POST_PROMPT, query, excerpt, draftResponse);
            String answer = secondaryLlmExecutor.complete(OPERATION_PLAN_POST, null, prompt);
            boolean verified = answer != null && answer.trim().toLowerCase().startsWith("yes");
            return new PostStepOutput(draftResponse, verified);
        } catch (Exception e) {
            return PostStepOutput.refined(draftResponse);
        }
    }
}
