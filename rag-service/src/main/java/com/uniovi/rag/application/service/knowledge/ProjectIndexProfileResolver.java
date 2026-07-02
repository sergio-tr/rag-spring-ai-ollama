package com.uniovi.rag.application.service.knowledge;

import com.uniovi.rag.application.service.llm.ProviderAwareEmbeddingService;
import com.uniovi.rag.application.service.llm.catalog.EmbeddingModelCatalogResolver;
import com.uniovi.rag.domain.knowledge.ProjectIndexProfile;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.domain.llm.ResolvedLlmConfig;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Resolves project document ingestion embedding from the persisted project index profile — never from global/user LLM
 * defaults when the profile names an explicit model.
 */
@Service
public class ProjectIndexProfileResolver {

    public record ResolvedIngestionIndexProfile(
            UUID projectId,
            String activeProfileEmbeddingModel,
            String resolvedEmbeddingModel,
            LlmProvider embeddingProvider,
            Map<String, Object> snapshotIndexProfileJsonb) {}

    private final ProjectIndexProfileService projectIndexProfileService;
    private final ProviderAwareEmbeddingService embeddingService;
    private final EmbeddingModelCatalogResolver embeddingModelCatalogResolver;

    public ProjectIndexProfileResolver(
            ProjectIndexProfileService projectIndexProfileService,
            ProviderAwareEmbeddingService embeddingService,
            EmbeddingModelCatalogResolver embeddingModelCatalogResolver) {
        this.projectIndexProfileService = projectIndexProfileService;
        this.embeddingService = embeddingService;
        this.embeddingModelCatalogResolver = embeddingModelCatalogResolver;
    }

    public ResolvedIngestionIndexProfile resolveForIngestion(UUID projectId) {
        Objects.requireNonNull(projectId, "projectId");
        ProjectIndexProfile profile = projectIndexProfileService.ensureDefault(projectId);
        return resolveForIngestion(projectId, profile);
    }

    public ResolvedIngestionIndexProfile resolveForIngestion(UUID projectId, ProjectIndexProfile profile) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(profile, "profile");
        String activeModel =
                profile.embeddingModelId() != null ? profile.embeddingModelId().trim() : "";
        if (activeModel.isBlank()) {
            throw new IllegalStateException("Project index profile embeddingModelId is required for ingestion");
        }

        ResolvedLlmConfig llmConfig = embeddingService.resolveEffectiveConfig();
        LlmProvider provider = llmConfig.embeddingProvider();
        String resolvedModel = resolveIngestionEmbeddingModel(activeModel, provider);
        String deploymentDefault = embeddingService.effectiveEmbeddingModelId(null);

        assertNoDeploymentDefaultSubstitution(activeModel, resolvedModel, deploymentDefault);

        Map<String, Object> snapshotProfile = new LinkedHashMap<>(profile.toSnapshotJsonb());
        snapshotProfile.put(IndexProfileJsonSupport.EMBEDDING_MODEL_ID_KEY, resolvedModel);
        snapshotProfile.put(IndexProfileJsonSupport.EMBEDDING_PROVIDER_KEY, provider.name());

        return new ResolvedIngestionIndexProfile(
                projectId, activeModel, resolvedModel, provider, new LinkedHashMap<>(snapshotProfile));
    }

    /**
     * Catalog resolution for the active profile model. Does not substitute the deployment/user default embedding model
     * when the profile names a different model (e.g. snowflake-arctic-embed2 vs mxbai).
     */
    String resolveIngestionEmbeddingModel(String activeProfileModel, LlmProvider provider) {
        String trimmed = activeProfileModel != null ? activeProfileModel.trim() : "";
        if (trimmed.isBlank()) {
            throw new IllegalArgumentException("activeProfileModel must not be blank");
        }
        LlmProvider effectiveProvider =
                provider != null ? provider : embeddingService.resolveEffectiveConfig().embeddingProvider();

        Optional<String> catalogMatch =
                embeddingModelCatalogResolver.resolveIfAvailable(effectiveProvider, trimmed);
        if (catalogMatch.isPresent()) {
            return catalogMatch.get();
        }

        if (IndexProfileJsonSupport.mixedbreadMxbaiAliasFamily(trimmed)) {
            String viaRuntime = embeddingService.effectiveEmbeddingModelId(trimmed);
            if (IndexProfileJsonSupport.mixedbreadMxbaiAliasFamily(viaRuntime)) {
                return viaRuntime;
            }
        }

        return embeddingModelCatalogResolver.resolve(effectiveProvider, trimmed);
    }

    private void assertNoDeploymentDefaultSubstitution(
            String activeModel, String resolvedModel, String deploymentDefault) {
        if (IndexProfileJsonSupport.embeddingKeysEquivalent(activeModel, resolvedModel)) {
            return;
        }
        if (!IndexProfileJsonSupport.embeddingKeysEquivalent(resolvedModel, deploymentDefault)) {
            return;
        }
        if (IndexProfileJsonSupport.embeddingKeysEquivalent(activeModel, deploymentDefault)) {
            return;
        }
        throw EmbeddingIndexCompatibilityException.projectIndexProfileMismatch(
                activeModel, resolvedModel, deploymentDefault);
    }
}
