package com.uniovi.rag.application.service.runtime.judge;

import com.uniovi.rag.domain.runtime.judge.JudgeEvaluation;
import com.uniovi.rag.domain.runtime.judge.JudgeOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JudgeEvaluatorTest {

    @Test
    void evaluate_mapsAcceptedLabel() {
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(chatClient.prompt().user(anyString()).call().content()).thenReturn("ACCEPTED\nFEEDBACK: ok");

        JudgeEvaluator evaluator = new JudgeEvaluator(chatClient);
        JudgeEvaluation r = evaluator.evaluate("q?", "answer", true);

        assertThat(r.outcome()).isEqualTo(JudgeOutcome.ACCEPTED);
        assertThat(r.feedback()).contains("ok");
        assertThat(r.stageTraces()).hasSize(1);
    }

    @Test
    void evaluate_mapsRejectedWhenNoRetry() {
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(chatClient.prompt().user(anyString()).call().content()).thenReturn("REJECTED_NO_RETRY");

        JudgeEvaluator evaluator = new JudgeEvaluator(chatClient);
        JudgeEvaluation r = evaluator.evaluate("q", "bad", false);

        assertThat(r.outcome()).isEqualTo(JudgeOutcome.REJECTED_NO_RETRY);
    }

    @Test
    void evaluate_retryRequestedOnlyWhenAllowed() {
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(chatClient.prompt().user(anyString()).call().content()).thenReturn("RETRY_REQUESTED");

        JudgeEvaluator allowed = new JudgeEvaluator(chatClient);
        assertThat(allowed.evaluate("q", "a", true).outcome()).isEqualTo(JudgeOutcome.RETRY_REQUESTED);

        ChatClient chatClient2 = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(chatClient2.prompt().user(anyString()).call().content()).thenReturn("RETRY_REQUESTED");
        JudgeEvaluator disallowed = new JudgeEvaluator(chatClient2);
        assertThat(disallowed.evaluate("q", "a", false).outcome()).isEqualTo(JudgeOutcome.REJECTED_NO_RETRY);
    }

    @Test
    void evaluate_failedSafe_onUnknownLabel() {
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(chatClient.prompt().user(anyString()).call().content()).thenReturn("MAYBE");

        JudgeEvaluator evaluator = new JudgeEvaluator(chatClient);
        assertThat(evaluator.evaluate("q", "a", true).outcome()).isEqualTo(JudgeOutcome.FAILED_SAFE);
    }

    @Test
    void evaluate_failedSafe_whenClientThrows() {
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(chatClient.prompt().user(anyString()).call().content()).thenThrow(new RuntimeException("boom"));

        JudgeEvaluator evaluator = new JudgeEvaluator(chatClient);
        JudgeEvaluation r = evaluator.evaluate("q", "a", true);
        assertThat(r.outcome()).isEqualTo(JudgeOutcome.FAILED_SAFE);
        assertThat(r.stageTraces().getFirst().outcome().name()).contains("FAILED");
    }
}
