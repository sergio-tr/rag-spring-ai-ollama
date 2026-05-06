package com.uniovi.rag.application.service.runtime.reasoning;

import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.engine.ExecutionStageOutcome;
import com.uniovi.rag.domain.runtime.engine.ExecutionStageTrace;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.stereotype.Service;

/**
 * Minimal verification step for R8A. Intended to be lightweight and safe:
 * verifies that the produced answer is supported by context (excerpt), without requesting chain-of-thought.
 */
@Service
public class AnswerVerificationService {

    private static final String PROMPT = """
            You are a strict verifier.
            Determine if the answer is supported by the context excerpt.
            Reply ONLY with one token: YES or NO.

            Question: %s
            ContextExcerpt: %s
            Answer: %s
            """;

    private final ChatClient chatClient;

    public AnswerVerificationService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public VerificationResult verify(ExecutionContext ctx, String question, String contextExcerpt, String answer) {
        long t0 = System.nanoTime();
        if (contextExcerpt == null || contextExcerpt.isBlank() || answer == null || answer.isBlank()) {
            return new VerificationResult(
                    Optional.empty(),
                    new ExecutionStageTrace(
                            "reasoning_verify",
                            TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0),
                            ExecutionStageOutcome.SKIPPED,
                            "reason=missing_context_or_answer"));
        }
        try {
            String prompt = String.format(PROMPT, safe(question, 240), safe(contextExcerpt, 900), safe(answer, 900));
            var spec = chatClient.prompt().user(prompt);
            if (ctx != null && ctx.chatModelOverride().isPresent()) {
                String m = ctx.chatModelOverride().get().trim();
                if (!m.isBlank()) {
                    spec = spec.options(OllamaOptions.builder().model(m).build());
                }
            }
            String raw = spec.call().content();
            String token = raw != null ? raw.trim().toUpperCase() : "";
            Optional<Boolean> verified =
                    token.startsWith("YES") ? Optional.of(true) : (token.startsWith("NO") ? Optional.of(false) : Optional.empty());
            ExecutionStageTrace trace =
                    new ExecutionStageTrace(
                            "reasoning_verify",
                            TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0),
                            verified.isPresent() ? ExecutionStageOutcome.SUCCESS : ExecutionStageOutcome.FAILED,
                            "outcome=" + (verified.map(v -> v ? "YES" : "NO").orElse("UNKNOWN")));
            return new VerificationResult(verified, trace);
        } catch (Exception e) {
            return new VerificationResult(
                    Optional.empty(),
                    new ExecutionStageTrace(
                            "reasoning_verify",
                            TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0),
                            ExecutionStageOutcome.FAILED,
                            "error=" + e.getClass().getSimpleName()));
        }
    }

    private static String safe(String s, int maxChars) {
        if (s == null) {
            return "";
        }
        String t = s.trim();
        if (maxChars <= 0 || t.length() <= maxChars) {
            return t;
        }
        return t.substring(0, maxChars);
    }

    public record VerificationResult(Optional<Boolean> verified, ExecutionStageTrace stageTrace) {}
}

