package com.uniovi.rag.application.service.config.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.application.port.ConfigurationSourcePort;
import com.uniovi.rag.application.port.llm.LlmClientRegistryPort;
import com.uniovi.rag.application.port.llm.LlmEmbeddingClient;
import com.uniovi.rag.application.service.llm.LlmClientResolver;
import com.uniovi.rag.application.service.llm.catalog.LlmModelCatalogService;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.domain.llm.ResolvedLlmConfig;
import com.uniovi.rag.infrastructure.llm.LlmOpenAiCompatibleDefaults;
import com.uniovi.rag.infrastructure.llm.LlmProperties;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Phase 1 — uniform provider resolution: {@code rag.llm.default-provider} governs chat and embeddings unless explicitly split.
 */
@ExtendWith(MockitoExtension.class)
class LlmProviderResolutionTest {

    @Mock
    private ConfigurationSourcePort configurationSource;

    @Mock
    private LlmClientRegistryPort clientRegistry;

    @Mock
    private LlmEmbeddingClient openAiEmbeddingClient;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final LlmProperties llmProperties = new LlmProperties();
    private LlmModelCatalogService modelCatalog;
    private ResolvedLlmConfigResolver configResolver;
    private LlmClientResolver clientResolver;

    @BeforeEach
    void setUp() {
        modelCatalog = new LlmModelCatalogService(llmProperties);
        configResolver = new ResolvedLlmConfigResolver(configurationSource, llmProperties, objectMapper, modelCatalog);
        clientResolver = new LlmClientResolver(clientRegistry);
    }

    @Test
    void openAiCompatibleDefaultProviderResolvesChatAndEmbeddingsToOpenAiCompatible() {
        defaultProviderOpenAiCompatibleAppliesToWholeRagStack();
    }

    @Test
    void ollamaDefaultProviderResolvesChatAndEmbeddingsToOllamaNative() {
        defaultProviderOllamaNativeAppliesToWholeRagStack();
    }

    @Test
    void openAiCompatibleDoesNotUseSpringAiOllamaChatModel() {
        openAiCompatibleProviderUsesLiteLlmChatModelNotSpringAiOllamaChatModel();
    }

    @Test
    void openAiCompatibleDoesNotUseSpringAiOllamaEmbeddingModel() {
        openAiCompatibleProviderUsesLiteLlmEmbeddingModelNotSpringAiOllamaEmbeddingModel();
    }

    @Test
    void ollamaNativeUsesSpringAiOllamaModelsOnlyWhenProviderIsOllama() {
        ollamaProviderUsesSpringAiOllamaModels();
    }

    @Test
    void noSilentFallbackFromOpenAiCompatibleToOllama() {
        configureOpenAiUniformDefaults();
        llmProperties.getOllama().setDefaultEmbeddingModel("mxbai-embed-large:latest");
        modelCatalog = new LlmModelCatalogService(llmProperties);
        configResolver = new ResolvedLlmConfigResolver(configurationSource, llmProperties, objectMapper, modelCatalog);

        ResolvedLlmConfig config = configResolver.resolve(null, null, null);

        assertEquals(LlmProvider.OPENAI_COMPATIBLE, config.embeddingProvider());
        assertEquals("qwen3-embedding:8b", config.embeddingModel());
        assertNotEquals("mxbai-embed-large:latest", config.embeddingModel());
        assertNotEquals(LlmProvider.OLLAMA_NATIVE, config.embeddingProvider());
    }

    @Test
    void noSilentFallbackFromOllamaToOpenAiCompatible() {
        llmProperties.setDefaultProvider(LlmProvider.OLLAMA_NATIVE);
        llmProperties.getOllama().setAvailableChatModels(List.of("ollama-only-chat"));
        llmProperties.getOllama().setDefaultChatModel("ollama-only-chat");
        llmProperties.getOpenAiCompatible().setDefaultChatModel("gpt-oss:20b");
        llmProperties.getOpenAiCompatible().setAvailableChatModels(List.of("gpt-oss:20b"));
        modelCatalog = new LlmModelCatalogService(llmProperties);
        configResolver = new ResolvedLlmConfigResolver(configurationSource, llmProperties, objectMapper, modelCatalog);

        ResolvedLlmConfig config = configResolver.resolve(null, null, null);

        assertEquals(LlmProvider.OLLAMA_NATIVE, config.chatProvider());
        assertEquals("ollama-only-chat", config.chatModel());
        assertNotEquals("gpt-oss:20b", config.chatModel());
    }

    @Test
    void openAiCompatibleProviderUsesLiteLlmChatModelNotSpringAiOllamaChatModel() {
        configureOpenAiUniformDefaults();
        simulateSpringAiOllamaLegacyPollution();
        modelCatalog = new LlmModelCatalogService(llmProperties);
        configResolver = new ResolvedLlmConfigResolver(configurationSource, llmProperties, objectMapper, modelCatalog);

        ResolvedLlmConfig config = configResolver.resolve(null, null, null);

        assertEquals("gpt-oss:20b", config.chatModel());
        assertEquals(LlmProvider.OPENAI_COMPATIBLE, config.chatProvider());
    }

    @Test
    void openAiCompatibleProviderUsesLiteLlmEmbeddingModelNotSpringAiOllamaEmbeddingModel() {
        configureOpenAiUniformDefaults();
        simulateSpringAiOllamaLegacyPollution();
        modelCatalog = new LlmModelCatalogService(llmProperties);
        configResolver = new ResolvedLlmConfigResolver(configurationSource, llmProperties, objectMapper, modelCatalog);

        ResolvedLlmConfig config = configResolver.resolve(null, null, null);

        assertEquals("qwen3-embedding:8b", config.embeddingModel());
        assertEquals(LlmProvider.OPENAI_COMPATIBLE, config.embeddingProvider());
        assertNotEquals("mxbai-embed-large:latest", config.embeddingModel());
    }

    @Test
    void ollamaProviderUsesSpringAiOllamaModels() {
        llmProperties.setDefaultProvider(LlmProvider.OLLAMA_NATIVE);
        llmProperties.getOllama().setDefaultBaseUrl("http://localhost:11434");
        llmProperties.getOllama().setDefaultChatModel("gemma3:4b");
        llmProperties.getOllama().setDefaultEmbeddingModel("mxbai-embed-large:latest");
        llmProperties.getOllama().setAvailableChatModels(List.of("gemma3:4b"));
        llmProperties.getOllama().setAvailableEmbeddingModels(List.of("mxbai-embed-large:latest"));
        llmProperties.getOpenAiCompatible().setDefaultChatModel("gpt-oss:20b");
        llmProperties.getOpenAiCompatible().setDefaultEmbeddingModel("qwen3-embedding:8b");
        modelCatalog = new LlmModelCatalogService(llmProperties);
        configResolver = new ResolvedLlmConfigResolver(configurationSource, llmProperties, objectMapper, modelCatalog);

        ResolvedLlmConfig config = configResolver.resolve(null, null, null);

        assertEquals("gemma3:4b", config.chatModel());
        assertEquals("mxbai-embed-large:latest", config.embeddingModel());
        assertEquals(LlmProvider.OLLAMA_NATIVE, config.chatProvider());
        assertEquals(LlmProvider.OLLAMA_NATIVE, config.embeddingProvider());
    }

    @Test
    void defaultProviderOpenAiCompatibleAppliesToWholeRagStack() {
        configureOpenAiUniformDefaults();
        modelCatalog = new LlmModelCatalogService(llmProperties);
        configResolver = new ResolvedLlmConfigResolver(configurationSource, llmProperties, objectMapper, modelCatalog);

        ResolvedLlmConfig config = configResolver.resolve(null, null, null);

        assertEquals(LlmProvider.OPENAI_COMPATIBLE, config.chatProvider());
        assertEquals(LlmProvider.OPENAI_COMPATIBLE, config.embeddingProvider());
        assertEquals(LlmProvider.OPENAI_COMPATIBLE, config.provider());
        assertTrue(config.uniformProviders());
        assertEquals("gpt-oss:20b", config.chatModel());
        assertEquals("qwen3-embedding:8b", config.embeddingModel());
        assertEquals("http://litellm:4000", config.baseUrl());
        assertEquals("PATH", config.apiKeyEnv());
        assertTrue(config.usesOpenAiCompatibleChat());
        assertTrue(config.usesOpenAiCompatibleEmbedding());
    }

    @Test
    void defaultProviderOllamaNativeAppliesToWholeRagStack() {
        llmProperties.setDefaultProvider(LlmProvider.OLLAMA_NATIVE);
        llmProperties.getOllama().setDefaultChatModel("gemma3:4b");
        llmProperties.getOllama().setDefaultEmbeddingModel("mxbai-embed-large:latest");
        llmProperties.getOllama().setAvailableChatModels(List.of("gemma3:4b"));
        llmProperties.getOllama().setAvailableEmbeddingModels(List.of("mxbai-embed-large:latest"));
        modelCatalog = new LlmModelCatalogService(llmProperties);
        configResolver = new ResolvedLlmConfigResolver(configurationSource, llmProperties, objectMapper, modelCatalog);

        ResolvedLlmConfig config = configResolver.resolve(null, null, null);

        assertEquals(LlmProvider.OLLAMA_NATIVE, config.chatProvider());
        assertEquals(LlmProvider.OLLAMA_NATIVE, config.embeddingProvider());
        assertEquals(LlmProvider.OLLAMA_NATIVE, config.provider());
        assertTrue(config.uniformProviders());
        assertEquals("gemma3:4b", config.chatModel());
        assertEquals("mxbai-embed-large:latest", config.embeddingModel());
        assertEquals("http://localhost:11434", config.baseUrl());
        assertTrue(config.requiresOllamaNativeChat());
        assertTrue(config.requiresOllamaNativeEmbedding());
    }

    @Test
    void openAiCompatibleDoesNotFallbackToOllamaForEmbeddings() {
        configureOpenAiUniformDefaults();
        llmProperties.getOllama().setDefaultEmbeddingModel("mxbai-embed-large:latest");
        modelCatalog = new LlmModelCatalogService(llmProperties);
        configResolver = new ResolvedLlmConfigResolver(configurationSource, llmProperties, objectMapper, modelCatalog);

        ResolvedLlmConfig config = configResolver.resolve(null, null, null);

        assertEquals(LlmProvider.OPENAI_COMPATIBLE, config.embeddingProvider());
        assertEquals("qwen3-embedding:8b", config.embeddingModel());
        assertNotEquals("mxbai-embed-large:latest", config.embeddingModel());
        assertNotEquals(LlmProvider.OLLAMA_NATIVE, config.embeddingProvider());

        when(clientRegistry.createOpenAiCompatibleEmbeddingClient(config))
                .thenReturn(openAiEmbeddingClient);
        assertEquals(openAiEmbeddingClient, clientResolver.resolveEmbeddingClient(config));
        verify(clientRegistry, never()).ollamaNativeEmbeddingClient();
    }

    private void configureOpenAiUniformDefaults() {
        llmProperties.setDefaultProvider(LlmProvider.OPENAI_COMPATIBLE);
        LlmOpenAiCompatibleDefaults openAi = llmProperties.getOpenAiCompatible();
        openAi.setDefaultBaseUrl("http://litellm:4000");
        openAi.setDefaultChatModel("gpt-oss:20b");
        openAi.setDefaultEmbeddingModel("qwen3-embedding:8b");
        openAi.setAvailableChatModels(List.of("gpt-oss:20b"));
        openAi.setAvailableEmbeddingModels(List.of("qwen3-embedding:8b"));
        openAi.setDefaultApiKeyEnv("PATH");
    }

    /** Simulates rag.llm.ollama.* chained from spring.ai.ollama.* in application.properties. */
    private void simulateSpringAiOllamaLegacyPollution() {
        llmProperties.getOllama().setDefaultChatModel("gemma3:4b");
        llmProperties.getOllama().setDefaultEmbeddingModel("mxbai-embed-large:latest");
        llmProperties.getOllama().setDefaultBaseUrl("http://host.docker.internal:11434");
    }
}
