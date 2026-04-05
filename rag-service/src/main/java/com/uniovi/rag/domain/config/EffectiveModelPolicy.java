package com.uniovi.rag.domain.config;

import java.util.Set;

/**
 * Chooses an effective chat model name under governance allowlist rules.
 */
public final class EffectiveModelPolicy {

    private EffectiveModelPolicy() {
    }

    /**
     * Validates a user-requested chat model override when governance lists allowed LLM names.
     *
     * @param requested           non-null, non-blank model id/name
     * @param governanceAllowlist when non-empty, {@code requested} must be contained; when empty, any name is accepted
     * @return trimmed requested name
     */
    public static String validateChatModelOverride(String requested, Set<String> governanceAllowlist) {
        if (requested == null || requested.isBlank()) {
            throw new IllegalArgumentException("Chat model override is empty");
        }
        String t = requested.trim();
        if (governanceAllowlist == null || governanceAllowlist.isEmpty()) {
            return t;
        }
        if (!governanceAllowlist.contains(t)) {
            throw new IllegalArgumentException("LLM model is not allowed by governance: " + t);
        }
        return t;
    }
}
