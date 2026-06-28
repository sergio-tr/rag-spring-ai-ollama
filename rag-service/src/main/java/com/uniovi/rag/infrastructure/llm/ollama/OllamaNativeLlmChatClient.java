package com.uniovi.rag.infrastructure.llm.ollama;

import com.uniovi.rag.application.port.llm.LlmChatClient;
import com.uniovi.rag.application.port.llm.LlmChatRequest;
import com.uniovi.rag.application.port.llm.LlmChatResponse;
import com.uniovi.rag.domain.llm.LlmProvider;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.stereotype.Component;

/**
 * Ollama-native {@link LlmChatClient} delegating to Spring AI {@link ChatModel} ({@code POST /api/chat}).
 * Does not replace the shared {@link org.springframework.ai.chat.client.ChatClient} bean used by the RAG runtime yet.
 */
@Component
public class OllamaNativeLlmChatClient implements LlmChatClient {

    private final ChatModel chatModel;

    public OllamaNativeLlmChatClient(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public LlmChatResponse chat(LlmChatRequest request) {
        OllamaOptions options = OllamaLlmOptionsMapper.toOllamaOptions(request);
        Prompt prompt = new Prompt(OllamaLlmChatMessageMapper.toSpringAiMessages(request.messages()), options);
        ChatResponse response = chatModel.call(prompt);
        return OllamaLlmChatResponseMapper.toPortResponse(response, request.model());
    }

    @Override
    public LlmProvider provider() {
        return LlmProvider.OLLAMA_NATIVE;
    }
}
