package com.uniovi.rag.application.service.runtime.judge;

import com.uniovi.rag.application.service.llm.ProviderAwareSecondaryLlmExecutor;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.domain.llm.ResolvedLlmConfig;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.judge.JudgeEvaluation;
import com.uniovi.rag.domain.runtime.judge.JudgeOutcome;
import com.uniovi.rag.application.service.runtime.RuntimePromptBudgeter;
import com.uniovi.rag.configuration.RagRuntimeProperties;
import com.uniovi.rag.testsupport.config.TestConfigurablePromptResolver;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JudgeEvaluatorTest {

    private static ExecutionContext testCtx() {
        return mock(ExecutionContext.class);
    }

    private static void stubExecutor(ProviderAwareSecondaryLlmExecutor executor, String response) {
        when(executor.complete(
                        any(ExecutionContext.class),
                        eq(JudgeEvaluator.OPERATION_RUNTIME_JUDGE),
                        isNull(),
                        anyString(),
                        eq(ProviderAwareSecondaryLlmExecutor.SECONDARY_TASK_DEFAULT_TEMPERATURE)))
                .thenReturn(response);
        when(executor.effectiveConfig(any(ExecutionContext.class)))
                .thenReturn(
                        ResolvedLlmConfig.uniform(
                                LlmProvider.OPENAI_COMPATIBLE,
                                "http://litellm:4000",
                                "test-model",
                                "emb-model",
                                "OPENAI_COMPATIBLE_API_KEY",
                                null,
                                0.0,
                                60_000,
                                null,
                                Map.of()));
    }

    @Test
    void evaluate_mapsAcceptedLabel() {
        ProviderAwareSecondaryLlmExecutor executor = mock(ProviderAwareSecondaryLlmExecutor.class);
        stubExecutor(executor, "ACCEPTED\nFEEDBACK: ok");

        JudgeEvaluator evaluator = new JudgeEvaluator(executor, new RuntimePromptBudgeter(new RagRuntimeProperties()), TestConfigurablePromptResolver.defaultsOnly());
        JudgeEvaluation r = evaluator.evaluate(testCtx(), "q?", "answer", true);

        assertThat(r.outcome()).isEqualTo(JudgeOutcome.ACCEPTED);
        assertThat(r.feedback()).contains("ok");
        assertThat(r.stageTraces()).hasSize(1);
        assertThat(r.stageTraces().getFirst().message()).contains("operation=runtime-judge");
    }

    @Test
    void evaluate_mapsRejectedWhenNoRetry() {
        ProviderAwareSecondaryLlmExecutor executor = mock(ProviderAwareSecondaryLlmExecutor.class);
        stubExecutor(executor, "REJECTED_NO_RETRY");

        JudgeEvaluator evaluator = new JudgeEvaluator(executor, new RuntimePromptBudgeter(new RagRuntimeProperties()), TestConfigurablePromptResolver.defaultsOnly());
        JudgeEvaluation r = evaluator.evaluate(testCtx(), "q", "bad", false);

        assertThat(r.outcome()).isEqualTo(JudgeOutcome.REJECTED_NO_RETRY);
    }

    @Test
    void evaluate_retryRequestedOnlyWhenAllowed() {
        ProviderAwareSecondaryLlmExecutor allowedExec = mock(ProviderAwareSecondaryLlmExecutor.class);
        stubExecutor(allowedExec, "RETRY_REQUESTED");
        JudgeEvaluator allowed = new JudgeEvaluator(allowedExec, new RuntimePromptBudgeter(new RagRuntimeProperties()), TestConfigurablePromptResolver.defaultsOnly());
        assertThat(allowed.evaluate(testCtx(), "q", "a", true).outcome()).isEqualTo(JudgeOutcome.RETRY_REQUESTED);

        ProviderAwareSecondaryLlmExecutor disallowedExec = mock(ProviderAwareSecondaryLlmExecutor.class);
        stubExecutor(disallowedExec, "RETRY_REQUESTED");
        JudgeEvaluator disallowed = new JudgeEvaluator(disallowedExec, new RuntimePromptBudgeter(new RagRuntimeProperties()), TestConfigurablePromptResolver.defaultsOnly());
        assertThat(disallowed.evaluate(testCtx(), "q", "a", false).outcome()).isEqualTo(JudgeOutcome.REJECTED_NO_RETRY);
    }

    @Test
    void evaluate_failedSafe_onUnknownLabel() {
        ProviderAwareSecondaryLlmExecutor executor = mock(ProviderAwareSecondaryLlmExecutor.class);
        stubExecutor(executor, "MAYBE");

        JudgeEvaluator evaluator = new JudgeEvaluator(executor, new RuntimePromptBudgeter(new RagRuntimeProperties()), TestConfigurablePromptResolver.defaultsOnly());
        assertThat(evaluator.evaluate(testCtx(), "q", "a", true).outcome()).isEqualTo(JudgeOutcome.FAILED_SAFE);
    }

    @Test
    void evaluate_failedSafe_whenClientThrows() {
        ProviderAwareSecondaryLlmExecutor executor = mock(ProviderAwareSecondaryLlmExecutor.class);
        when(executor.complete(
                        any(ExecutionContext.class),
                        anyString(),
                        isNull(),
                        anyString(),
                        any()))
                .thenThrow(new RuntimeException("boom"));

        JudgeEvaluator evaluator = new JudgeEvaluator(executor, new RuntimePromptBudgeter(new RagRuntimeProperties()), TestConfigurablePromptResolver.defaultsOnly());
        JudgeEvaluation r = evaluator.evaluate(testCtx(), "q", "a", true);
        assertThat(r.outcome()).isEqualTo(JudgeOutcome.FAILED_SAFE);
        assertThat(r.stageTraces().getFirst().outcome().name()).contains("FAILED");
    }

    @Test
    void evaluate_truncatesCandidateAnswerInPrompt() {
        ProviderAwareSecondaryLlmExecutor executor = mock(ProviderAwareSecondaryLlmExecutor.class);
        when(executor.complete(
                        any(ExecutionContext.class),
                        eq(JudgeEvaluator.OPERATION_RUNTIME_JUDGE),
                        isNull(),
                        contains("...[context truncated]"),
                        any()))
                .thenReturn("ACCEPTED");
        when(executor.effectiveConfig(any(ExecutionContext.class)))
                .thenReturn(
                        ResolvedLlmConfig.uniform(
                                LlmProvider.OPENAI_COMPATIBLE,
                                "http://litellm:4000",
                                "test-model",
                                "emb-model",
                                "OPENAI_COMPATIBLE_API_KEY",
                                null,
                                0.0,
                                60_000,
                                null,
                                Map.of()));
        RagRuntimeProperties props = new RagRuntimeProperties();
        props.getContext().setJudgeMaxAnswerChars(32);
        JudgeEvaluator evaluator = new JudgeEvaluator(executor, new RuntimePromptBudgeter(props), TestConfigurablePromptResolver.defaultsOnly());
        String longAnswer = "x".repeat(200);
        evaluator.evaluate(testCtx(), "q", longAnswer, true);
        verify(executor)
                .complete(
                        any(ExecutionContext.class),
                        eq(JudgeEvaluator.OPERATION_RUNTIME_JUDGE),
                        isNull(),
                        contains("...[context truncated]"),
                        any());
    }
}
