package com.uniovi.rag.infrastructure.llm.ollama;

import com.uniovi.rag.application.port.llm.LlmChatMessage;
import com.uniovi.rag.application.port.llm.LlmChatRequest;
import com.uniovi.rag.application.port.llm.LlmChatRole;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.ollama.api.OllamaOptions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OllamaLlmOptionsMapperTest {

    @Test
    void mapsModelTemperatureAndKnownAdditionalParameters() {
        LlmChatRequest request =
                LlmChatRequest.of(
                        "gemma3:4b",
                        "system",
                        "user",
                        0.2,
                        null,
                        Map.of("topK", 40, "top_p", 0.9, "num_predict", 128, "internalToolExecutionEnabled", false));

        OllamaOptions options = OllamaLlmOptionsMapper.toOllamaOptions(request);

        assertEquals("gemma3:4b", options.getModel());
        assertEquals(0.2, options.getTemperature());
        assertEquals(40, options.getTopK());
        assertEquals(0.9, options.getTopP());
        assertEquals(128, options.getNumPredict());
    }

    @Test
    void chatMessageMapper_mapsSystemUserAssistant() {
        var messages =
                OllamaLlmChatMessageMapper.toSpringAiMessages(
                        List.of(
                                LlmChatMessage.system("sys"),
                                LlmChatMessage.user("usr"),
                                LlmChatMessage.assistant("asst")));

        assertEquals(3, messages.size());
        assertTrue(messages.get(0) instanceof SystemMessage);
        assertEquals("sys", messages.get(0).getText());
        assertTrue(messages.get(1) instanceof UserMessage);
        assertEquals("usr", messages.get(1).getText());
        assertTrue(messages.get(2) instanceof AssistantMessage);
        assertEquals("asst", messages.get(2).getText());
    }

    @Test
    void chatMessageMapper_rejectsToolRole() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        OllamaLlmChatMessageMapper.toSpringAiMessages(
                                List.of(new LlmChatMessage(LlmChatRole.TOOL, "tool-output"))));
    }
}
