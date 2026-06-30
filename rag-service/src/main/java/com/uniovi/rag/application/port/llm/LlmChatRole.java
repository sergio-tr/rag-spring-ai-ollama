package com.uniovi.rag.application.port.llm;

/**
 * Role of a message in a {@link LlmChatRequest}.
 */
public enum LlmChatRole {
    SYSTEM,
    USER,
    ASSISTANT,
    TOOL
}
