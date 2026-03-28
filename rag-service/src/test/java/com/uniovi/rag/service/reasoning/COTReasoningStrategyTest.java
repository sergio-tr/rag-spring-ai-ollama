package com.uniovi.rag.service.reasoning;

import com.uniovi.rag.model.PostStepOutput;
import com.uniovi.rag.model.QueryType;
import com.uniovi.rag.model.ReasoningPreOutput;
import com.uniovi.rag.testsupport.ChatClientTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import static org.junit.jupiter.api.Assertions.*;
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

    @Test
    void runPreStep_success() {
        ChatClient c = ChatClientTestSupport.clientWithUserPromptReturning("Need facts; use search.");
        COTReasoningStrategy s = new COTReasoningStrategy(c);
        ReasoningPreOutput out = s.runPreStep("q", QueryType.COUNT_DOCUMENTS, null, "expanded");
        assertEquals("Need facts; use search.", out.thoughtOrPlan());
    }

    @Test
    void runPostStep_longContext_usesExcerptBranch() {
        ChatClient c = ChatClientTestSupport.clientWithUserPromptReturning("Yes");
        COTReasoningStrategy s = new COTReasoningStrategy(c);
        String ctx = "x".repeat(600);
        PostStepOutput out = s.runPostStep("q", ctx, "draft");
        assertNotNull(out);
        assertTrue(out.verified());
    }
}
