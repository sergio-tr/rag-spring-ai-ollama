package com.uniovi.rag.application.service.runtime.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.uniovi.rag.application.service.evaluation.preset.ExperimentalPresetCanonicalCatalog;
import com.uniovi.rag.application.service.knowledge.KnowledgeSnapshotService;
import com.uniovi.rag.domain.knowledge.IndexSnapshotStatus;
import com.uniovi.rag.domain.knowledge.MaterializationStrategy;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeIndexSnapshotEntity;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MaterializationAwareSnapshotResolverTest {

    private static final UUID PROJECT_ID = UUID.fromString("27027d52-6862-443f-bad3-b33c1de2f31a");

    @Mock private KnowledgeSnapshotService knowledgeSnapshotService;

    private MaterializationAwareSnapshotResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new MaterializationAwareSnapshotResolver(knowledgeSnapshotService);
    }

    @Test
    void resolveProjectSnapshot_prefersExactChunkOverHybridForChunkRequirement() {
        KnowledgeIndexSnapshotEntity hybrid = activeSnapshot("HYBRID", true, UUID.randomUUID());
        KnowledgeIndexSnapshotEntity chunk = activeSnapshot("CHUNK_LEVEL", true, UUID.randomUUID());
        when(knowledgeSnapshotService.findProjectSnapshots(PROJECT_ID)).thenReturn(List.of(hybrid, chunk));

        var req =
                new ExperimentalPresetCanonicalCatalog.IndexRequirements(
                        ExperimentalPresetCanonicalCatalog.RequiredMaterialization.CHUNK_LEVEL, true);
        Optional<MaterializationAwareSnapshotResolver.ResolvedProjectSnapshot> resolved =
                resolver.resolveProjectSnapshot(PROJECT_ID, req);

        assertThat(resolved).isPresent();
        assertThat(resolved.get().snapshotId()).isEqualTo(chunk.getId());
        assertThat(resolved.get().capabilities().materializationStrategy()).isEqualTo("CHUNK_LEVEL");
    }

    @Test
    void resolveProjectSnapshot_prefersCompatibleHybridOverPrimaryChunkActive() {
        KnowledgeIndexSnapshotEntity chunk = activeSnapshot("CHUNK_LEVEL", false, UUID.randomUUID());
        KnowledgeIndexSnapshotEntity hybrid = activeSnapshot("HYBRID", true, UUID.randomUUID());
        when(knowledgeSnapshotService.findProjectSnapshots(PROJECT_ID)).thenReturn(List.of(hybrid, chunk));
        when(knowledgeSnapshotService.findActiveProjectSnapshot(PROJECT_ID)).thenReturn(Optional.of(chunk));

        var req =
                new ExperimentalPresetCanonicalCatalog.IndexRequirements(
                        ExperimentalPresetCanonicalCatalog.RequiredMaterialization.HYBRID, true);
        Optional<MaterializationAwareSnapshotResolver.ResolvedProjectSnapshot> resolved =
                resolver.resolveProjectSnapshot(PROJECT_ID, req);

        assertThat(resolved).isPresent();
        assertThat(resolved.get().snapshotId()).isEqualTo(hybrid.getId());
        assertThat(resolved.get().compatibleWithRequirements()).isTrue();
        assertThat(resolved.get().capabilities().materializationStrategy()).isEqualTo("HYBRID");
    }

    @Test
    void resolveProjectSnapshot_marksPrimaryActiveIncompatibleWhenRequirementsUnmet() {
        KnowledgeIndexSnapshotEntity chunk = activeSnapshot("CHUNK_LEVEL", true, UUID.randomUUID());
        when(knowledgeSnapshotService.findProjectSnapshots(PROJECT_ID)).thenReturn(List.of(chunk));
        when(knowledgeSnapshotService.findActiveProjectSnapshot(PROJECT_ID)).thenReturn(Optional.of(chunk));

        var req =
                new ExperimentalPresetCanonicalCatalog.IndexRequirements(
                        ExperimentalPresetCanonicalCatalog.RequiredMaterialization.HYBRID, true);
        Optional<MaterializationAwareSnapshotResolver.ResolvedProjectSnapshot> resolved =
                resolver.resolveProjectSnapshot(PROJECT_ID, req);

        assertThat(resolved).isPresent();
        assertThat(resolved.get().compatibleWithRequirements()).isFalse();
    }

    @Test
    void requirementsFromPresetAndRag_usesCatalogWhenPresetKnown() {
        UUID p2 = UUID.fromString("cafe0001-0001-4001-8001-000000000012");
        var req =
                MaterializationAwareSnapshotResolver.requirementsFromPresetAndRag(Optional.of(p2), null);
        assertThat(req.requiredMaterialization())
                .isEqualTo(ExperimentalPresetCanonicalCatalog.RequiredMaterialization.DOCUMENT_LEVEL);
    }

    @Test
    void resolveProjectSnapshot_prefersExactDocumentOverHybridForDocumentRequirement() {
        KnowledgeIndexSnapshotEntity hybrid = activeSnapshot("HYBRID", true, UUID.randomUUID());
        KnowledgeIndexSnapshotEntity document = activeSnapshot("DOCUMENT_LEVEL", true, UUID.randomUUID());
        when(knowledgeSnapshotService.findProjectSnapshots(PROJECT_ID)).thenReturn(List.of(hybrid, document));

        var req =
                new ExperimentalPresetCanonicalCatalog.IndexRequirements(
                        ExperimentalPresetCanonicalCatalog.RequiredMaterialization.DOCUMENT_LEVEL, true);
        Optional<MaterializationAwareSnapshotResolver.ResolvedProjectSnapshot> resolved =
                resolver.resolveProjectSnapshot(PROJECT_ID, req);

        assertThat(resolved).isPresent();
        assertThat(resolved.get().snapshotId()).isEqualTo(document.getId());
        assertThat(resolved.get().capabilities().materializationStrategy()).isEqualTo("DOCUMENT_LEVEL");
    }

    @Test
    void resolveProjectSnapshot_fallsBackToHybridWhenExactMissing() {
        KnowledgeIndexSnapshotEntity hybrid = activeSnapshot("HYBRID", true, UUID.randomUUID());
        when(knowledgeSnapshotService.findProjectSnapshots(PROJECT_ID)).thenReturn(List.of(hybrid));

        var req =
                new ExperimentalPresetCanonicalCatalog.IndexRequirements(
                        ExperimentalPresetCanonicalCatalog.RequiredMaterialization.CHUNK_LEVEL, true);
        Optional<MaterializationAwareSnapshotResolver.ResolvedProjectSnapshot> resolved =
                resolver.resolveProjectSnapshot(PROJECT_ID, req);

        assertThat(resolved).isPresent();
        assertThat(resolved.get().snapshotId()).isEqualTo(hybrid.getId());
        assertThat(resolved.get().compatibleWithRequirements()).isTrue();
    }

    @Test
    void requirementsFromPresetAndRag_noneForP0BaselineWithoutRuntimeFallback() {
        UUID p0 = UUID.fromString("cafe0001-0001-4001-8001-000000000010");
        var req =
                MaterializationAwareSnapshotResolver.requirementsFromPresetAndRag(Optional.of(p0), null);
        assertThat(req.requiredMaterialization())
                .isEqualTo(ExperimentalPresetCanonicalCatalog.RequiredMaterialization.NONE);
    }

    @Test
    void requirementsFromPresetAndRag_noneForP1FullCorpusWithoutRuntimeFallback() {
        UUID p1 = UUID.fromString("cafe0001-0001-4001-8001-000000000011");
        var req =
                MaterializationAwareSnapshotResolver.requirementsFromPresetAndRag(Optional.of(p1), null);
        assertThat(req.requiredMaterialization())
                .isEqualTo(ExperimentalPresetCanonicalCatalog.RequiredMaterialization.NONE);
    }

    @Test
    void requirementsFromPresetAndRag_hybridForP8() {
        UUID p8 = UUID.fromString("cafe0001-0001-4001-8001-000000000018");
        var req =
                MaterializationAwareSnapshotResolver.requirementsFromPresetAndRag(Optional.of(p8), null);
        assertThat(req.requiredMaterialization())
                .isEqualTo(ExperimentalPresetCanonicalCatalog.RequiredMaterialization.HYBRID);
    }

    private static KnowledgeIndexSnapshotEntity activeSnapshot(
            String materialization, boolean metadata, UUID id) {
        KnowledgeIndexSnapshotEntity e = mock(KnowledgeIndexSnapshotEntity.class);
        when(e.getId()).thenReturn(id);
        when(e.getStatus()).thenReturn(IndexSnapshotStatus.ACTIVE);
        when(e.getIndexProfileJsonb())
                .thenReturn(
                        Map.of(
                                "materializationStrategy",
                                materialization,
                                "supportsMetadata",
                                metadata,
                                "embeddingModelId",
                                "mxbai-embed-large"));
        when(e.getIndexProfileHash()).thenReturn(materialization + "-" + metadata);
        return e;
    }
}
