package com.uniovi.rag.integration.modelmanagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.uniovi.rag.application.port.OllamaModelAvailabilityPort;
import com.uniovi.rag.application.service.llm.catalog.LlmCatalogApiService;
import com.uniovi.rag.application.service.llm.catalog.LlmModelCatalogService;
import com.uniovi.rag.configuration.RagVectorProperties;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.domain.llm.catalog.LlmCatalogRuntimeStatus;
import com.uniovi.rag.domain.llm.catalog.LlmCatalogSource;
import com.uniovi.rag.domain.llm.catalog.LlmModelCapability;
import com.uniovi.rag.domain.product.ProductDemoModel;
import com.uniovi.rag.infrastructure.llm.LlmOpenAiCompatibleDefaults;
import com.uniovi.rag.infrastructure.llm.LlmOllamaDefaults;
import com.uniovi.rag.infrastructure.llm.LlmProperties;
import com.uniovi.rag.interfaces.rest.dto.llm.catalog.LlmCatalogModelDto;
import com.uniovi.rag.interfaces.rest.dto.llm.catalog.LlmCatalogResponseDto;
import com.uniovi.rag.testsupport.llm.LlmModelCatalogTestSupport;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Phase 1 — properties-backed LLM catalog API service. */
class LlmCatalogApiIntegrationTest {

    private LlmCatalogApiService apiService;
    private LlmProperties properties;
    private OllamaModelAvailabilityPort ollamaAvailability;

    @BeforeEach
    void setUp() {
        properties = catalogProperties();
        ollamaAvailability = mock(OllamaModelAvailabilityPort.class);
        when(ollamaAvailability.isModelPresent(anyString())).thenReturn(false);
        apiService =
                new LlmCatalogApiService(
                        new LlmModelCatalogService(properties),
                        ollamaAvailability,
                        new RagVectorProperties(1024, true));
    }

    @Test
    void catalogReturnsConfiguredOllamaChatModelsFromPropertiesOnly() {
        LlmCatalogResponseDto response =
                apiService.listCatalog(LlmProvider.OLLAMA_NATIVE, LlmModelCapability.CHAT, null, false);

        assertThat(response.models())
                .extracting(LlmCatalogModelDto::modelName)
                .containsExactlyInAnyOrder("ollama-chat-a", "ollama-chat-b");
        assertThat(response.models()).allMatch(m -> m.provider() == LlmProvider.OLLAMA_NATIVE);
        assertThat(response.models()).allMatch(m -> m.capability() == LlmModelCapability.CHAT);
        assertThat(response.models()).allMatch(m -> m.source() == LlmCatalogSource.CONFIGURED_CATALOG);
    }

    @Test
    void catalogReturnsConfiguredOpenAiCompatibleChatModelsFromPropertiesOnly() {
        LlmCatalogResponseDto response =
                apiService.listCatalog(LlmProvider.OPENAI_COMPATIBLE, LlmModelCapability.CHAT, null, false);

        assertThat(response.models())
                .extracting(LlmCatalogModelDto::modelName)
                .containsExactly("gpt-oss:20b");
    }

    @Test
    void catalogReturnsOnlyConfiguredEmbeddingModels() {
        LlmCatalogResponseDto ollamaEmb =
                apiService.listCatalog(LlmProvider.OLLAMA_NATIVE, LlmModelCapability.EMBEDDING, null, false);
        LlmCatalogResponseDto openAiEmb =
                apiService.listCatalog(LlmProvider.OPENAI_COMPATIBLE, LlmModelCapability.EMBEDDING, null, false);

        assertThat(ollamaEmb.models())
                .extracting(LlmCatalogModelDto::modelName)
                .containsExactly("mxbai-embed-large:latest");
        assertThat(openAiEmb.models())
                .extracting(LlmCatalogModelDto::modelName)
                .containsExactly("hf.co/mixedbread-ai/mxbai-embed-large-v1:latest");
        assertThat(ollamaEmb.models()).allMatch(m -> !m.selectableByUser());
        assertThat(openAiEmb.models().getFirst().embeddingDimensions()).isEqualTo(1024);
        assertThat(openAiEmb.models().getFirst().compatibleWithCurrentVectorStore()).isTrue();
    }

    @Test
    void catalogDoesNotReturnProductDemoModelUnlessConfigured() {
        assertThat(
                        apiService
                                .listCatalog(null, LlmModelCapability.CHAT, null, false)
                                .models()
                                .stream()
                                .map(LlmCatalogModelDto::modelName)
                                .toList())
                .doesNotContain(
                        ProductDemoModel.MISTRAL_7B.modelId(),
                        ProductDemoModel.GEMMA3_4B.modelId(),
                        ProductDemoModel.LLAMA3_1_8B.modelId());
    }

    @Test
    void catalogCanFilterByProviderAndCapability() {
        LlmCatalogResponseDto filtered =
                apiService.listCatalog(LlmProvider.OPENAI_COMPATIBLE, LlmModelCapability.CHAT, null, false);

        assertThat(filtered.models()).hasSize(1);
        assertThat(filtered.models().getFirst().provider()).isEqualTo(LlmProvider.OPENAI_COMPATIBLE);
        assertThat(filtered.models().getFirst().capability()).isEqualTo(LlmModelCapability.CHAT);
    }

    @Test
    void catalogOpenAiCompatibleRuntimeHonestWithoutOllama() {
        when(ollamaAvailability.isModelPresent(anyString())).thenThrow(new RuntimeException("Ollama down"));

        LlmCatalogResponseDto response =
                apiService.listCatalog(LlmProvider.OPENAI_COMPATIBLE, LlmModelCapability.CHAT, null, true);

        assertThat(response.models()).isNotEmpty();
        assertThat(response.models().getFirst().source()).isEqualTo(LlmCatalogSource.LITELLM_CONFIGURED);
        assertThat(response.models().getFirst().runtimeStatus()).isEqualTo(LlmCatalogRuntimeStatus.NOT_PROBED);
    }

    @Test
    void catalogKeepsConfiguredModelVisibleWhenRuntimeUnavailable() {
        when(ollamaAvailability.isModelPresent("ollama-chat-a")).thenReturn(false);

        LlmCatalogResponseDto response =
                apiService.listCatalog(LlmProvider.OLLAMA_NATIVE, LlmModelCapability.CHAT, null, true);

        LlmCatalogModelDto row =
                response.models().stream()
                        .filter(m -> "ollama-chat-a".equals(m.modelName()))
                        .findFirst()
                        .orElseThrow();
        assertThat(row.available()).isTrue();
        assertThat(row.runtimeStatus()).isEqualTo(LlmCatalogRuntimeStatus.UNAVAILABLE);
        assertThat(row.runtimeDetail()).contains("not installed");
    }

    @Test
    void catalogSelectableFilterLimitsChatUserSelection() {
        LlmCatalogResponseDto selectable =
                apiService.listCatalog(null, LlmModelCapability.CHAT, true, false);

        assertThat(selectable.models()).isNotEmpty();
        assertThat(selectable.models()).allMatch(LlmCatalogModelDto::selectableByUser);
    }

    private static LlmProperties catalogProperties() {
        LlmProperties props = new LlmProperties();
        LlmOllamaDefaults ollama = props.getOllama();
        ollama.setDefaultChatModel("ollama-chat-a");
        ollama.setAvailableChatModels(List.of("ollama-chat-a", "ollama-chat-b"));
        ollama.setDefaultEmbeddingModel("mxbai-embed-large:latest");
        ollama.setAvailableEmbeddingModels(List.of("mxbai-embed-large:latest"));
        LlmOpenAiCompatibleDefaults openAi = props.getOpenAiCompatible();
        openAi.setDefaultBaseUrl("http://litellm:4000");
        openAi.setDefaultChatModel("gpt-oss:20b");
        openAi.setAvailableChatModels(List.of("gpt-oss:20b"));
        openAi.setDefaultEmbeddingModel("hf.co/mixedbread-ai/mxbai-embed-large-v1:latest");
        openAi.setAvailableEmbeddingModels(List.of("hf.co/mixedbread-ai/mxbai-embed-large-v1:latest"));
        return props;
    }
}
