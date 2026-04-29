package com.uniovi.rag.application.service.knowledge;

import com.uniovi.rag.domain.knowledge.IndexSnapshotStatus;
import com.uniovi.rag.domain.knowledge.KnowledgeSnapshotScopeType;
import com.uniovi.rag.infrastructure.persistence.KnowledgeDocumentRepository;
import com.uniovi.rag.infrastructure.persistence.KnowledgeIndexSnapshotRepository;
import com.uniovi.rag.infrastructure.persistence.KnowledgeSnapshotDocumentRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeDocumentEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeIndexSnapshotEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.ProjectEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeSnapshotServiceTest {

    @Mock private KnowledgeIndexSnapshotRepository snapshotRepository;
    @Mock private KnowledgeSnapshotDocumentRepository snapshotDocumentRepository;
    @Mock private KnowledgeDocumentRepository knowledgeDocumentRepository;
    @Mock private JdbcTemplate jdbcTemplate;

    @InjectMocks private KnowledgeSnapshotService knowledgeSnapshotService;

    @Test
    void activateSnapshot_supersedesPreviousActive() {
        ProjectEntity project = mock(ProjectEntity.class);
        UUID pid = UUID.randomUUID();
        when(project.getId()).thenReturn(pid);

        KnowledgeIndexSnapshotEntity building = mock(KnowledgeIndexSnapshotEntity.class);
        UUID bid = UUID.randomUUID();
        when(building.getId()).thenReturn(bid);
        when(building.getScopeType()).thenReturn(KnowledgeSnapshotScopeType.PROJECT);
        when(building.getProject()).thenReturn(project);
        when(snapshotRepository.countByProject_IdAndScopeTypeAndConversationIsNullAndStatus(
                        eq(pid), eq(KnowledgeSnapshotScopeType.PROJECT), eq(IndexSnapshotStatus.ACTIVE)))
                .thenReturn(1L);

        KnowledgeIndexSnapshotEntity prior = mock(KnowledgeIndexSnapshotEntity.class);

        KnowledgeDocumentEntity doc = mock(KnowledgeDocumentEntity.class);
        when(doc.getId()).thenReturn(UUID.randomUUID());

        knowledgeSnapshotService.activateSnapshot(building, List.of(doc), Optional.of(prior));

        verify(prior).setStatus(IndexSnapshotStatus.SUPERSEDED);
        verify(building).setStatus(IndexSnapshotStatus.ACTIVE);
        verify(snapshotRepository, Mockito.atLeast(2)).save(any());
        verify(snapshotDocumentRepository).save(any());
    }

    @Test
    void createBuildingSnapshot_storesBuildingStatus() {
        ProjectEntity project = mock(ProjectEntity.class);
        when(snapshotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UUID cfgId = UUID.randomUUID();
        KnowledgeIndexSnapshotEntity out =
                knowledgeSnapshotService.createBuildingSnapshot(
                        project, null, KnowledgeSnapshotScopeType.PROJECT, "sighex", cfgId, "a".repeat(64));

        assertThat(out.getStatus()).isEqualTo(IndexSnapshotStatus.BUILDING);
        assertThat(out.getSignatureHash()).isEqualTo("sighex");
    }
}
