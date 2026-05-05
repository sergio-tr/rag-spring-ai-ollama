package com.uniovi.rag.application.service;

import com.uniovi.rag.domain.knowledge.IndexSnapshotStatus;
import com.uniovi.rag.infrastructure.persistence.KnowledgeIndexSnapshotRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeIndexSnapshotEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.ProjectEntity;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeIndexSnapshotServiceTest {

    @Mock
    private KnowledgeIndexSnapshotRepository knowledgeIndexSnapshotRepository;

    @InjectMocks
    private KnowledgeIndexSnapshotService service;

    @Test
    void legacySignatureForProject_isStable() {
        UUID pid = UUID.fromString("00000000-0000-0000-0000-0000000000ab");
        assertEquals(
                KnowledgeIndexSnapshotService.LEGACY_SIGNATURE_PREFIX + pid,
                KnowledgeIndexSnapshotService.legacySignatureForProject(pid));
    }

    @Test
    void ensureLegacySnapshot_returnsExistingWhenPresent() {
        UUID pid = UUID.randomUUID();
        ProjectEntity project = Mockito.mock(ProjectEntity.class);
        when(project.getId()).thenReturn(pid);
        String sig = KnowledgeIndexSnapshotService.legacySignatureForProject(pid);
        KnowledgeIndexSnapshotEntity existing = Mockito.mock(KnowledgeIndexSnapshotEntity.class);
        when(knowledgeIndexSnapshotRepository.findByProject_IdAndSignatureHashAndStatusOrderByUpdatedAtDesc(
                        eq(pid), eq(sig), eq(IndexSnapshotStatus.ACTIVE)))
                .thenReturn(List.of(existing));

        assertSame(existing, service.ensureLegacySnapshotForProject(project));
    }

    @Test
    void ensureLegacySnapshot_createsWhenMissing() {
        UUID pid = UUID.randomUUID();
        ProjectEntity project = Mockito.mock(ProjectEntity.class);
        when(project.getId()).thenReturn(pid);
        String sig = KnowledgeIndexSnapshotService.legacySignatureForProject(pid);
        when(knowledgeIndexSnapshotRepository.findByProject_IdAndSignatureHashAndStatusOrderByUpdatedAtDesc(
                        eq(pid), eq(sig), eq(IndexSnapshotStatus.ACTIVE)))
                .thenReturn(List.of());
        when(knowledgeIndexSnapshotRepository.save(any(KnowledgeIndexSnapshotEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        KnowledgeIndexSnapshotEntity created = service.ensureLegacySnapshotForProject(project);

        ArgumentCaptor<KnowledgeIndexSnapshotEntity> cap =
                ArgumentCaptor.forClass(KnowledgeIndexSnapshotEntity.class);
        verify(knowledgeIndexSnapshotRepository).save(cap.capture());
        assertEquals(sig, cap.getValue().getSignatureHash());
        assertEquals(created, cap.getValue());
    }
}
