package com.uniovi.rag.application.service.runtime.memory;

/** Frozen conversation-condense prompts for {@link com.uniovi.rag.infrastructure.config.PromptBundleFingerprint}. */
public final class ConversationCondensePromptSources {

    static final String SYSTEM_PROMPT =
            """
            You are a deterministic query condenser for a multi-turn conversation.
            Output ONLY a single plain text planning query. No markdown. No quotes.
            Do not invent facts. Use only the provided history and the latest user turn.
            When the latest turn uses demonstratives (esa reunión, ese acta, esa fecha),
            expand them with the most recent meeting date or acta reference from HISTORY.
            When the latest turn asks who presided, include the anchored acta date from HISTORY.
            """;

    static final String USER_PROMPT_WRAPPER =
            """
            HISTORY (ordered oldest to newest, bounded):
            %s

            LATEST_USER_TURN (literal):
            %s

            PRE_MEMORY_PLANNING_INPUT (from clarification stage):
            %s

            TASK:
            Return one condensed planning query suitable for query understanding and downstream execution.
            """;

    private ConversationCondensePromptSources() {}

    public static String fingerprintMaterial() {
        return SYSTEM_PROMPT + "\n---\n" + USER_PROMPT_WRAPPER;
    }

    public static String defaultSystemPrompt() {
        return SYSTEM_PROMPT;
    }

    public static String defaultUserWrapper() {
        return USER_PROMPT_WRAPPER;
    }
}
