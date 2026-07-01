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

class PlanAndVerifyReasoningStrategyTest {

    private ProviderAwareSecondaryLlmExecutor secondaryLlmExecutor;
    private PlanAndVerifyReasoningStrategy strategy;

    @BeforeEach
    void setUp() {
        secondaryLlmExecutor = mock(ProviderAwareSecondaryLlmExecutor.class);
        strategy = new PlanAndVerifyReasoningStrategy(secondaryLlmExecutor);
    }

    @Test
    void runPreStep_onException_returnsEmptyThought() {
        when(secondaryLlmExecutor.complete(eq(PlanAndVerifyReasoningStrategy.OPERATION_PLAN_PRE), isNull(), anyString()))
                .thenThrow(new RuntimeException("error"));
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
        when(secondaryLlmExecutor.complete(eq(PlanAndVerifyReasoningStrategy.OPERATION_PLAN_POST), isNull(), anyString()))
                .thenThrow(new RuntimeException("error"));
        PostStepOutput out = strategy.runPostStep("q", "context", "draft");
        assertNotNull(out);
        assertEquals("draft", out.verifiedOrRefinedText());
        assertFalse(out.verified());
    }

    @Test
    void runPreStep_success_returnsPlan() {
        when(secondaryLlmExecutor.complete(eq(PlanAndVerifyReasoningStrategy.OPERATION_PLAN_PRE), isNull(), anyString()))
                .thenReturn("1. a\n2. b");
        ReasoningPreOutput out = strategy.runPreStep("q", QueryType.FIND_PARAGRAPH, null, "expanded q");
        assertEquals("1. a\n2. b", out.thoughtOrPlan());
    }

    @Test
    void runPreStep_nullClassification_usesUnknown() {
        when(secondaryLlmExecutor.complete(eq(PlanAndVerifyReasoningStrategy.OPERATION_PLAN_PRE), isNull(), anyString()))
                .thenReturn("plan");
        ReasoningPreOutput out = strategy.runPreStep("q", null, null, "exp");
        assertEquals("plan", out.thoughtOrPlan());
    }

    @Test
    void runPostStep_longContext_truncatesExcerpt() {
        when(secondaryLlmExecutor.complete(eq(PlanAndVerifyReasoningStrategy.OPERATION_PLAN_POST), isNull(), anyString()))
                .thenReturn("Yes");
        String ctx = "c".repeat(800);
        PostStepOutput out = strategy.runPostStep("q", ctx, "draft");
        assertNotNull(out);
        assertTrue(out.verified());
    }

    @Test
    void runPostStep_verifierSaysNo() {
        when(secondaryLlmExecutor.complete(eq(PlanAndVerifyReasoningStrategy.OPERATION_PLAN_POST), isNull(), anyString()))
                .thenReturn("No");
        PostStepOutput out = strategy.runPostStep("q", "short", "draft");
        assertFalse(out.verified());
    }
}
