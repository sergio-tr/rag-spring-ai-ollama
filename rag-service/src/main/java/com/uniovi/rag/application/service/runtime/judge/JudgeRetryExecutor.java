package com.uniovi.rag.application.service.runtime.judge;

import com.uniovi.rag.domain.runtime.engine.ExecutionStageOutcome;
import com.uniovi.rag.domain.runtime.engine.ExecutionStageTrace;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class JudgeRetryExecutor {

    private static final String STAGE_JUDGE_RETRY_EXECUTE = "judge_retry_execute";

    private final ChatClient chatClient;

    public JudgeRetryExecutor(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public RetryResult retry(String queryText, String candidateAnswerText, String judgeFeedback) {
        long startNanos = System.nanoTime();
        try {
            String prompt = buildPrompt(queryText, candidateAnswerText, judgeFeedback);
            String out = chatClient.prompt().user(prompt).call().content();
            String repaired = out != null ? out.trim() : "";
            if (repaired.isEmpty()) {
                return RetryResult.failed(
                        "",
                        List.of(new ExecutionStageTrace(
                                STAGE_JUDGE_RETRY_EXECUTE,
                                millisSince(startNanos),
                                ExecutionStageOutcome.FAILED,
                                "empty_response=true")));
            }
            return RetryResult.succeeded(
                    repaired,
                    List.of(new ExecutionStageTrace(
                            STAGE_JUDGE_RETRY_EXECUTE,
                            millisSince(startNanos),
                            ExecutionStageOutcome.SUCCESS,
                            "ok=true")));
        } catch (Exception e) {
            return RetryResult.failed(
                    "",
                    List.of(new ExecutionStageTrace(
                            STAGE_JUDGE_RETRY_EXECUTE,
                            millisSince(startNanos),
                            ExecutionStageOutcome.FAILED,
                            "error=" + e.getClass().getSimpleName())));
        }
    }

    private static String buildPrompt(String queryText, String candidateAnswerText, String judgeFeedback) {
        return """
                You are generating a repaired answer after a judge rejected a candidate answer.

                Question:
                %s

                Candidate answer:
                %s

                Judge feedback:
                %s

                Produce the repaired final answer. Do not mention the judge. Do not add headers.
                """.formatted(
                queryText == null ? "" : queryText,
                candidateAnswerText == null ? "" : candidateAnswerText,
                judgeFeedback == null ? "" : judgeFeedback);
    }

    private static long millisSince(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    public record RetryResult(boolean success, String answerText, List<ExecutionStageTrace> stageTraces) {
        public static RetryResult succeeded(String text, List<ExecutionStageTrace> traces) {
            return new RetryResult(true, text, List.copyOf(traces));
        }

        public static RetryResult failed(String text, List<ExecutionStageTrace> traces) {
            return new RetryResult(false, text, List.copyOf(traces));
        }
    }
}

