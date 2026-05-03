package com.uniovi.rag.application.service.runtime.judge;

import com.uniovi.rag.domain.runtime.engine.ExecutionStageOutcome;
import com.uniovi.rag.domain.runtime.engine.ExecutionStageTrace;
import com.uniovi.rag.domain.runtime.judge.JudgeEvaluation;
import com.uniovi.rag.domain.runtime.judge.JudgeOutcome;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class JudgeEvaluator {

    private final ChatClient chatClient;

    public JudgeEvaluator(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public JudgeEvaluation evaluate(String queryText, String candidateAnswerText, boolean retryAllowed) {
        long startNanos = System.nanoTime();
        try {
            String prompt = buildPrompt(queryText, candidateAnswerText, retryAllowed);
            String raw = chatClient.prompt().user(prompt).call().content();
            JudgeOutcome o = parseOutcome(raw, retryAllowed);
            String feedback = extractFeedback(raw);
            return new JudgeEvaluation(
                    o,
                    Optional.empty(),
                    feedback,
                    List.of(new ExecutionStageTrace(
                            "judge_evaluate",
                            millisSince(startNanos),
                            ExecutionStageOutcome.SUCCESS,
                            "outcome=" + o)));
        } catch (Exception e) {
            return new JudgeEvaluation(
                    JudgeOutcome.FAILED_SAFE,
                    Optional.empty(),
                    "",
                    List.of(new ExecutionStageTrace(
                            "judge_evaluate",
                            millisSince(startNanos),
                            ExecutionStageOutcome.FAILED,
                            "error=" + e.getClass().getSimpleName())));
        }
    }

    private static String buildPrompt(String queryText, String candidateAnswerText, boolean retryAllowed) {
        String retryLine = retryAllowed
                ? "If the answer is not acceptable, output RETRY_REQUESTED."
                : "If the answer is not acceptable, output REJECTED_NO_RETRY.";
        return """
                You are a post-answer judge for a RAG assistant.

                Question:
                %s

                Candidate answer:
                %s

                Decide one label:
                - ACCEPTED
                - REJECTED_NO_RETRY
                - RETRY_REQUESTED

                Rules:
                - Output exactly one label on the first line.
                - %s
                - If the answer is acceptable, output ACCEPTED.
                - If the answer contains unsupported claims or is clearly incorrect, do not invent facts.

                Optionally include feedback after the first line starting with "FEEDBACK:".
                """.formatted(
                queryText == null ? "" : queryText,
                candidateAnswerText == null ? "" : candidateAnswerText,
                retryLine);
    }

    private static JudgeOutcome parseOutcome(String raw, boolean retryAllowed) {
        if (raw == null) {
            return JudgeOutcome.FAILED_SAFE;
        }
        String first = raw.strip();
        int nl = first.indexOf('\n');
        if (nl >= 0) {
            first = first.substring(0, nl).trim();
        }
        return switch (first) {
            case "ACCEPTED" -> JudgeOutcome.ACCEPTED;
            case "REJECTED_NO_RETRY" -> JudgeOutcome.REJECTED_NO_RETRY;
            case "RETRY_REQUESTED" -> retryAllowed ? JudgeOutcome.RETRY_REQUESTED : JudgeOutcome.REJECTED_NO_RETRY;
            default -> JudgeOutcome.FAILED_SAFE;
        };
    }

    private static String extractFeedback(String raw) {
        if (raw == null) {
            return "";
        }
        int idx = raw.indexOf("FEEDBACK:");
        if (idx < 0) {
            return "";
        }
        return raw.substring(idx + "FEEDBACK:".length()).trim();
    }

    private static long millisSince(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }
}

