package com.uniovi.rag.application.service.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uniovi.rag.application.port.BinaryStoragePort;
import com.uniovi.rag.domain.config.indexing.ReindexImpact;
import com.uniovi.rag.domain.config.indexing.ReindexImpactLevel;
import com.uniovi.rag.domain.ProjectDocumentStatus;
import com.uniovi.rag.domain.knowledge.CorpusScope;
import com.uniovi.rag.domain.knowledge.KnowledgeSnapshotOwnerType;
import com.uniovi.rag.domain.knowledge.KnowledgeSnapshotScopeType;
import com.uniovi.rag.domain.knowledge.KnowledgeBuildProjection;
import com.uniovi.rag.domain.knowledge.MaterializationStrategy;
import com.uniovi.rag.domain.knowledge.ProjectIndexProfile;
import com.uniovi.rag.infrastructure.persistence.KnowledgeDocumentRepository;
import com.uniovi.rag.infrastructure.persistence.KnowledgeIndexSnapshotRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeDocumentEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeIndexSnapshotEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.ProjectEntity;
import com.uniovi.rag.configuration.RagIndexingEmbeddingProperties;
import com.uniovi.rag.infrastructure.vector.EmbeddingSpaceGuard;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import org.mockito.ArgumentMatchers;

@ExtendWith(MockitoExtension.class)
class KnowledgePipelineOrchestratorProfileOverrideTest {

    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private KnowledgeDocumentRepository knowledgeDocumentRepository;
    @Mock private BinaryStoragePort binaryStoragePort;
    @Mock private KnowledgeSnapshotService knowledgeSnapshotService;
    @Mock private KnowledgeIndexingService knowledgeIndexingService;
    @Mock private ProjectIndexProfileService projectIndexProfileService;
    @Mock private EmbeddingSpaceGuard embeddingSpaceGuard;
    @Mock private KnowledgeIndexSnapshotRepository knowledgeIndexSnapshotRepository;
    @Mock private PlatformTransactionManager transactionManager;

    private KnowledgePipelineOrchestrator orchestrator() {
        return new KnowledgePipelineOrchestrator(
                jdbcTemplate,
                knowledgeDocumentRepository,
                binaryStoragePort,
                knowledgeSnapshotService,
                knowledgeIndexingService,
                projectIndexProfileService,
                new LabIndexProfileOverrideFactory(),
                embeddingSpaceGuard,
                new IndexingEmbeddingGuard(new RagIndexingEmbeddingProperties(2048, 400, true, 0.85)),
                knowledgeIndexSnapshotRepository,
                transactionManager,
                null);
    }

    @Test
    void rebuildScopeWithProfileOverride_createsSnapshotWithEffectiveIndexProfileJsonb() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID runConfigSnapId = UUID.randomUUID();
        UUID snapshotId = UUID.randomUUID();

        KnowledgeDocumentEntity doc = mock(KnowledgeDocumentEntity.class);
        when(doc.getId()).thenReturn(UUID.randomUUID());
        when(doc.getStatus()).thenReturn(ProjectDocumentStatus.READY);
        when(doc.getStorageUri()).thenReturn("p/d/source.bin");
        when(doc.getContentChecksum()).thenReturn("c");
        when(doc.getFileName()).thenReturn("a.txt");
        when(doc.getMimeType()).thenReturn("text/plain");
        ProjectEntity project = mock(ProjectEntity.class);
        when(doc.getProject()).thenReturn(project);

        when(knowledgeDocumentRepository.findByProject_IdAndCorpusScopeOrderByIdAsc(projectId, CorpusScope.PROJECT_SHARED))
                .thenReturn(List.of(doc));
        when(knowledgeSnapshotService.findActiveProjectSnapshot(projectId)).thenReturn(Optional.empty());

        when(embeddingSpaceGuard.assertFitsPhysicalVectorColumnReturning(any())).thenReturn(1024);
        when(knowledgeIndexSnapshotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        KnowledgeIndexSnapshotEntity building = mock(KnowledgeIndexSnapshotEntity.class);
        when(building.getId()).thenReturn(snapshotId);
        when(knowledgeSnapshotService.createBuildingSnapshot(
                eq(project),
                eq(null),
                eq(KnowledgeSnapshotScopeType.PROJECT),
                isNull(),
                isNull(),
                any(),
                eq(runConfigSnapId),
                eq("hash"),
                any(),
                any()))
                .thenReturn(building);

        ProjectIndexProfile effective =
                new ProjectIndexProfile(
                        projectId,
                        MaterializationStrategy.HYBRID,
                        true,
                        "meta-v1",
                        "mxbai-embed-large",
                        400,
                        10,
                        ProjectIndexProfile.computeProfileHash(
                                MaterializationStrategy.HYBRID, true, "meta-v1", "mxbai-embed-large", 400, 10),
                        Instant.now(),
                        Instant.now());

        UUID out =
                orchestrator()
                        .rebuildScopeWithProfileOverride(
                                projectId,
                                CorpusScope.PROJECT_SHARED,
                                null,
                                runConfigSnapId,
                                "hash",
                                effective);

        assertThat(out).isEqualTo(snapshotId);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> profileJsonb = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<String> profileHash = ArgumentCaptor.forClass(String.class);
        verify(knowledgeSnapshotService)
                .createBuildingSnapshot(
                        eq(project),
                        eq(null),
                        eq(KnowledgeSnapshotScopeType.PROJECT),
                        isNull(),
                        isNull(),
                        any(),
                        eq(runConfigSnapId),
                        eq("hash"),
                        profileJsonb.capture(),
                        profileHash.capture());
        assertThat(profileJsonb.getValue()).containsEntry("materializationStrategy", "HYBRID");
        assertThat(profileJsonb.getValue()).containsEntry("supportsMetadata", true);
        assertThat(profileHash.getValue()).isEqualTo(effective.profileHash());
    }

    @Test
    void rebuildScope_usesProjectionEmbeddingModelForSnapshotProfileAndDimensionProbe() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID runConfigSnapId = UUID.randomUUID();
        UUID snapshotId = UUID.randomUUID();

        KnowledgeDocumentEntity doc = mock(KnowledgeDocumentEntity.class);
        when(doc.getId()).thenReturn(UUID.randomUUID());
        when(doc.getStatus()).thenReturn(ProjectDocumentStatus.READY);
        when(doc.getStorageUri()).thenReturn("p/d/source.bin");
        when(doc.getContentChecksum()).thenReturn("c");
        when(doc.getFileName()).thenReturn("a.txt");
        when(doc.getMimeType()).thenReturn("text/plain");
        ProjectEntity project = mock(ProjectEntity.class);
        when(doc.getProject()).thenReturn(project);

        when(knowledgeDocumentRepository.findByProject_IdAndCorpusScopeOrderByIdAsc(projectId, CorpusScope.PROJECT_SHARED))
                .thenReturn(List.of(doc));
        when(projectIndexProfileService.ensureDefault(projectId))
                .thenReturn(
                        new ProjectIndexProfile(
                                projectId,
                                MaterializationStrategy.CHUNK_LEVEL,
                                false,
                                "meta-v1",
                                "mxbai-embed-large",
                                400,
                                10,
                                ProjectIndexProfile.computeProfileHash(
                                        MaterializationStrategy.CHUNK_LEVEL,
                                        false,
                                        "meta-v1",
                                        "mxbai-embed-large",
                                        400,
                                        10),
                                Instant.now(),
                                Instant.now()));
        when(knowledgeSnapshotService.findActiveProjectSnapshot(projectId)).thenReturn(Optional.empty());
        when(embeddingSpaceGuard.assertFitsPhysicalVectorColumnReturning("qwen3-embedding:latest")).thenReturn(1024);
        when(knowledgeIndexSnapshotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        KnowledgeIndexSnapshotEntity building = mock(KnowledgeIndexSnapshotEntity.class);
        when(building.getId()).thenReturn(snapshotId);
        when(knowledgeSnapshotService.createBuildingSnapshot(
                eq(project),
                eq(null),
                eq(KnowledgeSnapshotScopeType.PROJECT),
                any(),
                eq(runConfigSnapId),
                eq("projection-hash"),
                any(),
                any()))
                .thenReturn(building);

        KnowledgeBuildProjection projection =
                new KnowledgeBuildProjection(
                        1,
                        MaterializationStrategy.HYBRID,
                        800,
                        20,
                        "qwen3-embedding:latest",
                        true,
                        new ReindexImpact(ReindexImpactLevel.HARD_REINDEX, List.of("embedding change")),
                        runConfigSnapId,
                        "projection-hash");

        UUID out =
                orchestrator()
                        .rebuildScope(projectId, CorpusScope.PROJECT_SHARED, null, projection, runConfigSnapId);

        assertThat(out).isEqualTo(snapshotId);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> profileJsonb = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<String> profileHash = ArgumentCaptor.forClass(String.class);
        verify(knowledgeSnapshotService)
                .createBuildingSnapshot(
                        eq(project),
                        eq(null),
                        eq(KnowledgeSnapshotScopeType.PROJECT),
                        any(),
                        eq(runConfigSnapId),
                        eq("projection-hash"),
                        profileJsonb.capture(),
                        profileHash.capture());
        assertThat(profileJsonb.getValue())
                .containsEntry("embeddingModelId", "qwen3-embedding:latest")
                .containsEntry("materializationStrategy", "HYBRID")
                .containsEntry("supportsMetadata", true)
                .containsEntry("chunkMaxChars", 800)
                .containsEntry("chunkOverlap", 20);
        assertThat(profileHash.getValue())
                .isEqualTo(
                        ProjectIndexProfile.computeProfileHash(
                                MaterializationStrategy.HYBRID, true, "meta-v1", "qwen3-embedding:latest", 800, 20));
        verify(embeddingSpaceGuard).assertFitsPhysicalVectorColumnReturning("qwen3-embedding:latest");
    }

    @Test
    void rebuildScopeWithProfileOverride_evaluationCorpus_skipsGlobalDocumentVectorDelete() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID corpusId = UUID.randomUUID();
        UUID runConfigSnapId = UUID.randomUUID();
        UUID snapshotId = UUID.randomUUID();

        KnowledgeDocumentEntity doc = mock(KnowledgeDocumentEntity.class);
        when(doc.getId()).thenReturn(UUID.randomUUID());
        when(doc.getStatus()).thenReturn(ProjectDocumentStatus.READY);
        when(doc.getStorageUri()).thenReturn("p/d/source.bin");
        when(doc.getContentChecksum()).thenReturn("c");
        when(doc.getFileName()).thenReturn("a.txt");
        when(doc.getMimeType()).thenReturn("text/plain");
        ProjectEntity project = mock(ProjectEntity.class);
        when(doc.getProject()).thenReturn(project);

        when(knowledgeDocumentRepository.findByProject_IdAndCorpusScopeOrderByIdAsc(projectId, CorpusScope.PROJECT_SHARED))
                .thenReturn(List.of(doc));
        when(knowledgeSnapshotService.findCompatibleCorpusSnapshot(eq(corpusId), any())).thenReturn(Optional.empty());
        when(embeddingSpaceGuard.assertFitsPhysicalVectorColumnReturning(any())).thenReturn(1024);
        when(knowledgeIndexSnapshotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(knowledgeIndexingService.computeChunkCountForDoc(any())).thenReturn(1);

        KnowledgeIndexSnapshotEntity building = mock(KnowledgeIndexSnapshotEntity.class);
        when(building.getId()).thenReturn(snapshotId);
        when(knowledgeSnapshotService.createBuildingSnapshot(
                        eq(project),
                        eq(null),
                        eq(KnowledgeSnapshotScopeType.PROJECT),
                        eq(KnowledgeSnapshotOwnerType.EVALUATION_CORPUS),
                        eq(corpusId),
                        any(),
                        eq(runConfigSnapId),
                        any(),
                        any(),
                        any()))
                .thenReturn(building);

        ProjectIndexProfile effective =
                new ProjectIndexProfile(
                        projectId,
                        MaterializationStrategy.CHUNK_LEVEL,
                        false,
                        "meta-v1",
                        "mxbai-embed-large",
                        400,
                        10,
                        ProjectIndexProfile.computeProfileHash(
                                MaterializationStrategy.CHUNK_LEVEL, false, "meta-v1", "mxbai-embed-large", 400, 10),
                        Instant.now(),
                        Instant.now());

        UUID out =
                orchestrator()
                        .rebuildScopeWithProfileOverride(
                                projectId,
                                CorpusScope.PROJECT_SHARED,
                                null,
                                KnowledgeSnapshotOwnerType.EVALUATION_CORPUS,
                                corpusId,
                                runConfigSnapId,
                                "hash",
                                effective);

        assertThat(out).isEqualTo(snapshotId);
        verify(jdbcTemplate, never())
                .update(
                        ArgumentMatchers.contains("metadata->>'projectDocumentId'"),
                        any(),
                        any());
    }
}

