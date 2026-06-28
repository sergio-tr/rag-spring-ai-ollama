package com.uniovi.rag.application.service.config.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.domain.llm.LlmConfigurationKeys;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.infrastructure.llm.LlmProperties;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LlmConfigurationMergeTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void mergeCascade_userOverridesApplicationDefaults() {
        LlmProperties properties = new LlmProperties();
        LlmConfigurationLayer application = LlmConfigurationApplicationDefaults.applicationLayer(properties);

        LlmConfigurationLayer merged =
                LlmConfigurationMerge.mergeCascade(
                        application,
                        Optional.empty(),
                        Optional.of(
                                Map.of(
                                        LlmConfigurationKeys.CHAT_MODEL,
                                        "user-chat",
                                        LlmConfigurationKeys.TEMPERATURE,
                                        0.5)),
                        Optional.empty(),
                        Optional.empty(),
                        null,
                        null,
                        objectMapper);

        assertEquals("user-chat", merged.chatModel);
        assertEquals(0.5, merged.temperature);
        assertEquals(LlmProvider.OLLAMA_NATIVE, merged.chatProvider);
    }

    @Test
    void mergeCascade_projectWinsOverUser() {
        LlmProperties properties = new LlmProperties();
        LlmConfigurationLayer application = LlmConfigurationApplicationDefaults.applicationLayer(properties);

        LlmConfigurationLayer merged =
                LlmConfigurationMerge.mergeCascade(
                        application,
                        Optional.empty(),
                        Optional.of(Map.of(LlmConfigurationKeys.BASE_URL, "http://user:11434")),
                        Optional.of(Map.of(LlmConfigurationKeys.BASE_URL, "http://project:11434")),
                        Optional.empty(),
                        null,
                        null,
                        objectMapper);

        assertEquals("http://project:11434", merged.baseUrl);
    }

    @Test
    void mergeCascade_runtimeOverrideWins() throws Exception {
        LlmProperties properties = new LlmProperties();
        LlmConfigurationLayer application = LlmConfigurationApplicationDefaults.applicationLayer(properties);

        var runtime =
                objectMapper.readTree(
                        "{\"llmProvider\":\"OPENAI_COMPATIBLE\",\"llmApiKeyEnv\":\"RUNTIME_KEY\"}");

        LlmConfigurationLayer merged =
                LlmConfigurationMerge.mergeCascade(
                        application,
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        runtime,
                        null,
                        objectMapper);

        assertEquals(LlmProvider.OPENAI_COMPATIBLE, merged.chatProvider);
        assertEquals(LlmProvider.OPENAI_COMPATIBLE, merged.embeddingProvider);
        assertEquals("RUNTIME_KEY", merged.apiKeyEnv);
    }

    @Test
    void materialize_fillsOllamaDefaultsWhenLayersEmpty() {
        LlmProperties properties = new LlmProperties();
        LlmConfigurationLayer empty = LlmConfigurationLayer.empty();

        var resolved = LlmConfigurationApplicationDefaults.materialize(empty, properties);

        assertEquals(LlmProvider.OLLAMA_NATIVE, resolved.provider());
        assertEquals("http://localhost:11434", resolved.baseUrl());
        assertEquals("gemma3:4b", resolved.chatModel());
        assertEquals("mxbai-embed-large:latest", resolved.embeddingModel());
        assertNull(resolved.apiKeyEnv());
    }
}
