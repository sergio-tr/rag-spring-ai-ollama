package com.uniovi.rag.integration.modelmanagement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uniovi.rag.application.port.llm.LlmClientRegistryPort;
import com.uniovi.rag.application.port.llm.LlmEmbeddingClient;
import com.uniovi.rag.application.port.llm.LlmEmbeddingRequest;
import com.uniovi.rag.application.port.llm.LlmEmbeddingResponse;
import com.uniovi.rag.application.service.config.llm.ResolvedLlmConfigResolver;
import com.uniovi.rag.application.service.knowledge.EmbeddingIndexCompatibilityService;
import com.uniovi.rag.application.service.knowledge.IndexProfileJsonSupport;
import com.uniovi.rag.application.service.knowledge.KnowledgeSnapshotService;
import com.uniovi.rag.application.service.llm.LlmClientResolver;
import com.uniovi.rag.application.service.llm.ProviderAwareEmbeddingService;
import com.uniovi.rag.application.service.runtime.llm.OrchestrationLlmConfigScope;
import com.uniovi.rag.configuration.RagVectorProperties;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.domain.llm.ResolvedLlmConfig;
import com.uniovi.rag.domain.product.ProductDemoModel;
import com.uniovi.rag.infrastructure.persistence.KnowledgeIndexSnapshotRepository;
import com.uniovi.rag.infrastructure.vector.EmbeddingSpaceGuard;
import com.uniovi.rag.infrastructure.vector.ProviderAwareEmbeddingModelFactory;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Phase 2 — knowledge ingest embedding provider routing. */
@ExtendWith(MockitoExtension.class)
class KnowledgeIngestionProviderIntegrationTest {

    private static final String LITELLM_EMBEDDING = "qwen3-embedding:8b";
    private static final String OLLAMA_LEGACY_EMBEDDING = "mxbai-embed-large:latest";

    @Mock private ResolvedLlmConfigResolver configResolver;
    @Mock private LlmClientRegistryPort clientRegistry;
    @Mock private LlmEmbeddingClient openAiEmbeddingClient;
    @Mock private KnowledgeIndexSnapshotRepository snapshotRepository;
    @Mock private KnowledgeSnapshotService knowledgeSnapshotService;

    private ProviderAwareEmbeddingService embeddingService;
    private EmbeddingIndexCompatibilityService compatibilityService;
    private EmbeddingSpaceGuard embeddingSpaceGuard;

    @BeforeEach
    void setUp() {
        LlmClientResolver clientResolver = new LlmClientResolver(clientRegistry);
        embeddingService = new ProviderAwareEmbeddingService(clientResolver, configResolver);
        compatibilityService =
                new EmbeddingIndexCompatibilityService(
                        embeddingService, snapshotRepository, knowledgeSnapshotService);
        embeddingSpaceGuard =
                new EmbeddingSpaceGuard(
                        new ProviderAwareEmbeddingModelFactory(embeddingService),
                        new RagVectorProperties(1024, true));
        lenient().when(clientRegistry.createOpenAiCompatibleEmbeddingClient(any())).thenReturn(openAiEmbeddingClient);
        lenient()
                .when(openAiEmbeddingClient.embed(any(LlmEmbeddingRequest.class)))
                .thenReturn(new LlmEmbeddingResponse(LITELLM_EMBEDDING, List.of(new float[] {0.1f}), Map.of()));
    }

    @Test
    void knowledgeIngestWithOpenAiCompatibleUsesOpenAiEmbeddings() {
        when(configResolver.resolve(null, null, null)).thenReturn(openAiConfig());

        Map<String, Object> enriched = compatibilityService.enrichIndexProfile(legacyOllamaProfile());

        assertEquals(LITELLM_EMBEDDING, enriched.get(IndexProfileJsonSupport.EMBEDDING_MODEL_ID_KEY));
        embeddingService.embed(OLLAMA_LEGACY_EMBEDDING, List.of("chunk"));
        ArgumentCaptor<LlmEmbeddingRequest> captor = ArgumentCaptor.forClass(LlmEmbeddingRequest.class);
        verify(openAiEmbeddingClient).embed(captor.capture());
        assertEquals(LITELLM_EMBEDDING, captor.getValue().model());
    }

    @Test
    void knowledgeIngestWithOllamaUsesOllamaEmbeddings() {
        ResolvedLlmConfig ollama =
                ResolvedLlmConfig.uniform(
                        LlmProvider.OLLAMA_NATIVE,
                        "http://localhost:11434",
                        "gemma3:4b",
                        OLLAMA_LEGACY_EMBEDDING,
                        null,
                        null,
                        0.1,
                        60_000,
                        null,
                        Map.of());
        LlmEmbeddingClient ollamaClient = mock(LlmEmbeddingClient.class);
        when(clientRegistry.ollamaNativeEmbeddingClient()).thenReturn(ollamaClient);
        when(ollamaClient.embed(any()))
                .thenReturn(
                        new LlmEmbeddingResponse(OLLAMA_LEGACY_EMBEDDING, List.of(new float[] {0.2f}), Map.of()));
        OrchestrationLlmConfigScope.bind(ollama);

        embeddingService.embed(OLLAMA_LEGACY_EMBEDDING, List.of("chunk"));

        verify(ollamaClient).embed(any());
        OrchestrationLlmConfigScope.clear();
    }

    @Test
    void knowledgeIngestRejectsEmbeddingDimensionMismatch() {
        when(configResolver.resolve(null, null, null)).thenReturn(openAiConfig());
        float[] vector = new float[1024];
        vector[0] = 0.01f;
        when(openAiEmbeddingClient.embed(any(LlmEmbeddingRequest.class)))
                .thenReturn(new LlmEmbeddingResponse(LITELLM_EMBEDDING, List.of(vector), Map.of()));

        assertEquals(1024, embeddingSpaceGuard.assertFitsPhysicalVectorColumnReturning(OLLAMA_LEGACY_EMBEDDING));
    }

    @Test
    void knowledgeIngestDoesNotFallbackToOllamaWhenOpenAiEmbeddingsFail() {
        when(configResolver.resolve(null, null, null)).thenReturn(openAiConfig());
        embeddingService.embed(OLLAMA_LEGACY_EMBEDDING, List.of("chunk"));
        verify(clientRegistry, never()).ollamaNativeEmbeddingClient();
    }

    @Test
    void knowledgeIngestPersistsEmbeddingProviderModelAndDimensions() {
        when(configResolver.resolve(null, null, null)).thenReturn(openAiConfig());
        Map<String, Object> enriched = compatibilityService.enrichIndexProfile(legacyOllamaProfile());
        assertEquals(LlmProvider.OPENAI_COMPATIBLE.name(), enriched.get(IndexProfileJsonSupport.EMBEDDING_PROVIDER_KEY));
        assertNotEquals(OLLAMA_LEGACY_EMBEDDING, enriched.get(IndexProfileJsonSupport.EMBEDDING_MODEL_ID_KEY));
        assertTrue(ProductDemoModel.MXBAI_EMBED_LARGE.fitsStoreEmbeddingDimension(1024));
    }

    private static Map<String, Object> legacyOllamaProfile() {
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put(IndexProfileJsonSupport.EMBEDDING_MODEL_ID_KEY, OLLAMA_LEGACY_EMBEDDING);
        profile.put(IndexProfileJsonSupport.EMBEDDING_PROVIDER_KEY, LlmProvider.OLLAMA_NATIVE.name());
        return profile;
    }

    private static ResolvedLlmConfig openAiConfig() {
        return ResolvedLlmConfig.uniform(
                LlmProvider.OPENAI_COMPATIBLE,
                "http://litellm:4000",
                "gpt-oss:20b",
                LITELLM_EMBEDDING,
                "PATH",
                null,
                0.1,
                60_000,
                null,
                Map.of());
    }
}
