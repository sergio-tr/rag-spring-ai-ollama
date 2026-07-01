package com.uniovi.rag.integration.modelmanagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.uniovi.rag.application.service.llm.catalog.EvaluationModelCatalogService;
import com.uniovi.rag.application.service.llm.catalog.LlmCatalogApiService;
import com.uniovi.rag.application.service.llm.catalog.LlmModelCatalogService;
import com.uniovi.rag.application.service.config.llm.ResolvedLlmConfigResolver;
import com.uniovi.rag.configuration.RagVectorProperties;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.domain.llm.ResolvedLlmConfig;
import com.uniovi.rag.domain.llm.catalog.LlmCatalogQuery;
import com.uniovi.rag.domain.llm.catalog.LlmModelCapability;
import com.uniovi.rag.domain.llm.catalog.LlmCatalogSource;
import com.uniovi.rag.domain.llm.catalog.LlmModelReasonCodes;
import com.uniovi.rag.domain.product.ProductDemoModel;
import com.uniovi.rag.infrastructure.llm.LlmProperties;
import com.uniovi.rag.testsupport.llm.LlmCatalogApiServiceTestSupport;
import com.uniovi.rag.testsupport.llm.LlmModelCatalogTestSupport;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/** Phase 5 — evaluation must use properties catalog, not legacy demo/preferred lists. */
@ExtendWith(MockitoExtension.class)
class EvaluationModelSourceIntegrationTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-4000-8000-000000000099");

    @Mock private ResolvedLlmConfigResolver configResolver;

    private LlmModelCatalogService catalog;
    private EvaluationModelCatalogService evaluationModelCatalogService;

    @BeforeEach
    void setUp() {
        LlmProperties properties = LlmModelCatalogTestSupport.openAiLiteLlmProperties();
        catalog = LlmModelCatalogTestSupport.catalogFrom(properties);
        LlmCatalogApiService catalogApiService =
                LlmCatalogApiServiceTestSupport.service(
                        catalog,
                        model -> true,
                        new RagVectorProperties(1024, true));
        evaluationModelCatalogService = new EvaluationModelCatalogService(configResolver, catalogApiService);
        lenient().when(configResolver.resolve(any(), isNull(), isNull())).thenReturn(openAiConfig(properties));
    }

    @Test
    void evaluationCatalogApiOpenAiWithoutOllama() {
        LlmCatalogApiService catalogApiService =
                LlmCatalogApiServiceTestSupport.service(
                        catalog,
                        model -> {
                            throw new RuntimeException("Ollama should not be probed");
                        },
                        new RagVectorProperties(1024, true));

        var response =
                catalogApiService.listCatalog(
                        LlmProvider.OPENAI_COMPATIBLE, LlmModelCapability.CHAT, null, true);

        assertThat(response.models()).isNotEmpty();
        assertThat(response.models().getFirst().source())
                .isEqualTo(LlmCatalogSource.LITELLM_CONFIGURED);
    }

    @Test
    void ragEvaluationUsesCatalogChatModels() {
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
    void ragEvaluationUsesCatalogEmbeddingModels() {
        assertThat(
                        catalog.listConfigured(
                                        LlmCatalogQuery.forProviderAndCapability(
                                                LlmProvider.OPENAI_COMPATIBLE, LlmModelCapability.EMBEDDING))
                                .size())
                .isGreaterThan(0);
    }

    @Test
    void ragEvaluationRejectsEmbeddingModelIncompatibleWithVectorStore() {
        LlmProperties properties = LlmModelCatalogTestSupport.openAiLiteLlmProperties();
        properties.getOllama().setAvailableEmbeddingModels(List.of("nomic-embed-text"));
        properties.getOllama().setDefaultEmbeddingModel("nomic-embed-text");
        LlmModelCatalogService isolatedCatalog = LlmModelCatalogTestSupport.catalogFrom(properties);
        LlmCatalogApiService catalogApiService =
                LlmCatalogApiServiceTestSupport.service(
                        isolatedCatalog,
                        model -> true,
                        new RagVectorProperties(1024, true));
        EvaluationModelCatalogService isolatedEval =
                new EvaluationModelCatalogService(configResolver, catalogApiService);
        when(configResolver.resolve(any(), isNull(), isNull()))
                .thenReturn(
                        new ResolvedLlmConfig(
                                LlmProvider.OPENAI_COMPATIBLE,
                                LlmProvider.OLLAMA_NATIVE,
                                properties.getOpenAiCompatible().getDefaultBaseUrl(),
                                properties.getOpenAiCompatible().getDefaultChatModel(),
                                "nomic-embed-text",
                                properties.getOpenAiCompatible().getDefaultApiKeyEnv(),
                                null,
                                null,
                                null,
                                null,
                                Map.of()));

        assertThatThrownBy(
                        () ->
                                isolatedEval.assertEmbeddingCompatibleWithVectorStore(
                                        USER_ID, ProductDemoModel.NOMIC_EMBED_TEXT.modelId()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(
                        ex ->
                                assertThat(((ResponseStatusException) ex).getReason())
                                        .isEqualTo(
                                                LlmModelReasonCodes
                                                        .EMBEDDING_MODEL_INCOMPATIBLE_WITH_VECTOR_STORE));
    }

    @Test
    void evaluationDoesNotUseProductDemoModelAsSourceOfTruth() {
        var properties = LlmModelCatalogTestSupport.openAiLiteLlmProperties();
        properties.getOpenAiCompatible().setAvailableChatModels(List.of("catalog-only-eval-chat"));
        LlmModelCatalogService isolated = LlmModelCatalogTestSupport.catalogFrom(properties);

        assertThat(
                        isolated.find(
                                LlmProvider.OPENAI_COMPATIBLE,
                                ProductDemoModel.GEMMA3_4B.modelId(),
                                LlmModelCapability.CHAT))
                .isEmpty();
    }

    @Test
    void evaluationDoesNotUseHardcodedPreferredModels() {
        var properties = new LlmProperties();
        properties.getOllama().setAvailableChatModels(List.of("eval-catalog-only"));
        properties.getOllama().setDefaultChatModel("eval-catalog-only");
        LlmModelCatalogService isolated = new LlmModelCatalogService(properties);

        for (String legacy : List.of("mistral:7b", "gemma3:4b", "llama3.1:8b")) {
            assertThat(isolated.find(LlmProvider.OLLAMA_NATIVE, legacy, LlmModelCapability.CHAT)).isEmpty();
        }
    }

    private static ResolvedLlmConfig openAiConfig(LlmProperties properties) {
        return ResolvedLlmConfig.uniform(
                LlmProvider.OPENAI_COMPATIBLE,
                properties.getOpenAiCompatible().getDefaultBaseUrl(),
                properties.getOpenAiCompatible().getDefaultChatModel(),
                properties.getOpenAiCompatible().getDefaultEmbeddingModel(),
                properties.getOpenAiCompatible().getDefaultApiKeyEnv(),
                null,
                null,
                null,
                null,
                Map.of());
    }
}
