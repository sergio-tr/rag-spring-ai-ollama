package com.uniovi.rag.application.service.llm.catalog;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.uniovi.rag.application.exception.llm.LlmConfigurationException;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.domain.llm.catalog.LlmCatalogEntry;
import com.uniovi.rag.domain.llm.catalog.LlmCatalogQuery;
import com.uniovi.rag.domain.llm.catalog.LlmModelCapability;
import com.uniovi.rag.domain.llm.catalog.LlmModelUsageContext;
import com.uniovi.rag.domain.product.ProductDemoModel;
import com.uniovi.rag.infrastructure.llm.LlmProperties;
import com.uniovi.rag.testsupport.llm.LlmModelCatalogTestSupport;
import java.util.List;
import org.junit.jupiter.api.Test;

class LlmModelCatalogServiceTest {

    @Test
    void catalogReturnsOnlyConfiguredModels() {
        LlmProperties properties = new LlmProperties();
        properties.getOllama().setAvailableChatModels(List.of("cfg-chat-a", "cfg-chat-b"));
        properties.getOllama().setDefaultChatModel("cfg-chat-a");
        properties.getOllama().setAvailableEmbeddingModels(List.of("cfg-embed-a"));
        properties.getOllama().setDefaultEmbeddingModel("cfg-embed-a");
        LlmModelCatalogService catalog = new LlmModelCatalogService(properties);

        List<String> chatIds =
                catalog.listConfigured(
                                LlmCatalogQuery.forProviderAndCapability(
                                        LlmProvider.OLLAMA_NATIVE, LlmModelCapability.CHAT))
                        .stream()
                        .map(LlmCatalogEntry::modelName)
                        .toList();
        assertEquals(2, chatIds.size());
        assertTrue(chatIds.containsAll(List.of("cfg-chat-a", "cfg-chat-b")));
    }

    @Test
    void catalogSeparatesOllamaAndOpenAiCompatibleProviders() {
        LlmProperties properties = LlmModelCatalogTestSupport.openAiCompatibleCatalogValidationProperties();
        LlmModelCatalogService catalog = LlmModelCatalogTestSupport.catalogFrom(properties);

        assertTrue(catalog.find(LlmProvider.OLLAMA_NATIVE, "gemma3:4b", LlmModelCapability.CHAT).isPresent());
        assertTrue(catalog.find(LlmProvider.OPENAI_COMPATIBLE, "gpt-oss:20b", LlmModelCapability.CHAT).isPresent());
        assertTrue(catalog.find(LlmProvider.OPENAI_COMPATIBLE, "gemma3:4b", LlmModelCapability.CHAT).isEmpty());
    }

    @Test
    void catalogSeparatesChatAndEmbeddingCapabilities() {
        LlmModelCatalogService catalog =
                LlmModelCatalogTestSupport.catalogFrom(LlmModelCatalogTestSupport.openAiLiteLlmProperties());

        assertTrue(catalog.find(LlmProvider.OPENAI_COMPATIBLE, "gpt-oss:20b", LlmModelCapability.CHAT).isPresent());
        assertTrue(catalog.find(LlmProvider.OPENAI_COMPATIBLE, "gpt-oss:20b", LlmModelCapability.EMBEDDING).isEmpty());
        assertTrue(
                catalog.find(LlmProvider.OPENAI_COMPATIBLE, "qwen3-embedding:8b", LlmModelCapability.EMBEDDING)
                        .isPresent());
    }

    @Test
    void catalogDoesNotReturnProductDemoModelUnlessConfigured() {
        LlmProperties properties = new LlmProperties();
        properties.getOllama().setAvailableChatModels(List.of("only-configured-chat"));
        properties.getOllama().setDefaultChatModel("only-configured-chat");
        LlmModelCatalogService catalog = new LlmModelCatalogService(properties);

        assertTrue(catalog.find(LlmProvider.OLLAMA_NATIVE, ProductDemoModel.MISTRAL_7B.modelId(), LlmModelCapability.CHAT).isEmpty());
        assertTrue(catalog.find(LlmProvider.OLLAMA_NATIVE, "only-configured-chat", LlmModelCapability.CHAT).isPresent());
    }

    @Test
    void catalogDoesNotReturnFrontendPreferredModelsUnlessConfigured() {
        LlmProperties properties = new LlmProperties();
        properties.getOllama().setAvailableChatModels(List.of("catalog-only-model"));
        properties.getOllama().setDefaultChatModel("catalog-only-model");
        LlmModelCatalogService catalog = new LlmModelCatalogService(properties);

        for (String legacyPreferred : List.of("gemma3:4b", "mistral:7b", "llama3.1:8b")) {
            assertTrue(catalog.find(LlmProvider.OLLAMA_NATIVE, legacyPreferred, LlmModelCapability.CHAT).isEmpty());
        }
    }

    @Test
    void embeddingCatalogMarks1024DimensionalModelCompatibleWithVector1024() {
        assertTrue(ProductDemoModel.MXBAI_EMBED_LARGE.fitsStoreEmbeddingDimension(1024));
        assertTrue(ProductDemoModel.QWEN3_EMBEDDING.fitsStoreEmbeddingDimension(1024));
    }

    @Test
    void embeddingCatalogMarks4096DimensionalModelIncompatibleWithVector1024() {
        assertFalse(ProductDemoModel.NOMIC_EMBED_TEXT.fitsStoreEmbeddingDimension(1024));
    }

    @Test
    void openAiCompatibleRejectsOllamaChatModelFromSpringAiDefaults() {
        LlmModelCatalogService catalog =
                LlmModelCatalogTestSupport.catalogFrom(
                        LlmModelCatalogTestSupport.openAiCompatibleCatalogValidationProperties());

        LlmConfigurationException ex =
                assertThrows(
                        LlmConfigurationException.class,
                        () ->
                                catalog.assertUsable(
                                        LlmProvider.OPENAI_COMPATIBLE,
                                        "gemma3:4b",
                                        LlmModelCapability.CHAT,
                                        LlmModelUsageContext.RAG_CHAT));
        assertTrue(ex.publicMessage().contains("gemma3:4b"));
        assertTrue(ex.publicMessage().contains("OLLAMA_NATIVE"));
        assertTrue(ex.publicMessage().contains("openai-compatible.available"));
    }

    @Test
    void openAiCompatibleRejectsOllamaEmbeddingModelFromSpringAiDefaults() {
        LlmModelCatalogService catalog =
                LlmModelCatalogTestSupport.catalogFrom(
                        LlmModelCatalogTestSupport.openAiCompatibleCatalogValidationProperties());

        LlmConfigurationException ex =
                assertThrows(
                        LlmConfigurationException.class,
                        () ->
                                catalog.assertUsable(
                                        LlmProvider.OPENAI_COMPATIBLE,
                                        "mxbai-embed-large:latest",
                                        LlmModelCapability.EMBEDDING,
                                        LlmModelUsageContext.RAG_CHAT));
        assertTrue(ex.publicMessage().contains("mxbai-embed-large:latest"));
        assertTrue(ex.publicMessage().contains("OLLAMA_NATIVE"));
        assertTrue(ex.publicMessage().contains("openai-compatible.available"));
    }

    @Test
    void openAiCompatibleAcceptsConfiguredLiteLlmModels() {
        LlmModelCatalogService catalog =
                LlmModelCatalogTestSupport.catalogFrom(
                        LlmModelCatalogTestSupport.openAiCompatibleCatalogValidationProperties());

        assertDoesNotThrow(
                () ->
                        catalog.assertUsable(
                                LlmProvider.OPENAI_COMPATIBLE,
                                "gpt-oss:20b",
                                LlmModelCapability.CHAT,
                                LlmModelUsageContext.RAG_CHAT));
        assertDoesNotThrow(
                () ->
                        catalog.assertUsable(
                                LlmProvider.OPENAI_COMPATIBLE,
                                "hf.co/mixedbread-ai/mxbai-embed-large-v1:latest",
                                LlmModelCapability.EMBEDDING,
                                LlmModelUsageContext.RAG_CHAT));
        assertTrue(
                catalog.find(LlmProvider.OPENAI_COMPATIBLE, "gpt-oss:20b", LlmModelCapability.CHAT).isPresent());
        assertTrue(
                catalog.find(
                                LlmProvider.OPENAI_COMPATIBLE,
                                "hf.co/mixedbread-ai/mxbai-embed-large-v1:latest",
                                LlmModelCapability.EMBEDDING)
                        .isPresent());
    }

    @Test
    void openAiCompatibleCatalogContainsConfiguredGptOssChatModel() {
        LlmModelCatalogService catalog =
                LlmModelCatalogTestSupport.catalogFrom(LlmModelCatalogTestSupport.openAiLiteLlmProperties());

        assertTrue(
                catalog.find(LlmProvider.OPENAI_COMPATIBLE, "gpt-oss:20b", LlmModelCapability.CHAT).isPresent());
    }

    @Test
    void openAiCompatibleCatalogDoesNotAcceptOllamaDefaultGemmaModelUnlessConfigured() {
        LlmModelCatalogService catalog =
                LlmModelCatalogTestSupport.catalogFrom(LlmModelCatalogTestSupport.openAiLiteLlmProperties());

        assertTrue(catalog.find(LlmProvider.OPENAI_COMPATIBLE, "gemma3:4b", LlmModelCapability.CHAT).isEmpty());
        assertThrows(
                LlmConfigurationException.class,
                () ->
                        catalog.assertUsable(
                                LlmProvider.OPENAI_COMPATIBLE,
                                "gemma3:4b",
                                LlmModelCapability.CHAT,
                                LlmModelUsageContext.RAG_CHAT));
    }

    @Test
    void ollamaCatalogContainsConfiguredDefaultChatModel() {
        LlmProperties properties = new LlmProperties();
        properties.getOllama().setAvailableChatModels(List.of("gemma3:4b", "llama3.1:8b"));
        LlmModelCatalogService catalog = new LlmModelCatalogService(properties);

        assertTrue(catalog.find(LlmProvider.OLLAMA_NATIVE, "gemma3:4b", LlmModelCapability.CHAT).isPresent());
    }

    @Test
    void chatModelCannotBeUsedAsEmbeddingModel() {
        LlmModelCatalogService catalog =
                LlmModelCatalogTestSupport.catalogFrom(LlmModelCatalogTestSupport.openAiLiteLlmProperties());

        assertTrue(catalog.find(LlmProvider.OPENAI_COMPATIBLE, "gpt-oss:20b", LlmModelCapability.EMBEDDING).isEmpty());
    }

    @Test
    void missingModelFailsWithClearConfigurationError() {
        LlmModelCatalogService catalog =
                LlmModelCatalogTestSupport.catalogFrom(LlmModelCatalogTestSupport.openAiLiteLlmProperties());

        LlmConfigurationException ex =
                assertThrows(
                        LlmConfigurationException.class,
                        () ->
                                catalog.assertUsable(
                                        LlmProvider.OPENAI_COMPATIBLE,
                                        "unknown-model",
                                        LlmModelCapability.CHAT,
                                        LlmModelUsageContext.RAG_CHAT));
        assertTrue(ex.publicMessage().contains("unknown-model"));
        assertTrue(ex.publicMessage().contains("OPENAI_COMPATIBLE"));
    }

    @Test
    void defaultNotListedIsAddedWithWarningPolicy() {
        LlmProperties properties = new LlmProperties();
        properties.getOllama().setDefaultChatModel("gemma3:4b");
        properties.getOllama().setAvailableChatModels(List.of("llama3.1:8b"));
        LlmModelCatalogService catalog = new LlmModelCatalogService(properties);

        assertDoesNotThrow(
                () ->
                        catalog.assertUsable(
                                LlmProvider.OLLAMA_NATIVE,
                                "gemma3:4b",
                                LlmModelCapability.CHAT,
                                LlmModelUsageContext.SYSTEM_DEFAULT));
        assertEquals(
                2,
                catalog.listConfigured(LlmCatalogQuery.forProviderAndCapability(
                                LlmProvider.OLLAMA_NATIVE, LlmModelCapability.CHAT))
                        .size());
    }
}
