package com.uniovi.rag.application.service.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uniovi.rag.application.port.llm.LlmClientRegistryPort;
import com.uniovi.rag.application.port.llm.LlmEmbeddingClient;
import com.uniovi.rag.application.port.llm.LlmEmbeddingRequest;
import com.uniovi.rag.application.port.llm.LlmEmbeddingResponse;
import com.uniovi.rag.application.service.config.llm.ResolvedLlmConfigResolver;
import com.uniovi.rag.application.service.llm.LlmClientResolver;
import com.uniovi.rag.application.service.llm.ProviderAwareEmbeddingService;
import com.uniovi.rag.configuration.RagVectorProperties;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.domain.llm.ResolvedLlmConfig;
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
import org.springframework.ai.embedding.EmbeddingRequest;

/**
 * Knowledge ingest embedding selection: {@link LlmProvider#OPENAI_COMPATIBLE} must use LiteLLM embedding model, not
 * legacy {@code spring.ai.ollama.embedding.model} values carried in project index profiles.
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeIngestEmbeddingProviderTest {

    private static final String LITELLM_EMBEDDING = "qwen3-embedding:8b";
    private static final String OLLAMA_LEGACY_EMBEDDING = "mxbai-embed-large:latest";

    @Mock private ResolvedLlmConfigResolver configResolver;
    @Mock private LlmClientRegistryPort clientRegistry;
    @Mock private LlmEmbeddingClient openAiEmbeddingClient;
    @Mock private KnowledgeIndexSnapshotRepository snapshotRepository;
    @Mock private KnowledgeSnapshotService knowledgeSnapshotService;

    private ProviderAwareEmbeddingService embeddingService;
    private EmbeddingIndexCompatibilityService compatibilityService;
    private ProviderAwareEmbeddingModelFactory embeddingModelFactory;
    private EmbeddingSpaceGuard embeddingSpaceGuard;

    @BeforeEach
    void setUp() {
        LlmClientResolver clientResolver = new LlmClientResolver(clientRegistry);
        embeddingService = new ProviderAwareEmbeddingService(clientResolver, configResolver);
        compatibilityService =
                new EmbeddingIndexCompatibilityService(
                        embeddingService, snapshotRepository, knowledgeSnapshotService);
        embeddingModelFactory = new ProviderAwareEmbeddingModelFactory(embeddingService);
        embeddingSpaceGuard = new EmbeddingSpaceGuard(embeddingModelFactory, new RagVectorProperties(1024, true));
        stubOpenAiEmbeddingClient();
    }

    @Test
    void knowledgeIngestWithOpenAiCompatibleUsesLiteLlmEmbeddingModel() {
        stubOpenAiResolvedConfig();

        Map<String, Object> enriched =
                compatibilityService.enrichIndexProfile(legacyOllamaProfile());

        assertEquals(LITELLM_EMBEDDING, enriched.get(IndexProfileJsonSupport.EMBEDDING_MODEL_ID_KEY));
        assertEquals(LlmProvider.OPENAI_COMPATIBLE.name(), enriched.get(IndexProfileJsonSupport.EMBEDDING_PROVIDER_KEY));

        embeddingService.embed(OLLAMA_LEGACY_EMBEDDING, List.of("chunk"));

        ArgumentCaptor<LlmEmbeddingRequest> captor = ArgumentCaptor.forClass(LlmEmbeddingRequest.class);
        verify(openAiEmbeddingClient).embed(captor.capture());
        assertEquals(LITELLM_EMBEDDING, captor.getValue().model());
    }

    @Test
    void knowledgeIngestWithOpenAiCompatibleDoesNotUseSpringAiOllamaEmbeddingModel() {
        stubOpenAiResolvedConfig();

        Map<String, Object> enriched =
                compatibilityService.enrichIndexProfile(legacyOllamaProfile());

        assertNotEquals(OLLAMA_LEGACY_EMBEDDING, enriched.get(IndexProfileJsonSupport.EMBEDDING_MODEL_ID_KEY));

        embeddingModelFactory
                .forModel(OLLAMA_LEGACY_EMBEDDING)
                .call(new EmbeddingRequest(List.of("probe"), null));

        ArgumentCaptor<LlmEmbeddingRequest> captor = ArgumentCaptor.forClass(LlmEmbeddingRequest.class);
        verify(openAiEmbeddingClient).embed(captor.capture());
        assertEquals(LITELLM_EMBEDDING, captor.getValue().model());
        assertNotEquals(OLLAMA_LEGACY_EMBEDDING, captor.getValue().model());
    }

    @Test
    void embeddingSpaceGuardChecksLiteLlmEmbeddingModelWhenProviderIsOpenAiCompatible() {
        stubOpenAiResolvedConfig();
        float[] vector = new float[1024];
        vector[0] = 0.01f;
        when(openAiEmbeddingClient.embed(any(LlmEmbeddingRequest.class)))
                .thenReturn(new LlmEmbeddingResponse(LITELLM_EMBEDDING, List.of(vector), Map.of()));

        int dims = embeddingSpaceGuard.assertFitsPhysicalVectorColumnReturning(OLLAMA_LEGACY_EMBEDDING);

        assertEquals(1024, dims);
        ArgumentCaptor<LlmEmbeddingRequest> captor = ArgumentCaptor.forClass(LlmEmbeddingRequest.class);
        verify(openAiEmbeddingClient).embed(captor.capture());
        assertEquals(LITELLM_EMBEDDING, captor.getValue().model());
        assertNotEquals(OLLAMA_LEGACY_EMBEDDING, captor.getValue().model());
    }

    private void stubOpenAiResolvedConfig() {
        when(configResolver.resolve(null, null, null)).thenReturn(openAiConfig());
    }

    private void stubOpenAiEmbeddingClient() {
        lenient().when(clientRegistry.createOpenAiCompatibleEmbeddingClient(any())).thenReturn(openAiEmbeddingClient);
        lenient()
                .when(openAiEmbeddingClient.embed(any(LlmEmbeddingRequest.class)))
                .thenReturn(new LlmEmbeddingResponse(LITELLM_EMBEDDING, List.of(new float[] {0.1f}), Map.of()));
    }

    private static Map<String, Object> legacyOllamaProfile() {
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put(IndexProfileJsonSupport.EMBEDDING_MODEL_ID_KEY, OLLAMA_LEGACY_EMBEDDING);
        profile.put(IndexProfileJsonSupport.EMBEDDING_PROVIDER_KEY, LlmProvider.OLLAMA_NATIVE.name());
        profile.put("chunkMaxChars", 400);
        return profile;
    }

    private static ResolvedLlmConfig openAiConfig() {
        return ResolvedLlmConfig.uniform(
                LlmProvider.OPENAI_COMPATIBLE,
                "http://litellm:4000",
                "gpt-oss:20b",
                LITELLM_EMBEDDING,
                "OPENAI_COMPATIBLE_API_KEY",
                null,
                0.1,
                60_000,
                null,
                Map.of());
    }
}
