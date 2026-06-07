package com.uniovi.rag.application.service.knowledge;

import com.uniovi.rag.domain.knowledge.MaterializationStrategy;
import com.uniovi.rag.domain.knowledge.ProjectIndexProfile;
import com.uniovi.rag.application.service.evaluation.preset.ExperimentalPresetCanonicalCatalog;
import com.uniovi.rag.application.service.evaluation.preset.LabPresetRunGroupKey;
import java.time.Instant;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * Builds an effective {@link ProjectIndexProfile} for Lab auto-reindex without mutating the persisted
 * {@code project_index_profile} row.
 *
 * <p>Only overrides materialization strategy and metadata toggle; keeps embedding + chunking from the current profile.
 */
@Service
public class LabIndexProfileOverrideFactory {

    public ProjectIndexProfile buildEffectiveProfile(
            ProjectIndexProfile current,
            ExperimentalPresetCanonicalCatalog.IndexRequirements requirements,
            LabPresetRunGroupKey groupKey) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(groupKey, "groupKey");
        ExperimentalPresetCanonicalCatalog.IndexRequirements req =
                requirements != null ? requirements : ExperimentalPresetCanonicalCatalog.IndexRequirements.none();

        MaterializationStrategy strat = switch (groupKey) {
            case DOCUMENT_LEVEL -> MaterializationStrategy.DOCUMENT_LEVEL;
            case CHUNK_LEVEL, CHUNK_LEVEL_METADATA -> MaterializationStrategy.CHUNK_LEVEL;
            case HYBRID_METADATA -> MaterializationStrategy.HYBRID;
            case NO_INDEX, MULTI_TURN_UNSUPPORTED_IN_SINGLE_TURN -> current.materializationStrategy();
        };

        boolean metadataEnabled = switch (groupKey) {
            case CHUNK_LEVEL_METADATA, HYBRID_METADATA -> true;
            case CHUNK_LEVEL -> false;
            case DOCUMENT_LEVEL -> req.requiresMetadataSupport();
            case NO_INDEX, MULTI_TURN_UNSUPPORTED_IN_SINGLE_TURN -> current.metadataEnabled();
        };

        String embeddingModelId = current.embeddingModelId();
        int chunkMaxChars = current.chunkMaxChars();
        Integer chunkOverlap = current.chunkOverlap();
        String metadataProfile = current.metadataProfile();

        String hash =
                ProjectIndexProfile.computeProfileHash(
                        strat,
                        metadataEnabled,
                        metadataProfile,
                        embeddingModelId,
                        chunkMaxChars,
                        chunkOverlap);

        Instant now = Instant.now();
        return new ProjectIndexProfile(
                current.projectId(),
                strat,
                metadataEnabled,
                metadataProfile,
                embeddingModelId,
                chunkMaxChars,
                chunkOverlap,
                hash,
                now,
                now);
    }

    /** Copy profile with a different embedding model id (Lab embedding campaigns). */
    public ProjectIndexProfile withEmbeddingModelId(ProjectIndexProfile current, String embeddingModelId) {
        Objects.requireNonNull(current, "current");
        String model = embeddingModelId != null && !embeddingModelId.isBlank() ? embeddingModelId.trim() : current.embeddingModelId();
        String hash =
                ProjectIndexProfile.computeProfileHash(
                        current.materializationStrategy(),
                        current.metadataEnabled(),
                        current.metadataProfile(),
                        model,
                        current.chunkMaxChars(),
                        current.chunkOverlap());
        Instant now = Instant.now();
        return new ProjectIndexProfile(
                current.projectId(),
                current.materializationStrategy(),
                current.metadataEnabled(),
                current.metadataProfile(),
                model,
                current.chunkMaxChars(),
                current.chunkOverlap(),
                hash,
                now,
                now);
    }
}

