package com.uniovi.rag.application.service.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uniovi.rag.application.port.llm.LlmEmbeddingClient;
import com.uniovi.rag.application.port.llm.LlmEmbeddingRequest;
import com.uniovi.rag.application.port.llm.LlmEmbeddingResponse;
import com.uniovi.rag.application.service.config.llm.ResolvedLlmConfigResolver;
import com.uniovi.rag.application.service.llm.catalog.EmbeddingModelCatalogResolver;
import com.uniovi.rag.application.service.runtime.llm.OrchestrationLlmConfigScope;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.domain.llm.ResolvedLlmConfig;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.EmbeddingRequest;

@ExtendWith(MockitoExtension.class)
class ProviderAwareEmbeddingServiceTest {

    @Mock
    private LlmClientResolver clientResolver;

    @Mock
    private ResolvedLlmConfigResolver configResolver;

    @Mock
    private EmbeddingModelCatalogResolver embeddingModelCatalogResolver;

    @Mock
    private LlmEmbeddingClient openAiEmbeddingClient;

    @Mock
    private LlmEmbeddingClient ollamaEmbeddingClient;

    private ProviderAwareEmbeddingService service;

    @BeforeEach
    void setUp() {
        lenient()
                .when(embeddingModelCatalogResolver.resolve(any(LlmProvider.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(1, String.class).trim());
        service = new ProviderAwareEmbeddingService(clientResolver, configResolver, embeddingModelCatalogResolver);
    }

    @AfterEach
    void tearDown() {
        OrchestrationLlmConfigScope.clear();
    }

    @Test
    void embeddingResolverUsesOpenAiCompatibleEmbeddingModel() {
        providerAwareEmbeddingServiceIgnoresOllamaProfileModelWhenProviderIsOpenAiCompatible();
    }

    @Test
    void embeddingResolverUsesOllamaEmbeddingModelOnlyForOllamaProvider() {
        providerAwareEmbeddingServiceUsesOllamaClientWhenProviderIsOllamaNative();
    }

    @Test
    void providerAwareEmbeddingServiceIgnoresOllamaProfileModelWhenProviderIsOpenAiCompatible() {
        ResolvedLlmConfig config = openAiConfig();
        when(configResolver.resolve(null, null, null)).thenReturn(config);
        when(clientResolver.resolveEmbeddingClient(config)).thenReturn(openAiEmbeddingClient);
        when(openAiEmbeddingClient.embed(any(LlmEmbeddingRequest.class)))
                .thenReturn(new LlmEmbeddingResponse("qwen3-embedding:8b", List.of(new float[] {0.1f}), Map.of()));

        service.embed("mxbai-embed-large:latest", List.of("hola"));

        ArgumentCaptor<LlmEmbeddingRequest> captor = ArgumentCaptor.forClass(LlmEmbeddingRequest.class);
        verify(openAiEmbeddingClient).embed(captor.capture());
        assertEquals("qwen3-embedding:8b", captor.getValue().model());
    }

    @Test
    void effectiveEmbeddingModelIdUsesLiteLlmModelForOpenAiCompatible() {
        when(configResolver.resolve(null, null, null)).thenReturn(openAiConfig());

        assertEquals("qwen3-embedding:8b", service.effectiveEmbeddingModelId("mxbai-embed-large:latest"));
    }

    @Test
    void providerAwareEmbeddingServiceUsesOpenAiClientWhenProviderIsOpenAiCompatible() {
        ResolvedLlmConfig config = openAiConfig();
        when(configResolver.resolve(null, null, null)).thenReturn(config);
        when(clientResolver.resolveEmbeddingClient(config)).thenReturn(openAiEmbeddingClient);
        when(openAiEmbeddingClient.embed(any(LlmEmbeddingRequest.class)))
                .thenReturn(new LlmEmbeddingResponse("qwen3-embedding:8b", List.of(new float[] {0.1f}), Map.of()));

        LlmEmbeddingResponse response = service.embed("qwen3-embedding:8b", List.of("hola"));

        assertEquals("qwen3-embedding:8b", response.model());
        verify(clientResolver).resolveEmbeddingClient(config);
        verify(openAiEmbeddingClient).embed(any(LlmEmbeddingRequest.class));
    }

    @Test
    void providerAwareEmbeddingServiceDoesNotCallOllamaWhenProviderIsOpenAiCompatible() {
        ResolvedLlmConfig config = openAiConfig();
        when(configResolver.resolve(null, null, null)).thenReturn(config);
        when(clientResolver.resolveEmbeddingClient(config)).thenReturn(openAiEmbeddingClient);
        when(openAiEmbeddingClient.embed(any(LlmEmbeddingRequest.class)))
                .thenReturn(new LlmEmbeddingResponse("qwen3-embedding:8b", List.of(new float[] {0.1f}), Map.of()));

        service.embed("qwen3-embedding:8b", List.of("hola"));

        verify(ollamaEmbeddingClient, never()).embed(any());
    }

    @Test
    void providerAwareEmbeddingServiceUsesOllamaClientWhenProviderIsOllamaNative() {
        ResolvedLlmConfig config = ollamaConfig();
        OrchestrationLlmConfigScope.bind(config);
        when(clientResolver.resolveEmbeddingClient(config)).thenReturn(ollamaEmbeddingClient);
        when(ollamaEmbeddingClient.embed(any(LlmEmbeddingRequest.class)))
                .thenReturn(
                        new LlmEmbeddingResponse(
                                "mxbai-embed-large:latest", List.of(new float[] {0.2f, 0.3f}), Map.of()));

        LlmEmbeddingResponse response = service.embed("mxbai-embed-large:latest", List.of("ping"));

        assertEquals(2, response.embeddings().getFirst().length);
        verify(clientResolver).resolveEmbeddingClient(config);
        verify(ollamaEmbeddingClient).embed(any(LlmEmbeddingRequest.class));
        verify(configResolver, never()).resolve(any(), any(), any());
    }

    @Test
    void embeddingModelForDelegatesToResolvedClient() {
        ResolvedLlmConfig config = openAiConfig();
        when(configResolver.resolve(null, null, null)).thenReturn(config);
        when(clientResolver.resolveEmbeddingClient(config)).thenReturn(openAiEmbeddingClient);
        when(openAiEmbeddingClient.embed(any(LlmEmbeddingRequest.class)))
                .thenReturn(new LlmEmbeddingResponse("qwen3-embedding:8b", List.of(new float[] {1f}), Map.of()));

        var model = service.embeddingModelFor("qwen3-embedding:8b");
        var springResponse = model.call(new EmbeddingRequest(List.of("x"), null));

        assertEquals(1, springResponse.getResults().size());
        assertEquals(1, springResponse.getResult().getOutput().length);
    }

    private static ResolvedLlmConfig openAiConfig() {
        return ResolvedLlmConfig.uniform(
                LlmProvider.OPENAI_COMPATIBLE,
                "http://litellm:4000",
                "gpt-oss:20b",
                "qwen3-embedding:8b",
                "OPENAI_COMPATIBLE_API_KEY",
                null,
                0.1,
                60_000,
                null,
                Map.of());
    }

    private static ResolvedLlmConfig ollamaConfig() {
        return ResolvedLlmConfig.uniform(
                LlmProvider.OLLAMA_NATIVE,
                "http://localhost:11434",
                "gemma3:4b",
                "mxbai-embed-large:latest",
                null,
                null,
                0.1,
                60_000,
                null,
                Map.of());
    }
}
