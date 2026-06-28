package com.uniovi.rag.integration.modelmanagement;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniovi.rag.application.service.llm.catalog.LlmModelCatalogService;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.domain.llm.catalog.LlmCatalogQuery;
import com.uniovi.rag.domain.llm.catalog.LlmModelCapability;
import com.uniovi.rag.domain.product.ProductDemoModel;
import com.uniovi.rag.testsupport.llm.LlmModelCatalogTestSupport;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Phase 2 — catalog from configured properties (no runtime provider probes). */
class ModelCatalogIntegrationTest {

    @Test
    void getCatalogReturnsConfiguredModelsFromProperties() {
        LlmModelCatalogService catalog =
                LlmModelCatalogTestSupport.catalogFrom(LlmModelCatalogTestSupport.openAiLiteLlmProperties());

        List<String> openAiChat =
                catalog.listConfigured(
                                LlmCatalogQuery.forProviderAndCapability(
                                        LlmProvider.OPENAI_COMPATIBLE, LlmModelCapability.CHAT))
                        .stream()
                        .map(e -> e.modelName())
                        .toList();

        assertThat(openAiChat).contains("gpt-oss:20b");
    }

    @Test
    void getCatalogFiltersByProvider() {
        LlmModelCatalogService catalog =
                LlmModelCatalogTestSupport.catalogFrom(LlmModelCatalogTestSupport.openAiLiteLlmProperties());

        long ollamaCount =
                catalog.listConfigured(LlmCatalogQuery.forProvider(LlmProvider.OLLAMA_NATIVE)).size();
        long openAiCount =
                catalog.listConfigured(LlmCatalogQuery.forProvider(LlmProvider.OPENAI_COMPATIBLE)).size();

        assertThat(ollamaCount).isGreaterThan(0);
        assertThat(openAiCount).isGreaterThan(0);
    }

    @Test
    void getCatalogFiltersByCapability() {
        LlmModelCatalogService catalog =
                LlmModelCatalogTestSupport.catalogFrom(LlmModelCatalogTestSupport.openAiLiteLlmProperties());

        assertThat(
                        catalog.listConfigured(
                                        LlmCatalogQuery.forProviderAndCapability(
                                                LlmProvider.OPENAI_COMPATIBLE, LlmModelCapability.EMBEDDING))
                                .stream()
                                .map(e -> e.modelName())
                                .toList())
                .contains("qwen3-embedding:8b");
    }

    @Test
    void getCatalogIncludesEmbeddingDimensionCompatibility() {
        assertThat(ProductDemoModel.MXBAI_EMBED_LARGE.fitsStoreEmbeddingDimension(1024))
                .isTrue();
        assertThat(ProductDemoModel.NOMIC_EMBED_TEXT.fitsStoreEmbeddingDimension(1024))
                .isFalse();
    }

    @Test
    void getCatalogDoesNotExposeHardcodedLegacyModels() {
        var properties = LlmModelCatalogTestSupport.openAiLiteLlmProperties();
        properties.getOpenAiCompatible().setAvailableChatModels(List.of("gpt-oss:20b"));
        LlmModelCatalogService catalog = LlmModelCatalogTestSupport.catalogFrom(properties);

        assertThat(catalog.find(LlmProvider.OPENAI_COMPATIBLE, "mistral:7b", LlmModelCapability.CHAT)).isEmpty();
    }
}
