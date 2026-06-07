package com.uniovi.rag.application.service.runtime.reasoning;

import com.uniovi.rag.configuration.RagReasoningProperties;
import com.uniovi.rag.domain.runtime.RagExecutionContext;
import com.uniovi.rag.domain.runtime.RagExecutionContextHolder;
import com.uniovi.rag.application.result.reasoning.PostStepOutput;
import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.application.result.reasoning.ReasoningPreOutput;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;

import java.util.Locale;

/**
 * Delegates to SIMPLE, COT, or PLAN_AND_VERIFY based on {@link com.uniovi.rag.domain.runtime.RagConfig#reasoningStrategy()}
 * in {@link RagExecutionContextHolder}, falling back to {@link RagReasoningProperties}.
 */
public final class SelectingReasoningStrategy implements ReasoningStrategy {

    private final SimpleReasoningStrategy simple;
    private final COTReasoningStrategy cot;
    private final PlanAndVerifyReasoningStrategy planAndVerify;
    private final RagReasoningProperties defaultProperties;

    public SelectingReasoningStrategy(ChatClient chatClient, RagReasoningProperties defaultProperties) {
        this.simple = new SimpleReasoningStrategy();
        this.cot = new COTReasoningStrategy(chatClient);
        this.planAndVerify = new PlanAndVerifyReasoningStrategy(chatClient);
        this.defaultProperties = defaultProperties;
    }

    private ReasoningStrategy delegate() {
        String s = null;
        RagExecutionContext ctx = RagExecutionContextHolder.get();
        if (ctx != null
                && ctx.resolvedConfig() != null
                && ctx.resolvedConfig().reasoningStrategy() != null
                && !ctx.resolvedConfig().reasoningStrategy().isBlank()) {
            s = ctx.resolvedConfig().reasoningStrategy();
        } else if (defaultProperties != null && defaultProperties.getStrategy() != null) {
            s = defaultProperties.getStrategy();
        }
        if (s == null || s.isBlank()) {
            return simple;
        }
        return switch (s.trim().toUpperCase(Locale.ROOT)) {
            case "COT" -> cot;
            case "PLAN_AND_VERIFY" -> planAndVerify;
            default -> simple;
        };
    }

    @Override
    public ReasoningPreOutput runPreStep(String query, QueryType classification, JSONObject ner, String expandedQuery) {
        return delegate().runPreStep(query, classification, ner, expandedQuery);
    }

    @Override
    public PostStepOutput runPostStep(String query, String context, String draftResponse) {
        return delegate().runPostStep(query, context, draftResponse);
    }
}
