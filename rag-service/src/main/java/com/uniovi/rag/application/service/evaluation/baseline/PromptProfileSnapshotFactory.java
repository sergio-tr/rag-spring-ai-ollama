package com.uniovi.rag.application.service.evaluation.baseline;

import com.uniovi.rag.domain.evaluation.snapshot.PromptProfileSnapshot;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.stream.Stream;

/** Builds {@link PromptProfileSnapshot} for baseline lab runs. */
public final class PromptProfileSnapshotFactory {

    private PromptProfileSnapshotFactory() {}

    public static PromptProfileSnapshot baselineLabProfile() {
        String effective =
                Stream.of(
                                EvaluationBaselinePrompts.BASE_SYSTEM,
                                EvaluationBaselinePrompts.PROJECT_SYSTEM,
                                EvaluationBaselinePrompts.CHAT_SYSTEM)
                        .filter(s -> s != null && !s.isBlank())
                        .reduce((a, b) -> a + "\n\n" + b)
                        .orElse("");
        return new PromptProfileSnapshot(
                EvaluationBaselinePrompts.PROFILE_VERSION,
                EvaluationBaselinePrompts.BASE_SYSTEM.trim(),
                EvaluationBaselinePrompts.PROJECT_SYSTEM.trim(),
                EvaluationBaselinePrompts.CHAT_SYSTEM,
                EvaluationBaselinePrompts.RETRIEVAL_QUESTION_TEMPLATE.trim(),
                EvaluationBaselinePrompts.ANSWER_FORMATTING.trim(),
                effective.trim(),
                sha256Hex(effective.trim()));
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
