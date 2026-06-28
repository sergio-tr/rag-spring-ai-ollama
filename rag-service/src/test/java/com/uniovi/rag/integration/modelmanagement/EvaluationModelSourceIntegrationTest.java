package com.uniovi.rag.integration.modelmanagement;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniovi.rag.application.service.llm.catalog.LlmModelCatalogService;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.domain.llm.catalog.LlmCatalogQuery;
import com.uniovi.rag.domain.llm.catalog.LlmModelCapability;
import com.uniovi.rag.domain.product.ProductDemoModel;
import com.uniovi.rag.infrastructure.llm.LlmProperties;
import com.uniovi.rag.testsupport.llm.LlmModelCatalogTestSupport;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Phase 2 — evaluation must use properties catalog, not legacy demo/preferred lists. */
class EvaluationModelSourceIntegrationTest {

    @Test
    void llmEvaluationLoadsChatModelsFromCatalog() {
        LlmModelCatalogService catalog =
                LlmModelCatalogTestSupport.catalogFrom(LlmModelCatalogTestSupport.openAiLiteLlmProperties());

        assertThat(
                        catalog.listConfigured(
                                        LlmCatalogQuery.forProviderAndCapability(
                                                LlmProvider.OPENAI_COMPATIBLE, LlmModelCapability.CHAT))
                                .stream()
                                .map(e -> e.modelName())
                                .toList())
                .isNotEmpty();
    }

    @Test
    void ragEvaluationLoadsChatAndEmbeddingModelsFromCatalog() {
        LlmModelCatalogService catalog =
                LlmModelCatalogTestSupport.catalogFrom(LlmModelCatalogTestSupport.openAiLiteLlmProperties());

        assertThat(
                        catalog.listConfigured(
                                        LlmCatalogQuery.forProviderAndCapability(
                                                LlmProvider.OPENAI_COMPATIBLE, LlmModelCapability.CHAT))
                                .size())
                .isGreaterThan(0);
        assertThat(
                        catalog.listConfigured(
                                        LlmCatalogQuery.forProviderAndCapability(
                                                LlmProvider.OPENAI_COMPATIBLE, LlmModelCapability.EMBEDDING))
                                .size())
                .isGreaterThan(0);
    }

    @Test
    void ragEvaluationRejectsIncompatibleEmbeddingModel() {
        assertThat(ProductDemoModel.NOMIC_EMBED_TEXT.fitsStoreEmbeddingDimension(1024)).isFalse();
    }

    @Test
    void evaluationDoesNotUseProductDemoModelAsModelSource() {
        var properties = LlmModelCatalogTestSupport.openAiLiteLlmProperties();
        properties.getOpenAiCompatible().setAvailableChatModels(List.of("catalog-only-eval-chat"));
        LlmModelCatalogService catalog = LlmModelCatalogTestSupport.catalogFrom(properties);

        assertThat(catalog.find(LlmProvider.OPENAI_COMPATIBLE, ProductDemoModel.GEMMA3_4B.modelId(), LlmModelCapability.CHAT))
                .isEmpty();
    }

    @Test
    void evaluationDoesNotUseHardcodedPreferredModels() {
        var properties = new LlmProperties();
        properties.getOllama().setAvailableChatModels(List.of("eval-catalog-only"));
        properties.getOllama().setDefaultChatModel("eval-catalog-only");
        LlmModelCatalogService catalog = new LlmModelCatalogService(properties);

        for (String legacy : List.of("mistral:7b", "gemma3:4b", "llama3.1:8b")) {
            assertThat(catalog.find(LlmProvider.OLLAMA_NATIVE, legacy, LlmModelCapability.CHAT)).isEmpty();
        }
    }
}
