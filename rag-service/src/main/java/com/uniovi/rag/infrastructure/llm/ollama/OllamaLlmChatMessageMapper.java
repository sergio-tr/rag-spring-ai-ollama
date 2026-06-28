package com.uniovi.rag.infrastructure.llm.ollama;

import com.uniovi.rag.application.port.llm.LlmChatMessage;
import com.uniovi.rag.application.port.llm.LlmChatRole;
import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

/** Maps {@link LlmChatMessage} turns into Spring AI {@link Message} instances for Ollama {@code /api/chat}. */
final class OllamaLlmChatMessageMapper {

    private OllamaLlmChatMessageMapper() {}

    static List<Message> toSpringAiMessages(List<LlmChatMessage> messages) {
        List<Message> out = new ArrayList<>(messages.size());
        for (LlmChatMessage message : messages) {
            out.add(toSpringAiMessage(message));
        }
        return out;
    }

    private static Message toSpringAiMessage(LlmChatMessage message) {
        return switch (message.role()) {
            case SYSTEM -> new SystemMessage(message.content());
            case USER -> new UserMessage(message.content());
            case ASSISTANT -> new AssistantMessage(message.content());
            case TOOL ->
                    throw new IllegalArgumentException(
                            "TOOL messages are not supported by the Ollama native chat adapter");
        };
    }
}
