package com.uniovi.rag.application.service.runtime.retrieval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uniovi.rag.application.service.knowledge.EmbeddingIndexCompatibilityService;
import com.uniovi.rag.application.service.knowledge.IndexProfileJsonSupport;
import com.uniovi.rag.application.service.knowledge.KnowledgeSnapshotService;
import com.uniovi.rag.application.service.llm.ProviderAwareEmbeddingService;
import com.uniovi.rag.application.service.llm.catalog.EmbeddingModelCatalogResolver;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.domain.llm.ResolvedLlmConfig;
import com.uniovi.rag.domain.knowledge.MaterializationStrategy;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.domain.runtime.RagExecutionContext;
import com.uniovi.rag.domain.runtime.RagExecutionContextHolder;
import com.uniovi.rag.infrastructure.persistence.KnowledgeIndexSnapshotRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeIndexSnapshotEntity;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class MetadataToolsProviderAwarenessTest {

    private static final UUID PROJECT_ID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");

    @Mock private ProviderAwareEmbeddingService providerAwareEmbeddingService;
    @Mock private KnowledgeIndexSnapshotRepository snapshotRepository;
    @Mock private KnowledgeSnapshotService knowledgeSnapshotService;
    @Mock private ContextRetriever delegateRetriever;
    @Mock private EmbeddingModelCatalogResolver embeddingModelCatalogResolver;

    private EmbeddingIndexCompatibilityService compatibilityService;
    private ProviderAwareContextRetriever providerAwareRetriever;

    @BeforeEach
    void setUp() {
        compatibilityService =
                new EmbeddingIndexCompatibilityService(
                        providerAwareEmbeddingService,
                        snapshotRepository,
                        knowledgeSnapshotService,
                        embeddingModelCatalogResolver);
        providerAwareRetriever = new ProviderAwareContextRetriever(delegateRetriever, compatibilityService);
        RagExecutionContextHolder.set(
                new RagExecutionContext(
                        null,
                        null,
                        PROJECT_ID.toString(),
                        minimalRagConfig(),
                        List.of(RagExecutionContext.ALL_DOCUMENTS),
                        "trace-metadata-tools"));
    }

    @AfterEach
    void tearDown() {
        RagExecutionContextHolder.clear();
    }

    @Test
    void metadataToolDoesNotUseOllamaWhenProviderIsOpenAiCompatible() {
        ResolvedLlmConfig openAi = openAiConfig();
        when(providerAwareEmbeddingService.resolveEffectiveConfig()).thenReturn(openAi);
        KnowledgeIndexSnapshotEntity active = compatibleSnapshot(openAi);
        when(knowledgeSnapshotService.findActiveProjectSnapshot(PROJECT_ID)).thenReturn(Optional.of(active));
        when(delegateRetriever.retrieve("acta reunion")).thenReturn(List.of(new Document("doc")));

        List<Document> docs = providerAwareRetriever.retrieve("acta reunion");

        assertEquals(1, docs.size());
        verify(delegateRetriever).retrieve("acta reunion");
        verify(knowledgeSnapshotService).findActiveProjectSnapshot(PROJECT_ID);
    }

    @Test
    void metadataFallbackUsesProviderAwareRetrieval() {
        ResolvedLlmConfig openAi = openAiConfig();
        when(providerAwareEmbeddingService.resolveEffectiveConfig()).thenReturn(openAi);
        KnowledgeIndexSnapshotEntity active = compatibleSnapshot(openAi);
        when(knowledgeSnapshotService.findActiveProjectSnapshot(PROJECT_ID)).thenReturn(Optional.of(active));
        JSONObject filters = new JSONObject().put("date_iso", "2024-01-15");
        when(delegateRetriever.retrieveWithMetadataFilters("acta", filters))
                .thenReturn(List.of(new Document("filtered")));

        List<Document> docs = providerAwareRetriever.retrieveWithMetadataFilters("acta", filters);

        assertEquals(1, docs.size());
        verify(knowledgeSnapshotService).findActiveProjectSnapshot(PROJECT_ID);
        verify(delegateRetriever).retrieveWithMetadataFilters("acta", filters);
    }

    @Test
    void metadataToolFailsClearlyWhenCompatibleIndexIsMissing() {
        ResolvedLlmConfig openAi = openAiConfig();
        when(providerAwareEmbeddingService.resolveEffectiveConfig()).thenReturn(openAi);
        KnowledgeIndexSnapshotEntity legacyOllama =
                snapshotWith(LlmProvider.OLLAMA_NATIVE, "mxbai-embed-large:latest");
        when(knowledgeSnapshotService.findActiveProjectSnapshot(PROJECT_ID)).thenReturn(Optional.of(legacyOllama));

        ResponseStatusException ex =
                assertThrows(ResponseStatusException.class, () -> providerAwareRetriever.retrieve("acta"));

        assertTrue(ex.getReason().contains("No compatible vector index found"));
        assertTrue(ex.getReason().contains("provider=OPENAI_COMPATIBLE"));
        assertTrue(ex.getReason().contains("embeddingModel=qwen3-embedding:8b"));
        assertTrue(ex.getReason().contains("Reindex is required"));
        verify(delegateRetriever, never()).retrieve(any());
    }

    private static RagConfig minimalRagConfig() {
        return new RagConfig(
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                true,
                false,
                false,
                false,
                5,
                0.7,
                "l",
                "e",
                "c",
                "r",
                false,
                RagConfig.DEFAULT_NAIVE_FULL_CORPUS_MAX_CHARS,
                RagConfig.DEFAULT_ADVANCED_RETRIEVAL_MAX_CONTEXT_CHARS,
                MaterializationStrategy.CHUNK_LEVEL);
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

    private static KnowledgeIndexSnapshotEntity compatibleSnapshot(ResolvedLlmConfig config) {
        return snapshotWith(config.embeddingProvider(), config.embeddingModel());
    }

    private static KnowledgeIndexSnapshotEntity snapshotWith(LlmProvider provider, String model) {
        KnowledgeIndexSnapshotEntity snap = mock(KnowledgeIndexSnapshotEntity.class);
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put(IndexProfileJsonSupport.EMBEDDING_MODEL_ID_KEY, model);
        if (provider != null) {
            profile.put(IndexProfileJsonSupport.EMBEDDING_PROVIDER_KEY, provider.name());
        }
        when(snap.getIndexProfileJsonb()).thenReturn(profile);
        return snap;
    }
}
