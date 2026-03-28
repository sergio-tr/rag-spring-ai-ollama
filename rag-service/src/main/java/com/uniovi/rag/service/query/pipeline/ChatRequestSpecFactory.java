package com.uniovi.rag.service.query.pipeline;

import org.springframework.ai.chat.client.ChatClient;

/**
 * Supplies {@link ChatClient} request specs (e.g. per-request Ollama model override).
 */
@FunctionalInterface
public interface ChatRequestSpecFactory {

    ChatClient.ChatClientRequestSpec spec();
}
