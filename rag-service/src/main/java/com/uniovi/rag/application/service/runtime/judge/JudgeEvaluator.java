package com.uniovi.rag.application.service.runtime.judge;

import com.uniovi.rag.application.service.runtime.RuntimePromptBudgeter;
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
    private final RuntimePromptBudgeter promptBudgeter;

    public JudgeEvaluator(ChatClient chatClient, RuntimePromptBudgeter promptBudgeter) {
        this.chatClient = chatClient;
        this.promptBudgeter = promptBudgeter;
    }

    public JudgeEvaluation evaluate(String queryText, String candidateAnswerText, boolean retryAllowed) {
        return evaluate(queryText, candidateAnswerText, "", retryAllowed);
    }

    public JudgeEvaluation evaluate(
            String queryText, String candidateAnswerText, String contextText, boolean retryAllowed) {
        long startNanos = System.nanoTime();
        try {
            RuntimePromptBudgeter.BudgetResult budget =
                    promptBudgeter != null
                            ? promptBudgeter.budgetForJudgeCandidateAnswer(candidateAnswerText)
                            : RuntimePromptBudgeter.truncate(
                                    "judge_candidate_answer", candidateAnswerText, 4_000, "default_judge_max_answer_chars");
            RuntimePromptBudgeter.BudgetResult contextBudget =
                    promptBudgeter != null
                            ? promptBudgeter.budgetForJudgeCandidateAnswer(contextText)
                            : RuntimePromptBudgeter.truncate(
                                    "judge_context", contextText, 6_000, "default_judge_max_context_chars");
            String prompt =
                    buildPrompt(
                            queryText, budget.textUsed(), contextBudget.textUsed(), retryAllowed);
            String raw = chatClient.prompt().user(prompt).call().content();
            JudgeOutcome o = parseOutcome(raw, retryAllowed);
            String feedback = extractFeedback(raw);
            return new JudgeEvaluation(
                    o,
                    Optional.empty(),
                    feedback,
                    List.of(
                            new ExecutionStageTrace(
                                    "judge_evaluate",
                                    millisSince(startNanos),
                                    ExecutionStageOutcome.SUCCESS,
                                    "outcome="
                                            + o
                                            + " truncated="
                                            + budget.truncated()
                                            + " contextChars="
                                            + contextBudget.originalChars())));
        } catch (Exception e) {
            return new JudgeEvaluation(
                    JudgeOutcome.FAILED_SAFE,
                    Optional.empty(),
                    "",
                    List.of(
                            new ExecutionStageTrace(
                                    "judge_evaluate",
                                    millisSince(startNanos),
                                    ExecutionStageOutcome.FAILED,
                                    "error=" + e.getClass().getSimpleName())));
        }
    }

    private static String buildPrompt(
            String queryText, String candidateAnswerText, String contextText, boolean retryAllowed) {
        String retryLine = retryAllowed
                ? RuntimeJudgePromptSources.RETRY_ALLOWED_LINE
                : RuntimeJudgePromptSources.RETRY_DENIED_LINE;
        String contextBlock =
                contextText == null || contextText.isBlank()
                        ? RuntimeJudgePromptSources.EMPTY_CONTEXT_PLACEHOLDER
                        : contextText;
        return RuntimeJudgePromptSources.TEMPLATE_RAW.formatted(
                queryText == null ? "" : queryText,
                contextBlock,
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

