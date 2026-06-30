package com.uniovi.rag.application.service.runtime.judge;

import com.uniovi.rag.application.service.llm.ProviderAwareSecondaryLlmExecutor;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.domain.llm.ResolvedLlmConfig;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.engine.ExecutionStageOutcome;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JudgeRetryExecutorTest {

    private static ExecutionContext testCtx() {
        return mock(ExecutionContext.class);
    }

    private static void stubConfig(ProviderAwareSecondaryLlmExecutor executor) {
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
    void retry_whenModelReturnsNonEmptyText_returnsSuccessWithStageTrace() {
        ProviderAwareSecondaryLlmExecutor executor = mock(ProviderAwareSecondaryLlmExecutor.class);
        when(executor.complete(
                        any(ExecutionContext.class),
                        eq(JudgeRetryExecutor.OPERATION_RUNTIME_JUDGE_RETRY),
                        isNull(),
                        anyString(),
                        eq(ProviderAwareSecondaryLlmExecutor.SECONDARY_TASK_DEFAULT_TEMPERATURE)))
                .thenReturn("  repaired  ");
        stubConfig(executor);

        JudgeRetryExecutor exec = new JudgeRetryExecutor(executor);
        var res = exec.retry(testCtx(), "q", "candidate", "feedback");

        assertThat(res.success()).isTrue();
        assertThat(res.answerText()).isEqualTo("repaired");
        assertThat(res.stageTraces()).hasSize(1);
        assertThat(res.stageTraces().getFirst().outcome()).isEqualTo(ExecutionStageOutcome.SUCCESS);
        assertThat(res.stageTraces().getFirst().message()).contains("operation=runtime-judge-retry");
    }

    @Test
    void retry_whenModelReturnsBlank_returnsFailedWithEmptyResponseTrace() {
        ProviderAwareSecondaryLlmExecutor executor = mock(ProviderAwareSecondaryLlmExecutor.class);
        when(executor.complete(any(), anyString(), isNull(), anyString(), any())).thenReturn("   ");
        stubConfig(executor);

        JudgeRetryExecutor exec = new JudgeRetryExecutor(executor);
        var res = exec.retry(testCtx(), "q", "candidate", "feedback");

        assertThat(res.success()).isFalse();
        assertThat(res.answerText()).isEmpty();
        assertThat(res.stageTraces()).hasSize(1);
        assertThat(res.stageTraces().getFirst().outcome()).isEqualTo(ExecutionStageOutcome.FAILED);
        assertThat(res.stageTraces().getFirst().message()).contains("empty_response=true");
    }

    @Test
    void retry_whenChatClientThrows_returnsFailedWithErrorTypeTrace() {
        ProviderAwareSecondaryLlmExecutor executor = mock(ProviderAwareSecondaryLlmExecutor.class);
        when(executor.complete(any(), anyString(), isNull(), anyString(), any()))
                .thenThrow(new RuntimeException("down"));

        JudgeRetryExecutor exec = new JudgeRetryExecutor(executor);
        var res = exec.retry(testCtx(), "q", "candidate", "feedback");

        assertThat(res.success()).isFalse();
        assertThat(res.answerText()).isEmpty();
        assertThat(res.stageTraces()).hasSize(1);
        assertThat(res.stageTraces().getFirst().outcome()).isEqualTo(ExecutionStageOutcome.FAILED);
        assertThat(res.stageTraces().getFirst().message()).contains("error=RuntimeException");
    }
}
