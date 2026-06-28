package com.uniovi.rag.application.port.llm;

import java.util.Objects;

/**
 * Single turn in a provider-agnostic chat completion request.
 */
public record LlmChatMessage(LlmChatRole role, String content) {

    public LlmChatMessage {
        Objects.requireNonNull(role, "role");
        content = content != null ? content : "";
    }

    public static LlmChatMessage system(String content) {
        return new LlmChatMessage(LlmChatRole.SYSTEM, content);
    }

    public static LlmChatMessage user(String content) {
        return new LlmChatMessage(LlmChatRole.USER, content);
    }

    public static LlmChatMessage assistant(String content) {
        return new LlmChatMessage(LlmChatRole.ASSISTANT, content);
    }
}
