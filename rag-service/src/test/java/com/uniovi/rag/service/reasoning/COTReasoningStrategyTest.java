package com.uniovi.rag.service.reasoning;

import com.uniovi.rag.model.PostStepOutput;
import com.uniovi.rag.model.QueryType;
import com.uniovi.rag.model.ReasoningPreOutput;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class COTReasoningStrategyTest {

    private ChatClient chatClient;
    private COTReasoningStrategy strategy;

    @BeforeEach
    void setUp() {
        chatClient = mock(ChatClient.class);
        strategy = new COTReasoningStrategy(chatClient);
    }

    @Test
    void runPreStep_onException_returnsEmptyThought() {
        when(chatClient.prompt()).thenThrow(new RuntimeException("LLM error"));
        ReasoningPreOutput out = strategy.runPreStep("query", QueryType.COUNT_DOCUMENTS, null, "expanded");
        assertNotNull(out);
        assertEquals("", out.thoughtOrPlan());
    }

    @Test
    void runPostStep_nullContext_returnsNull() {
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
