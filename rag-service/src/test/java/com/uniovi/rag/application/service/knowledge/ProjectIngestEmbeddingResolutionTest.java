package com.uniovi.rag.application.service.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.uniovi.rag.application.port.llm.LlmClientRegistryPort;
import com.uniovi.rag.application.port.llm.LlmEmbeddingClient;
import com.uniovi.rag.application.port.llm.LlmEmbeddingRequest;
import com.uniovi.rag.application.port.llm.LlmEmbeddingResponse;
import com.uniovi.rag.application.service.config.llm.ResolvedLlmConfigResolver;
import com.uniovi.rag.application.service.llm.LlmClientResolver;
import com.uniovi.rag.application.service.llm.ProviderAwareEmbeddingService;
import com.uniovi.rag.application.service.llm.catalog.EmbeddingModelCatalogResolver;
import com.uniovi.rag.domain.knowledge.MaterializationStrategy;
import com.uniovi.rag.domain.knowledge.ProjectIndexProfile;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.domain.llm.ResolvedLlmConfig;
import com.uniovi.rag.infrastructure.persistence.KnowledgeIndexSnapshotRepository;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
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

@ExtendWith(MockitoExtension.class)
class ProjectIngestEmbeddingResolutionTest {

    private static final String SNOWFLAKE = "snowflake-arctic-embed2";
    private static final String MXBAI_CATALOG = "hf.co/mixedbread-ai/mxbai-embed-large-v1:latest";
    private static final UUID PROJECT_ID = UUID.randomUUID();

    @Mock private ProjectIndexProfileService projectIndexProfileService;
    @Mock private ResolvedLlmConfigResolver configResolver;
    @Mock private LlmClientRegistryPort clientRegistry;
    @Mock private LlmEmbeddingClient openAiEmbeddingClient;
    @Mock private KnowledgeIndexSnapshotRepository snapshotRepository;
    @Mock private KnowledgeSnapshotService knowledgeSnapshotService;
    @Mock private EmbeddingModelCatalogResolver embeddingModelCatalogResolver;

    private ProviderAwareEmbeddingService embeddingService;
    private ProjectIndexProfileResolver projectIndexProfileResolver;
    private EmbeddingIndexCompatibilityService compatibilityService;

    @BeforeEach
    void setUp() {
        lenient()
                .when(embeddingModelCatalogResolver.resolveIfAvailable(eq(LlmProvider.OPENAI_COMPATIBLE), anyString()))
                .thenAnswer(
                        inv -> {
                            String id = inv.getArgument(1, String.class).trim();
                            if (SNOWFLAKE.equals(id)) {
                                return Optional.of(SNOWFLAKE);
                            }
                            if ("mxbai-embed-large:latest".equals(id) || "mxbai-embed-large".equals(id)) {
                                return Optional.of(MXBAI_CATALOG);
                            }
                            return Optional.empty();
                        });
        lenient()
                .when(embeddingModelCatalogResolver.resolve(eq(LlmProvider.OPENAI_COMPATIBLE), anyString()))
                .thenAnswer(inv -> inv.getArgument(1, String.class).trim());
        lenient().when(configResolver.resolve(null, null, null)).thenReturn(openAiConfig());
        lenient().when(clientRegistry.createOpenAiCompatibleEmbeddingClient(any())).thenReturn(openAiEmbeddingClient);
        lenient()
                .when(openAiEmbeddingClient.embed(any(LlmEmbeddingRequest.class)))
                .thenReturn(new LlmEmbeddingResponse(MXBAI_CATALOG, List.of(new float[] {0.1f}), Map.of()));

        LlmClientResolver clientResolver = new LlmClientResolver(clientRegistry);
        embeddingService =
                new ProviderAwareEmbeddingService(clientResolver, configResolver, embeddingModelCatalogResolver);
        projectIndexProfileResolver =
                new ProjectIndexProfileResolver(
                        projectIndexProfileService, embeddingService, embeddingModelCatalogResolver);
        compatibilityService =
                new EmbeddingIndexCompatibilityService(
                        embeddingService, snapshotRepository, knowledgeSnapshotService, embeddingModelCatalogResolver);
    }

    @Test
    void snowflakeProfileResolvesSnowflakeForProjectUpload() {
        when(projectIndexProfileService.ensureDefault(PROJECT_ID)).thenReturn(snowflakeProfile());

        ProjectIndexProfileResolver.ResolvedIngestionIndexProfile resolved =
                projectIndexProfileResolver.resolveForIngestion(PROJECT_ID);

        assertEquals(SNOWFLAKE, resolved.activeProfileEmbeddingModel());
        assertEquals(SNOWFLAKE, resolved.resolvedEmbeddingModel());
        assertEquals(SNOWFLAKE, resolved.snapshotIndexProfileJsonb().get(IndexProfileJsonSupport.EMBEDDING_MODEL_ID_KEY));
    }

    @Test
    void snowflakeDoesNotResolveToMxbaiDuringProjectUpload() {
        when(projectIndexProfileService.ensureDefault(PROJECT_ID)).thenReturn(snowflakeProfile());

        ProjectIndexProfileResolver.ResolvedIngestionIndexProfile resolved =
                projectIndexProfileResolver.resolveForIngestion(PROJECT_ID);

        assertThat(resolved.resolvedEmbeddingModel()).isNotEqualTo(MXBAI_CATALOG);
        assertThat(IndexProfileJsonSupport.mixedbreadMxbaiAliasFamily(resolved.resolvedEmbeddingModel()))
                .isFalse();
        assertThat(embeddingService.effectiveEmbeddingModelId(null)).isEqualTo(MXBAI_CATALOG);
    }

    @Test
    void projectIngestCompatibilityUsesProfileModelNotUserDefault() {
        when(projectIndexProfileService.ensureDefault(PROJECT_ID)).thenReturn(snowflakeProfile());
        ProjectIndexProfileResolver.ResolvedIngestionIndexProfile ingestion =
                projectIndexProfileResolver.resolveForIngestion(PROJECT_ID);
        Map<String, Object> enriched =
                compatibilityService.enrichIndexProfileForIngestion(snowflakeProfile().toSnapshotJsonb(), ingestion);

        assertDoesNotThrow(
                () -> compatibilityService.assertIndexingCompatibleForProjectIngestion(enriched, ingestion));
        assertThatThrownBy(() -> compatibilityService.assertIndexingCompatible(enriched))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void profileMismatchThrowsStructuredErrorWithModelIds() {
        ProjectIndexProfileResolver.ResolvedIngestionIndexProfile ingestion =
                new ProjectIndexProfileResolver.ResolvedIngestionIndexProfile(
                        PROJECT_ID,
                        SNOWFLAKE,
                        MXBAI_CATALOG,
                        LlmProvider.OPENAI_COMPATIBLE,
                        Map.of(
                                IndexProfileJsonSupport.EMBEDDING_MODEL_ID_KEY,
                                MXBAI_CATALOG,
                                IndexProfileJsonSupport.EMBEDDING_PROVIDER_KEY,
                                LlmProvider.OPENAI_COMPATIBLE.name()));
        Map<String, Object> profileJson =
                Map.of(
                        IndexProfileJsonSupport.EMBEDDING_MODEL_ID_KEY,
                        SNOWFLAKE,
                        IndexProfileJsonSupport.EMBEDDING_PROVIDER_KEY,
                        LlmProvider.OPENAI_COMPATIBLE.name());

        EmbeddingIndexCompatibilityException ex =
                assertThrows(
                        EmbeddingIndexCompatibilityException.class,
                        () ->
                                compatibilityService.assertIndexingCompatibleForProjectIngestion(
                                        profileJson, ingestion));

        assertEquals(IngestionEmbeddingReasonCodes.PROJECT_INDEX_PROFILE_MISMATCH, ex.code());
        assertEquals(SNOWFLAKE, ex.details().get("activeProfileEmbeddingModel"));
        assertEquals(MXBAI_CATALOG, ex.details().get("resolvedEmbeddingModel"));
    }

    @Test
    void resolverRejectsDeploymentDefaultSubstitutionForSnowflakeProfile() {
        when(embeddingModelCatalogResolver.resolveIfAvailable(eq(LlmProvider.OPENAI_COMPATIBLE), eq(SNOWFLAKE)))
                .thenReturn(Optional.empty());
        when(embeddingModelCatalogResolver.resolve(eq(LlmProvider.OPENAI_COMPATIBLE), eq(SNOWFLAKE)))
                .thenReturn(MXBAI_CATALOG);

        ProjectIndexProfile profile = snowflakeProfile();

        assertThatThrownBy(() -> projectIndexProfileResolver.resolveForIngestion(PROJECT_ID, profile))
                .isInstanceOf(EmbeddingIndexCompatibilityException.class)
                .satisfies(
                        err -> {
                            EmbeddingIndexCompatibilityException ex = (EmbeddingIndexCompatibilityException) err;
                            assertEquals(IngestionEmbeddingReasonCodes.PROJECT_INDEX_PROFILE_MISMATCH, ex.code());
                            assertEquals(SNOWFLAKE, ex.details().get("activeProfileEmbeddingModel"));
                            assertEquals(MXBAI_CATALOG, ex.details().get("resolvedEmbeddingModel"));
                        });
    }

    private static ProjectIndexProfile snowflakeProfile() {
        return new ProjectIndexProfile(
                PROJECT_ID,
                MaterializationStrategy.HYBRID,
                true,
                null,
                SNOWFLAKE,
                400,
                null,
                "hash",
                Instant.now(),
                Instant.now());
    }

    private static ResolvedLlmConfig openAiConfig() {
        return ResolvedLlmConfig.uniform(
                LlmProvider.OPENAI_COMPATIBLE,
                "http://litellm:4000",
                "gpt-oss:20b",
                MXBAI_CATALOG,
                "OPENAI_COMPATIBLE_API_KEY",
                null,
                0.1,
                60_000,
                null,
                Map.of());
    }
}
