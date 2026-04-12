package com.uniovi.rag.application.service;

import com.uniovi.rag.infrastructure.persistence.ProjectRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeIndexSnapshotEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.ProjectEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeLegacyBackfillServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private KnowledgeIndexSnapshotService knowledgeIndexSnapshotService;

    @Mock
    private ProjectRepository projectRepository;

    @InjectMocks
    private KnowledgeLegacyBackfillService service;

    @Test
    void backfillProject_updatesVectorsWhenProjectExists() {
        UUID projectId = UUID.randomUUID();
        UUID snapshotId = UUID.randomUUID();
        ProjectEntity project = org.mockito.Mockito.mock(ProjectEntity.class);
        KnowledgeIndexSnapshotEntity snap = org.mockito.Mockito.mock(KnowledgeIndexSnapshotEntity.class);
        when(snap.getId()).thenReturn(snapshotId);
        when(snap.getSignatureHash()).thenReturn("sig");

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(knowledgeIndexSnapshotService.ensureLegacySnapshotForProject(project)).thenReturn(snap);
        when(jdbcTemplate.update(anyString(), anyString(), anyString())).thenReturn(4);

        assertEquals(4, service.backfillProject(projectId));

        verify(jdbcTemplate).update(anyString(), anyString(), eq(projectId.toString()));
    }

    @Test
    void backfillProject_unknownProject_throws() {
        UUID projectId = UUID.randomUUID();
        when(projectRepository.findById(projectId)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.backfillProject(projectId));
    }
}
