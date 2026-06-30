package com.uniovi.rag.application.service.runtime.reasoning;

import com.uniovi.rag.application.service.llm.ProviderAwareSecondaryLlmExecutor;
import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.configuration.RagReasoningProperties;
import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.domain.runtime.RagExecutionContext;
import com.uniovi.rag.domain.runtime.RagExecutionContextHolder;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SelectingReasoningStrategyTest {

    @AfterEach
    void clear() {
        RagExecutionContextHolder.clear();
    }

    @Test
    void runPreStep_whenConfigStrategyIsCot_delegatesToCotAndReturnsTrimmedThought() {
        ProviderAwareSecondaryLlmExecutor secondaryLlmExecutor = mock(ProviderAwareSecondaryLlmExecutor.class);
        when(secondaryLlmExecutor.complete(eq(COTReasoningStrategy.OPERATION_COT_PRE), isNull(), anyString()))
                .thenReturn("  thought  ");

        RagReasoningProperties props = new RagReasoningProperties();
        props.setStrategy("SIMPLE");

        RagExecutionContextHolder.set(
                RagExecutionContext.forUnscopedExecution(configWithReasoningStrategy("COT"), "t"));

        SelectingReasoningStrategy s = new SelectingReasoningStrategy(secondaryLlmExecutor, props);
        var out = s.runPreStep("q", QueryType.BOOLEAN_QUERY, new JSONObject(), "expanded");

        assertThat(out.thoughtOrPlan()).isEqualTo("thought");
    }

    @Test
    void runPostStep_whenDefaultPropertyPlanAndVerify_delegatesAndVerifiesYesNo() {
        ProviderAwareSecondaryLlmExecutor secondaryLlmExecutor = mock(ProviderAwareSecondaryLlmExecutor.class);
        when(secondaryLlmExecutor.complete(eq(PlanAndVerifyReasoningStrategy.OPERATION_PLAN_POST), isNull(), anyString()))
                .thenReturn("Yes");

        RagReasoningProperties props = new RagReasoningProperties();
        props.setStrategy("PLAN_AND_VERIFY");

        RagExecutionContextHolder.set(
                RagExecutionContext.forUnscopedExecution(configWithReasoningStrategy(""), "t"));

        SelectingReasoningStrategy s = new SelectingReasoningStrategy(secondaryLlmExecutor, props);
        var out = s.runPostStep("q", "ctx", "draft");

        assertThat(out).isNotNull();
        assertThat(out.verifiedOrRefinedText()).isEqualTo("draft");
        assertThat(out.verified()).isTrue();
    }

    @Test
    void runPreStep_whenNoConfigAndNoDefaultStrategy_returnsSimpleNoop() {
        ProviderAwareSecondaryLlmExecutor secondaryLlmExecutor = mock(ProviderAwareSecondaryLlmExecutor.class);

        RagExecutionContextHolder.set(
                RagExecutionContext.forUnscopedExecution(configWithReasoningStrategy("   "), "t"));

        SelectingReasoningStrategy s = new SelectingReasoningStrategy(secondaryLlmExecutor, null);
        var out = s.runPreStep("q", null, null, "expanded");

        assertThat(out.thoughtOrPlan()).isEmpty();
    }

    private static RagConfig configWithReasoningStrategy(String strategy) {
        RagFeatureConfiguration fc = new RagFeatureConfiguration();
        fc.setReasoningEnabled(true);
        return RagConfig.fromFeatureConfiguration(fc, 10, 0.7, "llm", "emb", "clf", strategy);
    }
}
