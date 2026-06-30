package com.uniovi.rag.application.service.runtime.reasoning;

import com.uniovi.rag.application.result.reasoning.PostStepOutput;
import com.uniovi.rag.application.service.llm.ProviderAwareSecondaryLlmExecutor;
import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.application.result.reasoning.ReasoningPreOutput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class COTReasoningStrategyTest {

    private ProviderAwareSecondaryLlmExecutor secondaryLlmExecutor;
    private COTReasoningStrategy strategy;

    @BeforeEach
    void setUp() {
        secondaryLlmExecutor = mock(ProviderAwareSecondaryLlmExecutor.class);
        strategy = new COTReasoningStrategy(secondaryLlmExecutor);
    }

    @Test
    void runPreStep_onException_returnsEmptyThought() {
        when(secondaryLlmExecutor.complete(eq(COTReasoningStrategy.OPERATION_COT_PRE), isNull(), anyString()))
                .thenThrow(new RuntimeException("LLM error"));
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
        when(secondaryLlmExecutor.complete(eq(COTReasoningStrategy.OPERATION_COT_POST), isNull(), anyString()))
                .thenThrow(new RuntimeException("error"));
        PostStepOutput out = strategy.runPostStep("q", "context", "draft");
        assertNotNull(out);
        assertEquals("draft", out.verifiedOrRefinedText());
        assertFalse(out.verified());
    }

    @Test
    void runPreStep_success() {
        when(secondaryLlmExecutor.complete(eq(COTReasoningStrategy.OPERATION_COT_PRE), isNull(), anyString()))
                .thenReturn("Need facts; use search.");
        ReasoningPreOutput out = strategy.runPreStep("q", QueryType.COUNT_DOCUMENTS, null, "expanded");
        assertEquals("Need facts; use search.", out.thoughtOrPlan());
    }

    @Test
    void runPostStep_longContext_usesExcerptBranch() {
        when(secondaryLlmExecutor.complete(eq(COTReasoningStrategy.OPERATION_COT_POST), isNull(), anyString()))
                .thenReturn("Yes");
        String ctx = "x".repeat(600);
        PostStepOutput out = strategy.runPostStep("q", ctx, "draft");
        assertNotNull(out);
        assertTrue(out.verified());
    }
}
