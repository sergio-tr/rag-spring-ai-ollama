package com.uniovi.rag.application.service.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniovi.rag.domain.knowledge.MaterializationStrategy;
import com.uniovi.rag.domain.knowledge.ProjectIndexProfile;
import com.uniovi.rag.service.evaluation.preset.ExperimentalPresetCanonicalCatalog;
import com.uniovi.rag.service.evaluation.preset.LabPresetRunGroupKey;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LabIndexProfileOverrideFactoryTest {

    private static ProjectIndexProfile baseProfile(UUID projectId) {
        return new ProjectIndexProfile(
                projectId,
                MaterializationStrategy.CHUNK_LEVEL,
                false,
                "meta-v1",
                "mxbai-embed-large",
                400,
                20,
                "h",
                Instant.now(),
                Instant.now());
    }

    @Test
    void documentLevel_withoutMetadataRequirement_disablesMetadata() {
        UUID projectId = UUID.randomUUID();
        LabIndexProfileOverrideFactory f = new LabIndexProfileOverrideFactory();
        ProjectIndexProfile out =
                f.buildEffectiveProfile(
                        baseProfile(projectId),
                        new ExperimentalPresetCanonicalCatalog.IndexRequirements(
                                ExperimentalPresetCanonicalCatalog.RequiredMaterialization.DOCUMENT_LEVEL,
                                false),
                        LabPresetRunGroupKey.DOCUMENT_LEVEL);
        assertThat(out.materializationStrategy()).isEqualTo(MaterializationStrategy.DOCUMENT_LEVEL);
        assertThat(out.metadataEnabled()).isFalse();
    }

    @Test
    void chunkLevel_disablesMetadata() {
        UUID projectId = UUID.randomUUID();
        LabIndexProfileOverrideFactory f = new LabIndexProfileOverrideFactory();
        ProjectIndexProfile out =
                f.buildEffectiveProfile(
                        baseProfile(projectId),
                        new ExperimentalPresetCanonicalCatalog.IndexRequirements(
                                ExperimentalPresetCanonicalCatalog.RequiredMaterialization.CHUNK_LEVEL,
                                false),
                        LabPresetRunGroupKey.CHUNK_LEVEL);
        assertThat(out.materializationStrategy()).isEqualTo(MaterializationStrategy.CHUNK_LEVEL);
        assertThat(out.metadataEnabled()).isFalse();
    }

    @Test
    void chunkLevelMetadata_enablesMetadata() {
        UUID projectId = UUID.randomUUID();
        LabIndexProfileOverrideFactory f = new LabIndexProfileOverrideFactory();
        ProjectIndexProfile out =
                f.buildEffectiveProfile(
                        baseProfile(projectId),
                        new ExperimentalPresetCanonicalCatalog.IndexRequirements(
                                ExperimentalPresetCanonicalCatalog.RequiredMaterialization.CHUNK_LEVEL,
                                true),
                        LabPresetRunGroupKey.CHUNK_LEVEL_METADATA);
        assertThat(out.materializationStrategy()).isEqualTo(MaterializationStrategy.CHUNK_LEVEL);
        assertThat(out.metadataEnabled()).isTrue();
    }

    @Test
    void hybridMetadata_enablesMetadata() {
        UUID projectId = UUID.randomUUID();
        LabIndexProfileOverrideFactory f = new LabIndexProfileOverrideFactory();
        ProjectIndexProfile out =
                f.buildEffectiveProfile(
                        baseProfile(projectId),
                        new ExperimentalPresetCanonicalCatalog.IndexRequirements(
                                ExperimentalPresetCanonicalCatalog.RequiredMaterialization.HYBRID,
                                true),
                        LabPresetRunGroupKey.HYBRID_METADATA);
        assertThat(out.materializationStrategy()).isEqualTo(MaterializationStrategy.HYBRID);
        assertThat(out.metadataEnabled()).isTrue();
    }

    @Test
    void keepsEmbeddingAndChunkingFromBaseProfile() {
        UUID projectId = UUID.randomUUID();
        ProjectIndexProfile base = baseProfile(projectId);
        LabIndexProfileOverrideFactory f = new LabIndexProfileOverrideFactory();
        ProjectIndexProfile out =
                f.buildEffectiveProfile(
                        base,
                        new ExperimentalPresetCanonicalCatalog.IndexRequirements(
                                ExperimentalPresetCanonicalCatalog.RequiredMaterialization.HYBRID,
                                true),
                        LabPresetRunGroupKey.HYBRID_METADATA);
        assertThat(out.embeddingModelId()).isEqualTo(base.embeddingModelId());
        assertThat(out.chunkMaxChars()).isEqualTo(base.chunkMaxChars());
        assertThat(out.chunkOverlap()).isEqualTo(base.chunkOverlap());
        assertThat(out.metadataProfile()).isEqualTo(base.metadataProfile());
    }
}

