package com.uniovi.rag.application.service.knowledge;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uniovi.rag.application.port.llm.LlmClientRegistryPort;
import com.uniovi.rag.application.port.llm.LlmEmbeddingClient;
import com.uniovi.rag.application.port.llm.LlmEmbeddingRequest;
import com.uniovi.rag.application.port.llm.LlmEmbeddingResponse;
import com.uniovi.rag.application.service.config.llm.ResolvedLlmConfigResolver;
import com.uniovi.rag.application.service.llm.LlmClientResolver;
import com.uniovi.rag.application.service.llm.catalog.EmbeddingModelCatalogResolver;
import com.uniovi.rag.application.service.llm.ProviderAwareEmbeddingService;
import com.uniovi.rag.application.service.runtime.retrieval.DenseRetrievalStrategy;
import com.uniovi.rag.configuration.RagVectorProperties;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.domain.llm.ResolvedLlmConfig;
import com.uniovi.rag.domain.runtime.query.EntityExtractionResult;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalMode;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalRequest;
import com.uniovi.rag.infrastructure.persistence.KnowledgeIndexSnapshotRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeIndexSnapshotEntity;
import com.uniovi.rag.infrastructure.vector.OllamaEmbeddingModelFactory;
import com.uniovi.rag.infrastructure.vector.PgVectorStoreRegistry;
import com.uniovi.rag.infrastructure.vector.ProviderAwareEmbeddingModelFactory;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;

@ExtendWith(MockitoExtension.class)
class EmbeddingIndexProviderAwarenessTest {

    @Mock private ProviderAwareEmbeddingService providerAwareEmbeddingService;
    @Mock private ResolvedLlmConfigResolver configResolver;
    @Mock private KnowledgeIndexSnapshotRepository snapshotRepository;
    @Mock private LlmClientRegistryPort clientRegistry;
    @Mock private LlmEmbeddingClient openAiEmbeddingClient;
    @Mock private LlmEmbeddingClient ollamaEmbeddingClient;
    @Mock private OllamaEmbeddingModelFactory ollamaEmbeddingModelFactory;
    @Mock private EmbeddingModel ollamaSpringEmbeddingModel;
    @Mock private PgVectorStoreRegistry vectorStoreRegistry;
    @Mock private PgVectorStore vectorStore;
    @Mock private RagVectorProperties ragVectorProperties;

    @Mock private KnowledgeSnapshotService knowledgeSnapshotService;
    @Mock private EmbeddingModelCatalogResolver embeddingModelCatalogResolver;

    private EmbeddingIndexCompatibilityService compatibilityService;
    private LlmClientResolver llmClientResolver;
    private ProviderAwareEmbeddingService realEmbeddingService;

    @BeforeEach
    void setUp() {
        lenient()
                .when(embeddingModelCatalogResolver.resolve(any(LlmProvider.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(1, String.class).trim());
        compatibilityService =
                new EmbeddingIndexCompatibilityService(
                        providerAwareEmbeddingService,
                        snapshotRepository,
                        knowledgeSnapshotService,
                        embeddingModelCatalogResolver);
        llmClientResolver = new LlmClientResolver(clientRegistry);
        realEmbeddingService =
                new ProviderAwareEmbeddingService(llmClientResolver, configResolver, embeddingModelCatalogResolver);
        lenient().when(ragVectorProperties.requireSnapshotEmbeddingModelId()).thenReturn(true);
    }

    @Test
    void retrievalRejectsIndexBuiltWithDifferentEmbeddingProvider() {
        ResolvedLlmConfig openAi = openAiConfig();
        when(providerAwareEmbeddingService.resolveEffectiveConfig()).thenReturn(openAi);

        UUID snapshotId = UUID.randomUUID();
        KnowledgeIndexSnapshotEntity snap = legacyOllamaSnapshot(snapshotId);
        when(snapshotRepository.findById(snapshotId)).thenReturn(Optional.of(snap));

        RetrievalRequest req = retrievalRequest(snapshotId, "mxbai-embed-large:latest");

        ResponseStatusException ex =
                assertThrows(ResponseStatusException.class, () -> compatibilityService.assertRetrievalCompatible(req));
        assertTrue(ex.getReason().contains("provider=OPENAI_COMPATIBLE"));
        assertTrue(ex.getReason().contains("embeddingModel=qwen3-embedding:8b"));
        assertTrue(ex.getReason().contains("Reindex is required"));
    }

    @Test
    void retrievalRejectsIndexBuiltWithDifferentEmbeddingModel() {
        ResolvedLlmConfig ollama = ollamaConfig("mxbai-embed-large:latest");
        when(providerAwareEmbeddingService.resolveEffectiveConfig()).thenReturn(ollama);

        UUID snapshotId = UUID.randomUUID();
        KnowledgeIndexSnapshotEntity snap = snapshotWith(
                snapshotId,
                LlmProvider.OLLAMA_NATIVE,
                "nomic-embed-text");
        when(snapshotRepository.findById(snapshotId)).thenReturn(Optional.of(snap));

        RetrievalRequest req = retrievalRequest(snapshotId, "nomic-embed-text");

        ResponseStatusException ex =
                assertThrows(ResponseStatusException.class, () -> compatibilityService.assertRetrievalCompatible(req));
        assertTrue(ex.getReason().contains(EmbeddingIndexCompatibilityService.NO_COMPATIBLE_INDEX_MESSAGE.split("%s")[0]));
    }

    @Test
    void indexingUsesOpenAiCompatibleEmbeddingsWhenProviderIsOpenAiCompatible() throws Exception {
        ResolvedLlmConfig openAi = openAiConfig();
        when(configResolver.resolve(null, null, null)).thenReturn(openAi);
        when(clientRegistry.createOpenAiCompatibleEmbeddingClient(openAi)).thenReturn(openAiEmbeddingClient);
        when(openAiEmbeddingClient.embed(any(LlmEmbeddingRequest.class)))
                .thenReturn(new LlmEmbeddingResponse("qwen3-embedding:8b", List.of(new float[] {0.1f}), Map.of()));

        ProviderAwareEmbeddingModelFactory factory = new ProviderAwareEmbeddingModelFactory(realEmbeddingService);
        EmbeddingModel model = factory.forModel("qwen3-embedding:8b");
        model.call(new EmbeddingRequest(List.of("chunk"), null));

        verify(clientRegistry).createOpenAiCompatibleEmbeddingClient(openAi);
        verify(clientRegistry, never()).ollamaNativeEmbeddingClient();
        verify(openAiEmbeddingClient).embed(any(LlmEmbeddingRequest.class));
    }

    @Test
    void indexingUsesOllamaEmbeddingsWhenProviderIsOllamaNative() {
        ResolvedLlmConfig ollama = ollamaConfig("mxbai-embed-large:latest");
        when(configResolver.resolve(null, null, null)).thenReturn(ollama);
        when(clientRegistry.ollamaNativeEmbeddingClient()).thenReturn(ollamaEmbeddingClient);
        when(ollamaEmbeddingClient.embed(any(LlmEmbeddingRequest.class)))
                .thenReturn(
                        new LlmEmbeddingResponse("mxbai-embed-large:latest", List.of(new float[] {0.2f}), Map.of()));

        ProviderAwareEmbeddingModelFactory factory = new ProviderAwareEmbeddingModelFactory(realEmbeddingService);
        factory.forModel("mxbai-embed-large:latest").call(new EmbeddingRequest(List.of("chunk"), null));

        verify(clientRegistry).ollamaNativeEmbeddingClient();
        verify(clientRegistry, never()).createOpenAiCompatibleEmbeddingClient(any());
        verify(ollamaEmbeddingClient).embed(any(LlmEmbeddingRequest.class));
    }

    @Test
    void openAiCompatibleRetrievalDoesNotCallOllamaOnMissingIndex() {
        ResolvedLlmConfig openAi = openAiConfig();
        when(providerAwareEmbeddingService.resolveEffectiveConfig()).thenReturn(openAi);

        UUID snapshotId = UUID.randomUUID();
        KnowledgeIndexSnapshotEntity legacySnapshot = legacyOllamaSnapshot(snapshotId);
        when(snapshotRepository.findById(snapshotId)).thenReturn(Optional.of(legacySnapshot));

        DenseRetrievalStrategy strategy =
                new DenseRetrievalStrategy(
                        vectorStoreRegistry, vectorStore, ragVectorProperties, compatibilityService, 10, 0.7);

        RetrievalRequest req = retrievalRequest(snapshotId, "mxbai-embed-large:latest");

        assertThrows(ResponseStatusException.class, () -> strategy.retrieve(req));

        verify(vectorStoreRegistry, never()).forEmbeddingModelId(anyString());
        verify(ollamaEmbeddingModelFactory, never()).forModel(anyString());
    }

    private static RetrievalRequest retrievalRequest(UUID snapshotId, String denseModel) {
        return new RetrievalRequest(
                "q",
                Map.of(),
                List.of(),
                List.of(),
                EntityExtractionResult.emptyWithNote(""),
                RetrievalMode.DENSE_ONLY,
                5,
                5,
                10,
                5,
                24_000,
                50,
                List.of(snapshotId),
                UUID.randomUUID(),
                Optional.empty(),
                List.of("all"),
                true,
                Optional.of(denseModel));
    }

    private static KnowledgeIndexSnapshotEntity legacyOllamaSnapshot(UUID id) {
        return snapshotWith(id, null, "mxbai-embed-large:latest");
    }

    private static KnowledgeIndexSnapshotEntity snapshotWith(UUID id, LlmProvider provider, String model) {
        KnowledgeIndexSnapshotEntity snap = mock(KnowledgeIndexSnapshotEntity.class);
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put(IndexProfileJsonSupport.EMBEDDING_MODEL_ID_KEY, model);
        if (provider != null) {
            profile.put(IndexProfileJsonSupport.EMBEDDING_PROVIDER_KEY, provider.name());
        }
        when(snap.getIndexProfileJsonb()).thenReturn(profile);
        return snap;
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

    private static ResolvedLlmConfig ollamaConfig(String embeddingModel) {
        return ResolvedLlmConfig.uniform(
                LlmProvider.OLLAMA_NATIVE,
                "http://localhost:11434",
                "gemma3:4b",
                embeddingModel,
                null,
                null,
                0.1,
                60_000,
                null,
                Map.of());
    }
}
