package com.uniovi.rag.infrastructure.persistence.mapper;

import com.uniovi.rag.domain.knowledge.IndexSnapshotStatus;
import com.uniovi.rag.domain.knowledge.KnowledgeIndexSnapshot;
import com.uniovi.rag.domain.knowledge.KnowledgeSnapshotScopeType;
import com.uniovi.rag.infrastructure.persistence.jpa.ConversationEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeIndexSnapshotEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.ProjectEntity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KnowledgeIndexSnapshotMapperTest {

    @Test
    void toDomain_mapsProjectAndOptionalConversation() {
        UUID id = UUID.randomUUID();
        UUID pid = UUID.randomUUID();
        UUID cid = UUID.randomUUID();
        UUID rs = UUID.randomUUID();
        ProjectEntity project = mock(ProjectEntity.class);
        when(project.getId()).thenReturn(pid);
        ConversationEntity conv = mock(ConversationEntity.class);
        when(conv.getId()).thenReturn(cid);
        Instant c = Instant.parse("2026-01-02T00:00:00Z");
        Instant u = Instant.parse("2026-01-03T00:00:00Z");
        KnowledgeIndexSnapshotEntity e = mock(KnowledgeIndexSnapshotEntity.class);
        when(e.getId()).thenReturn(id);
        when(e.getSignatureHash()).thenReturn("sig");
        when(e.getScopeType()).thenReturn(KnowledgeSnapshotScopeType.CONVERSATION);
        when(e.getProject()).thenReturn(project);
        when(e.getConversation()).thenReturn(conv);
        when(e.getStatus()).thenReturn(IndexSnapshotStatus.ACTIVE);
        when(e.getResolvedConfigSnapshotId()).thenReturn(rs);
        when(e.getResolvedConfigHash()).thenReturn("rh");
        when(e.getCreatedAt()).thenReturn(c);
        when(e.getUpdatedAt()).thenReturn(u);

        KnowledgeIndexSnapshot d = KnowledgeIndexSnapshotMapper.toDomain(e);
        assertEquals(id, d.id());
        assertEquals("sig", d.signatureHash());
        assertEquals(KnowledgeSnapshotScopeType.CONVERSATION, d.scopeType());
        assertEquals(pid, d.projectId());
        assertEquals(cid, d.conversationId());
        assertEquals(IndexSnapshotStatus.ACTIVE, d.status());
        assertEquals(rs, d.resolvedConfigSnapshotId());
        assertEquals("rh", d.resolvedConfigHash());
        assertEquals(c, d.createdAt());
        assertEquals(u, d.updatedAt());
    }

    @Test
    void toDomain_nullConversationMapsNullConversationId() {
        UUID pid = UUID.randomUUID();
        ProjectEntity project = mock(ProjectEntity.class);
        when(project.getId()).thenReturn(pid);
        KnowledgeIndexSnapshotEntity e = mock(KnowledgeIndexSnapshotEntity.class);
        when(e.getId()).thenReturn(UUID.randomUUID());
        when(e.getSignatureHash()).thenReturn("s");
        when(e.getScopeType()).thenReturn(KnowledgeSnapshotScopeType.PROJECT);
        when(e.getProject()).thenReturn(project);
        when(e.getConversation()).thenReturn(null);
        when(e.getStatus()).thenReturn(IndexSnapshotStatus.BUILDING);
        when(e.getResolvedConfigSnapshotId()).thenReturn(null);
        when(e.getResolvedConfigHash()).thenReturn(null);
        when(e.getCreatedAt()).thenReturn(Instant.now());
        when(e.getUpdatedAt()).thenReturn(Instant.now());

        KnowledgeIndexSnapshot d = KnowledgeIndexSnapshotMapper.toDomain(e);
        assertNull(d.conversationId());
        assertEquals(pid, d.projectId());
    }
}
