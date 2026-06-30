package com.uniovi.rag.infrastructure.llm.ollama;

import com.uniovi.rag.application.port.llm.LlmChatRequest;
import com.uniovi.rag.application.port.llm.LlmChatResponse;
import com.uniovi.rag.domain.llm.LlmProvider;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.api.OllamaOptions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OllamaNativeLlmChatClientTest {

    @Mock
    private ChatModel chatModel;

    @Test
    void delegatesToChatModelWithOllamaOptions() {
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("hola")))));

        OllamaNativeLlmChatClient client = new OllamaNativeLlmChatClient(chatModel);
        LlmChatResponse response = client.chat(LlmChatRequest.of("gemma3:4b", "sys", "user", 0.1, null, Map.of()));

        assertEquals("hola", response.content());
        assertEquals(LlmProvider.OLLAMA_NATIVE, client.provider());

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(promptCaptor.capture());
        Prompt prompt = promptCaptor.getValue();
        assertEquals(2, prompt.getInstructions().size());
        OllamaOptions options = (OllamaOptions) prompt.getOptions();
        assertEquals("gemma3:4b", options.getModel());
        assertEquals(0.1, options.getTemperature());
    }
}
