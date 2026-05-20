package com.uniovi.rag.application.service.runtime.reasoning;

import com.uniovi.rag.application.result.reasoning.PostStepOutput;
import com.uniovi.rag.application.result.reasoning.ReasoningPreOutput;
import com.uniovi.rag.domain.model.QueryType;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;

/**
 * Pre-step produces a short plan (steps 1-2-3); post-step verifies the response is supported by context.
 */
public class PlanAndVerifyReasoningStrategy implements ReasoningStrategy {

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

    private final ChatClient chatClient;

    public PlanAndVerifyReasoningStrategy(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public ReasoningPreOutput runPreStep(String query, QueryType classification, JSONObject ner, String expandedQuery) {
        try {
            String prompt = String.format(PRE_PROMPT, expandedQuery, classification != null ? classification.name() : "UNKNOWN");
            String plan = chatClient.prompt().user(prompt).call().content();
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
            String answer = chatClient.prompt().user(prompt).call().content();
            boolean verified = answer != null && answer.trim().toLowerCase().startsWith("yes");
            return new PostStepOutput(draftResponse, verified);
        } catch (Exception e) {
            return PostStepOutput.refined(draftResponse);
        }
    }
}
