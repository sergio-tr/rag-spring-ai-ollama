package com.uniovi.rag.application.service.config.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.uniovi.rag.application.service.llm.catalog.LlmModelCatalogService;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.domain.llm.ResolvedLlmConfig;
import com.uniovi.rag.infrastructure.llm.LlmOpenAiCompatibleDefaults;
import com.uniovi.rag.infrastructure.llm.LlmProperties;
import com.uniovi.rag.testsupport.llm.LlmModelCatalogTestSupport;
import java.util.List;
import org.junit.jupiter.api.Test;

class LlmConfigurationApplicationDefaultsTest {

    @Test
    void applicationLayer_openAiProviderUsesOpenAiDefaultChatModelNotOllamaModel() {
        LlmProperties properties = LlmModelCatalogTestSupport.openAiLiteLlmProperties();
        properties.setDefaultProvider(LlmProvider.OPENAI_COMPATIBLE);

        LlmConfigurationLayer layer = LlmConfigurationApplicationDefaults.applicationLayer(properties);

        assertEquals(LlmProvider.OPENAI_COMPATIBLE, layer.chatProvider);
        assertEquals(LlmProvider.OPENAI_COMPATIBLE, layer.embeddingProvider);
        assertEquals("gpt-oss:20b", layer.chatModel);
    }

    @Test
    void applicationLayer_ollamaProviderKeepsOllamaDefaultChatModel() {
        LlmProperties properties = new LlmProperties();
        properties.getOllama().setDefaultChatModel("gemma3:4b");

        LlmConfigurationLayer layer = LlmConfigurationApplicationDefaults.applicationLayer(properties);

        assertEquals(LlmProvider.OLLAMA_NATIVE, layer.chatProvider);
        assertEquals(LlmProvider.OLLAMA_NATIVE, layer.embeddingProvider);
        assertEquals("gemma3:4b", layer.chatModel);
    }

    @Test
    void materialize_openAiProviderIgnoresOllamaOnlyMergedChatModel() {
        LlmProperties properties = LlmModelCatalogTestSupport.openAiLiteLlmProperties();
        LlmModelCatalogService catalog = new LlmModelCatalogService(properties);
        LlmConfigurationLayer layer = LlmConfigurationLayer.empty();
        layer.chatProvider = LlmProvider.OPENAI_COMPATIBLE;
        layer.embeddingProvider = LlmProvider.OPENAI_COMPATIBLE;
        layer.chatModel = "gemma3:4b";

        ResolvedLlmConfig resolved = LlmConfigurationApplicationDefaults.materialize(layer, properties, catalog);

        assertEquals("gpt-oss:20b", resolved.chatModel());
    }

    @Test
    void materialize_openAiProviderUsesOpenAiDefaultsNotOllamaChatModel() {
        LlmProperties properties = new LlmProperties();
        properties.setDefaultProvider(LlmProvider.OPENAI_COMPATIBLE);
        LlmOpenAiCompatibleDefaults openAi = properties.getOpenAiCompatible();
        openAi.setDefaultBaseUrl("http://litellm:4000");
        openAi.setDefaultChatModel("gpt-oss:20b");
        openAi.setAvailableChatModels(List.of("gpt-oss:20b"));
        properties.getOllama().setDefaultChatModel("gemma3:4b");

        ResolvedLlmConfig resolved =
                LlmConfigurationApplicationDefaults.materialize(
                        LlmConfigurationApplicationDefaults.applicationLayer(properties), properties, null);

        assertEquals("gpt-oss:20b", resolved.chatModel());
        assertEquals(LlmProvider.OPENAI_COMPATIBLE, resolved.provider());
    }
}
