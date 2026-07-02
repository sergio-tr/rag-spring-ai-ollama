package com.uniovi.rag.application.service.knowledge;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.Optional;

import com.uniovi.rag.application.port.llm.LlmClientRegistryPort;
import com.uniovi.rag.application.port.llm.LlmEmbeddingClient;
import com.uniovi.rag.application.port.llm.LlmEmbeddingRequest;
import com.uniovi.rag.application.port.llm.LlmEmbeddingResponse;
import com.uniovi.rag.application.service.config.llm.ResolvedLlmConfigResolver;
import com.uniovi.rag.application.service.llm.LlmClientResolver;
import com.uniovi.rag.application.service.llm.ProviderAwareEmbeddingService;
import com.uniovi.rag.application.service.llm.catalog.EmbeddingModelCatalogResolver;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.domain.llm.ResolvedLlmConfig;
import com.uniovi.rag.infrastructure.persistence.KnowledgeIndexSnapshotRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Ensures snapshot profile enrichment and ingest-time compatibility checks resolve embedding models through the same
 * {@link ProviderAwareEmbeddingService#effectiveEmbeddingModelId(String)} path.
 */
@ExtendWith(MockitoExtension.class)
class DocumentIngestEmbeddingResolutionTest {

    private static final String CATALOG_EMBEDDING = "hf.co/mixedbread-ai/mxbai-embed-large-v1:latest";
    private static final String LEGACY_OLLAMA_EMBEDDING = "mxbai-embed-large:latest";

    @Mock private ResolvedLlmConfigResolver configResolver;
    @Mock private LlmClientRegistryPort clientRegistry;
    @Mock private LlmEmbeddingClient openAiEmbeddingClient;
    @Mock private KnowledgeIndexSnapshotRepository snapshotRepository;
    @Mock private KnowledgeSnapshotService knowledgeSnapshotService;
    @Mock private EmbeddingModelCatalogResolver embeddingModelCatalogResolver;

    private EmbeddingIndexCompatibilityService compatibilityService;

    @BeforeEach
    void setUp() {
        lenient()
                .when(embeddingModelCatalogResolver.resolve(any(LlmProvider.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(1, String.class).trim());
        LlmClientResolver clientResolver = new LlmClientResolver(clientRegistry);
        ProviderAwareEmbeddingService embeddingService =
                new ProviderAwareEmbeddingService(clientResolver, configResolver, embeddingModelCatalogResolver);
        compatibilityService =
                new EmbeddingIndexCompatibilityService(
                        embeddingService, snapshotRepository, knowledgeSnapshotService, embeddingModelCatalogResolver);
        when(configResolver.resolve(null, null, null)).thenReturn(openAiConfig());
        lenient().when(clientRegistry.createOpenAiCompatibleEmbeddingClient(any())).thenReturn(openAiEmbeddingClient);
        lenient()
                .when(openAiEmbeddingClient.embed(any(LlmEmbeddingRequest.class)))
                .thenReturn(new LlmEmbeddingResponse(CATALOG_EMBEDDING, List.of(new float[] {0.1f}), Map.of()));
    }

    @Test
    void enrichIndexProfileAndCompatibilityUseSameEffectiveEmbeddingModel() {
        Map<String, Object> enriched =
                compatibilityService.enrichIndexProfile(projectProfileWithLegacyOllamaId());

        assertEquals(CATALOG_EMBEDDING, enriched.get(IndexProfileJsonSupport.EMBEDDING_MODEL_ID_KEY));
        assertEquals(LlmProvider.OPENAI_COMPATIBLE.name(), enriched.get(IndexProfileJsonSupport.EMBEDDING_PROVIDER_KEY));
        assertDoesNotThrow(() -> compatibilityService.assertIndexingCompatible(enriched));
    }

    @Test
    void legacyMxbaiCatalogAliasIsCompatibleWithDeploymentDefault() {
        when(embeddingModelCatalogResolver.resolveIfAvailable(
                        eq(LlmProvider.OPENAI_COMPATIBLE), anyString()))
                .thenAnswer(
                        inv -> {
                            String id = inv.getArgument(1, String.class).trim();
                            if ("mxbai-embed-large:latest".equals(id) || "mxbai-embed-large".equals(id)) {
                                return Optional.of("mxbai-embed-large");
                            }
                            return Optional.empty();
                        });

        Map<String, Object> enriched =
                compatibilityService.enrichIndexProfile(projectProfileWithLegacyOllamaId());

        assertEquals(CATALOG_EMBEDDING, enriched.get(IndexProfileJsonSupport.EMBEDDING_MODEL_ID_KEY));
        assertDoesNotThrow(() -> compatibilityService.assertIndexingCompatible(enriched));
    }

    private static Map<String, Object> projectProfileWithLegacyOllamaId() {
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put(IndexProfileJsonSupport.EMBEDDING_MODEL_ID_KEY, LEGACY_OLLAMA_EMBEDDING);
        profile.put("chunkMaxChars", 400);
        return profile;
    }

    private static ResolvedLlmConfig openAiConfig() {
        return ResolvedLlmConfig.uniform(
                LlmProvider.OPENAI_COMPATIBLE,
                "http://litellm:4000",
                "gpt-oss:20b",
                CATALOG_EMBEDDING,
                "OPENAI_COMPATIBLE_API_KEY",
                null,
                0.1,
                60_000,
                null,
                Map.of());
    }
}
