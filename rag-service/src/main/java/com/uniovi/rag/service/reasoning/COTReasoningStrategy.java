package com.uniovi.rag.service.reasoning;

import com.uniovi.rag.application.model.PostStepOutput;
import com.uniovi.rag.application.model.ReasoningPreOutput;
import com.uniovi.rag.domain.model.QueryType;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;

/**
 * Chain-of-Thought: pre-step produces short reasoning; optional post-step checks coherence with context.
 */
public class COTReasoningStrategy implements ReasoningStrategy {

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

    private final ChatClient chatClient;

    public COTReasoningStrategy(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public ReasoningPreOutput runPreStep(String query, QueryType classification, JSONObject ner, String expandedQuery) {
        try {
            String prompt = String.format(PRE_PROMPT, expandedQuery, classification != null ? classification.name() : "UNKNOWN");
            String thought = chatClient.prompt().user(prompt).call().content();
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
            String answer = chatClient.prompt().user(prompt).call().content();
            boolean verified = answer != null && answer.trim().toLowerCase().startsWith("yes");
            return new PostStepOutput(draftResponse, verified);
        } catch (Exception e) {
            return PostStepOutput.refined(draftResponse);
        }
    }
}
