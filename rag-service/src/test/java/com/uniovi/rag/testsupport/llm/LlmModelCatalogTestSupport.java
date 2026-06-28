package com.uniovi.rag.testsupport.llm;

import com.uniovi.rag.application.service.llm.catalog.LlmModelCatalogService;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.infrastructure.llm.LlmOpenAiCompatibleDefaults;
import com.uniovi.rag.infrastructure.llm.LlmOllamaDefaults;
import com.uniovi.rag.infrastructure.llm.LlmProperties;
import java.util.List;

/** Builds {@link LlmModelCatalogService} instances for unit tests. */
public final class LlmModelCatalogTestSupport {

    private LlmModelCatalogTestSupport() {}

    public static LlmModelCatalogService catalogFrom(LlmProperties properties) {
        return new LlmModelCatalogService(properties);
    }

    public static LlmProperties openAiCompatibleCatalogValidationProperties() {
        LlmProperties properties = new LlmProperties();
        properties.setDefaultProvider(LlmProvider.OPENAI_COMPATIBLE);
        LlmOllamaDefaults ollama = properties.getOllama();
        ollama.setDefaultChatModel("gemma3:4b");
        ollama.setAvailableChatModels(List.of("gemma3:4b", "llama3.1:8b"));
        ollama.setDefaultEmbeddingModel("mxbai-embed-large:latest");
        ollama.setAvailableEmbeddingModels(List.of("mxbai-embed-large:latest"));
        LlmOpenAiCompatibleDefaults openAi = properties.getOpenAiCompatible();
        openAi.setDefaultBaseUrl("http://litellm:4000");
        openAi.setDefaultChatModel("gpt-oss:20b");
        openAi.setAvailableChatModels(List.of("gpt-oss:20b"));
        openAi.setDefaultApiKeyEnv("OPENAI_COMPATIBLE_API_KEY");
        openAi.setDefaultEmbeddingModel("hf.co/mixedbread-ai/mxbai-embed-large-v1:latest");
        openAi.setAvailableEmbeddingModels(List.of("hf.co/mixedbread-ai/mxbai-embed-large-v1:latest"));
        return properties;
    }

    public static LlmProperties openAiLiteLlmProperties() {
        LlmProperties properties = new LlmProperties();
        LlmOllamaDefaults ollama = properties.getOllama();
        ollama.setDefaultChatModel("gemma3:4b");
        ollama.setAvailableChatModels(List.of("gemma3:4b", "llama3.1:8b"));
        ollama.setDefaultEmbeddingModel("mxbai-embed-large:latest");
        ollama.setAvailableEmbeddingModels(List.of("mxbai-embed-large:latest"));
        LlmOpenAiCompatibleDefaults openAi = properties.getOpenAiCompatible();
        openAi.setDefaultBaseUrl("http://litellm:4000");
        openAi.setDefaultChatModel("gpt-oss:20b");
        openAi.setAvailableChatModels(
                List.of("gpt-oss:20b", "qwen3.6:27b", "deepseek-v2:16b", "qwen3.5:27b", "gemma3:27b", "deepseek-r1:14b"));
        openAi.setDefaultApiKeyEnv("OPENAI_COMPATIBLE_API_KEY");
        openAi.setDefaultEmbeddingModel("qwen3-embedding:8b");
        openAi.setAvailableEmbeddingModels(List.of("qwen3-embedding:8b"));
        return properties;
    }
}
