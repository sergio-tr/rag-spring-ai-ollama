package com.uniovi.rag.application.service.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uniovi.rag.application.port.BinaryStoragePort;
import com.uniovi.rag.domain.ProjectDocumentStatus;
import com.uniovi.rag.domain.knowledge.CorpusScope;
import com.uniovi.rag.domain.knowledge.KnowledgeSnapshotScopeType;
import com.uniovi.rag.domain.knowledge.MaterializationStrategy;
import com.uniovi.rag.domain.knowledge.ProjectIndexProfile;
import com.uniovi.rag.infrastructure.persistence.KnowledgeDocumentRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeDocumentEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeIndexSnapshotEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.ProjectEntity;
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

@ExtendWith(MockitoExtension.class)
class KnowledgePipelineOrchestratorProfileOverrideTest {

    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private KnowledgeDocumentRepository knowledgeDocumentRepository;
    @Mock private BinaryStoragePort binaryStoragePort;
    @Mock private KnowledgeSnapshotService knowledgeSnapshotService;
    @Mock private KnowledgeIndexingService knowledgeIndexingService;
    @Mock private ProjectIndexProfileService projectIndexProfileService;
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

        KnowledgeIndexSnapshotEntity building = mock(KnowledgeIndexSnapshotEntity.class);
        when(building.getId()).thenReturn(snapshotId);
        when(knowledgeSnapshotService.createBuildingSnapshot(
                eq(project),
                eq(null),
                eq(KnowledgeSnapshotScopeType.PROJECT),
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
                        any(),
                        eq(runConfigSnapId),
                        eq("hash"),
                        profileJsonb.capture(),
                        profileHash.capture());
        assertThat(profileJsonb.getValue()).containsEntry("materializationStrategy", "HYBRID");
        assertThat(profileJsonb.getValue()).containsEntry("supportsMetadata", true);
        assertThat(profileHash.getValue()).isEqualTo(effective.profileHash());
    }
}

