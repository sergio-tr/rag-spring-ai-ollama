package com.uniovi.rag.service.reasoning;

import com.uniovi.rag.model.PostStepOutput;
import com.uniovi.rag.model.QueryType;
import com.uniovi.rag.model.ReasoningPreOutput;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlanAndVerifyReasoningStrategyTest {

    private ChatClient chatClient;
    private PlanAndVerifyReasoningStrategy strategy;

    @BeforeEach
    void setUp() {
        chatClient = mock(ChatClient.class);
        strategy = new PlanAndVerifyReasoningStrategy(chatClient);
    }

    @Test
    void runPreStep_onException_returnsEmptyThought() {
        when(chatClient.prompt()).thenThrow(new RuntimeException("error"));
        ReasoningPreOutput out = strategy.runPreStep("query", QueryType.FIND_PARAGRAPH, null, "expanded");
        assertNotNull(out);
        assertEquals("", out.thoughtOrPlan());
    }

    @Test
    void runPostStep_nullContextOrDraft_returnsNull() {
        assertNull(strategy.runPostStep("q", null, "draft"));
        assertNull(strategy.runPostStep("q", "ctx", null));
    }

    @Test
    void runPostStep_onException_returnsRefined() {
        when(chatClient.prompt()).thenThrow(new RuntimeException("error"));
        PostStepOutput out = strategy.runPostStep("q", "context", "draft");
        assertNotNull(out);
        assertEquals("draft", out.verifiedOrRefinedText());
        assertFalse(out.verified());
    }
}
