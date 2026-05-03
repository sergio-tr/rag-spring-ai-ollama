package com.uniovi.rag.application.service.runtime.judge;

import com.uniovi.rag.domain.runtime.engine.ExecutionStageOutcome;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JudgeRetryExecutorTest {

    @Test
    void retry_whenModelReturnsNonEmptyText_returnsSuccessWithStageTrace() {
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(chatClient.prompt().user(anyString()).call().content())
                .thenReturn("  repaired  ");

        JudgeRetryExecutor exec = new JudgeRetryExecutor(chatClient);
        var res = exec.retry("q", "candidate", "feedback");

        assertThat(res.success()).isTrue();
        assertThat(res.answerText()).isEqualTo("repaired");
        assertThat(res.stageTraces()).hasSize(1);
        assertThat(res.stageTraces().getFirst().outcome()).isEqualTo(ExecutionStageOutcome.SUCCESS);
    }

    @Test
    void retry_whenModelReturnsBlank_returnsFailedWithEmptyResponseTrace() {
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(chatClient.prompt().user(anyString()).call().content())
                .thenReturn("   ");

        JudgeRetryExecutor exec = new JudgeRetryExecutor(chatClient);
        var res = exec.retry("q", "candidate", "feedback");

        assertThat(res.success()).isFalse();
        assertThat(res.answerText()).isEmpty();
        assertThat(res.stageTraces()).hasSize(1);
        assertThat(res.stageTraces().getFirst().outcome()).isEqualTo(ExecutionStageOutcome.FAILED);
        assertThat(res.stageTraces().getFirst().message()).contains("empty_response=true");
    }

    @Test
    void retry_whenChatClientThrows_returnsFailedWithErrorTypeTrace() {
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(chatClient.prompt().user(anyString()).call().content())
                .thenThrow(new RuntimeException("down"));

        JudgeRetryExecutor exec = new JudgeRetryExecutor(chatClient);
        var res = exec.retry("q", "candidate", "feedback");

        assertThat(res.success()).isFalse();
        assertThat(res.answerText()).isEmpty();
        assertThat(res.stageTraces()).hasSize(1);
        assertThat(res.stageTraces().getFirst().outcome()).isEqualTo(ExecutionStageOutcome.FAILED);
        assertThat(res.stageTraces().getFirst().message()).contains("error=RuntimeException");
    }
}

