package com.uniovi.rag.application.service.runtime.reasoning;

import com.uniovi.rag.application.result.reasoning.PostStepOutput;
import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.application.result.reasoning.ReasoningPreOutput;
import com.uniovi.rag.testsupport.ChatClientTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

    @Test
    void runPreStep_success_returnsPlan() {
        ChatClient c = ChatClientTestSupport.clientWithUserPromptReturning("1. a\n2. b");
        PlanAndVerifyReasoningStrategy s = new PlanAndVerifyReasoningStrategy(c);
        ReasoningPreOutput out = s.runPreStep("q", QueryType.FIND_PARAGRAPH, null, "expanded q");
        assertEquals("1. a\n2. b", out.thoughtOrPlan());
    }

    @Test
    void runPreStep_nullClassification_usesUnknown() {
        ChatClient c = ChatClientTestSupport.clientWithUserPromptReturning("plan");
        PlanAndVerifyReasoningStrategy s = new PlanAndVerifyReasoningStrategy(c);
        ReasoningPreOutput out = s.runPreStep("q", null, null, "exp");
        assertEquals("plan", out.thoughtOrPlan());
    }

    @Test
    void runPostStep_longContext_truncatesExcerpt() {
        ChatClient c = ChatClientTestSupport.clientWithUserPromptReturning("Yes");
        PlanAndVerifyReasoningStrategy s = new PlanAndVerifyReasoningStrategy(c);
        String ctx = "c".repeat(800);
        PostStepOutput out = s.runPostStep("q", ctx, "draft");
        assertNotNull(out);
        assertTrue(out.verified());
    }

    @Test
    void runPostStep_verifierSaysNo() {
        ChatClient c = ChatClientTestSupport.clientWithUserPromptReturning("No");
        PlanAndVerifyReasoningStrategy s = new PlanAndVerifyReasoningStrategy(c);
        PostStepOutput out = s.runPostStep("q", "short", "draft");
        assertFalse(out.verified());
    }
}
