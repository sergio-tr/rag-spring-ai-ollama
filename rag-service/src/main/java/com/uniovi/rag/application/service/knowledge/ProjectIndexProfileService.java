package com.uniovi.rag.application.service.knowledge;

import com.uniovi.rag.application.service.config.llm.ResolvedLlmConfigResolver;
import com.uniovi.rag.domain.knowledge.MaterializationStrategy;
import com.uniovi.rag.domain.knowledge.ProjectIndexProfile;
import com.uniovi.rag.application.service.llm.catalog.EmbeddingModelCatalogResolver;
import com.uniovi.rag.infrastructure.llm.LlmProperties;
import com.uniovi.rag.infrastructure.persistence.ProjectIndexProfileRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.ProjectIndexProfileEntity;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ProjectIndexProfileService {

    private final ProjectIndexProfileRepository repository;
    private final String defaultEmbeddingModelId;
    private final int defaultChunkMaxChars;
    private final MaterializationStrategy defaultMaterializationStrategy;
    private final EmbeddingModelCatalogResolver embeddingModelCatalogResolver;
    private final ResolvedLlmConfigResolver llmConfigResolver;

    public ProjectIndexProfileService(
            ProjectIndexProfileRepository repository,
            LlmProperties llmProperties,
            EmbeddingModelCatalogResolver embeddingModelCatalogResolver,
            ResolvedLlmConfigResolver llmConfigResolver,
            @Value("${rag.chunk.max-chars:400}") int defaultChunkMaxChars,
            @Value("${rag.knowledge.materialization-strategy:CHUNK_LEVEL}") String defaultMaterializationStrategyRaw) {
        this.repository = repository;
        this.defaultEmbeddingModelId = llmProperties.effectiveDefaultEmbeddingModel();
        this.defaultChunkMaxChars = defaultChunkMaxChars > 0 ? defaultChunkMaxChars : 400;
        this.defaultMaterializationStrategy = parseStrategy(defaultMaterializationStrategyRaw);
        this.embeddingModelCatalogResolver = embeddingModelCatalogResolver;
        this.llmConfigResolver = llmConfigResolver;
    }

    public ProjectIndexProfile ensureDefault(UUID projectId) {
        return find(projectId).orElseGet(() -> createDefault(projectId));
    }

    public Optional<ProjectIndexProfile> find(UUID projectId) {
        return repository.findById(projectId).map(this::toResolvedDomain);
    }

    public ProjectIndexProfile upsert(
            UUID userId,
            UUID projectId,
            MaterializationStrategy materializationStrategy,
            boolean metadataEnabled,
            String metadataProfile,
            String embeddingModelId,
            int chunkMaxChars,
            Integer chunkOverlap) {
        ProjectIndexProfileEntity e = repository.findById(projectId).orElseGet(ProjectIndexProfileEntity::new);
        Instant now = Instant.now();
        if (e.getProjectId() == null) {
            e.setProjectId(projectId);
            e.setCreatedAt(now);
        }
        e.setUpdatedAt(now);
        e.setMaterializationStrategy((materializationStrategy != null ? materializationStrategy : defaultMaterializationStrategy).name());
        e.setMetadataEnabled(metadataEnabled);
        e.setMetadataProfile(metadataProfile);
        e.setEmbeddingModelId(resolveEmbeddingModelId(userId, embeddingModelId));
        e.setChunkMaxChars(chunkMaxChars > 0 ? chunkMaxChars : defaultChunkMaxChars);
        e.setChunkOverlap(chunkOverlap);
        e.setProfileHash(
                ProjectIndexProfile.computeProfileHash(
                        materializationStrategy != null ? materializationStrategy : defaultMaterializationStrategy,
                        metadataEnabled,
                        metadataProfile,
                        e.getEmbeddingModelId(),
                        e.getChunkMaxChars(),
                        chunkOverlap));
        return toResolvedDomain(repository.save(e));
    }

    private ProjectIndexProfile toResolvedDomain(ProjectIndexProfileEntity e) {
        ProjectIndexProfile base = toDomain(e);
        String resolvedEmbedding = resolveEmbeddingModelId(null, base.embeddingModelId());
        if (resolvedEmbedding.equals(base.embeddingModelId())) {
            return base;
        }
        return new ProjectIndexProfile(
                base.projectId(),
                base.materializationStrategy(),
                base.metadataEnabled(),
                base.metadataProfile(),
                resolvedEmbedding,
                base.chunkMaxChars(),
                base.chunkOverlap(),
                base.profileHash(),
                base.createdAt(),
                base.updatedAt());
    }

    private String resolveEmbeddingModelId(UUID userId, String embeddingModelId) {
        if (embeddingModelId != null && !embeddingModelId.isBlank()) {
            return embeddingModelCatalogResolver.resolveForEffectiveProvider(embeddingModelId.trim());
        }
        if (userId != null) {
            String userModel = blankToNull(llmConfigResolver.resolve(userId, null, null).embeddingModel());
            if (userModel != null) {
                return embeddingModelCatalogResolver.resolveForEffectiveProvider(userModel);
            }
        }
        return embeddingModelCatalogResolver.resolveForEffectiveProvider(defaultEmbeddingModelId);
    }

    private static String blankToNull(String value) {
        return value != null && !value.isBlank() ? value.trim() : null;
    }

    private ProjectIndexProfile createDefault(UUID projectId) {
        return upsert(
                null,
                projectId,
                defaultMaterializationStrategy,
                false,
                null,
                defaultEmbeddingModelId,
                defaultChunkMaxChars,
                null);
    }

    private static ProjectIndexProfile toDomain(ProjectIndexProfileEntity e) {
        MaterializationStrategy strat = parseStrategy(e.getMaterializationStrategy());
        String embeddingModelId = e.getEmbeddingModelId();
        return new ProjectIndexProfile(
                e.getProjectId(),
                strat,
                e.isMetadataEnabled(),
                e.getMetadataProfile(),
                embeddingModelId,
                e.getChunkMaxChars(),
                e.getChunkOverlap(),
                e.getProfileHash(),
                e.getCreatedAt(),
                e.getUpdatedAt());
    }

    private static MaterializationStrategy parseStrategy(String raw) {
        if (raw == null || raw.isBlank()) return MaterializationStrategy.CHUNK_LEVEL;
        try {
            return MaterializationStrategy.valueOf(raw.trim());
        } catch (IllegalArgumentException e) {
            return MaterializationStrategy.CHUNK_LEVEL;
        }
    }
}

