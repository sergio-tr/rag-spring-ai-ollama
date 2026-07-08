package com.uniovi.rag.application.service.evaluation.judge;

import java.util.Optional;
import java.util.UUID;

/** Thread-local scope for evaluation judge calls (user + optional explicit judge model). */
public final class EvaluationJudgeExecutionScope {

    public record Scope(UUID userId, String judgeModelOverride) {}

    private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<>();

    private EvaluationJudgeExecutionScope() {}

    public static AutoCloseable open(UUID userId, String judgeModelOverride) {
        CURRENT.set(new Scope(userId, blankToNull(judgeModelOverride)));
        return () -> CURRENT.remove();
    }

    public static Optional<UUID> currentUserId() {
        return Optional.ofNullable(CURRENT.get()).map(Scope::userId);
    }

    public static Optional<String> currentJudgeModelOverride() {
        return Optional.ofNullable(CURRENT.get())
                .map(Scope::judgeModelOverride)
                .filter(v -> v != null && !v.isBlank());
    }

    private static String blankToNull(String value) {
        return value != null && !value.isBlank() ? value.trim() : null;
    }
}
