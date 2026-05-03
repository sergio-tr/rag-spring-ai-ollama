package com.uniovi.rag.infrastructure.persistence.mapper;

import com.uniovi.rag.domain.knowledge.ReindexEvent;
import com.uniovi.rag.domain.knowledge.ReindexEventStatus;
import com.uniovi.rag.infrastructure.persistence.jpa.ConversationEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeDocumentEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.ProjectEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.ReindexEventEntity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReindexEventMapperTest {

    @Test
    void toDomain_allReferencesPresent() {
        UUID id = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        UUID projId = UUID.randomUUID();
        UUID convId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        KnowledgeDocumentEntity doc = mock(KnowledgeDocumentEntity.class);
        when(doc.getId()).thenReturn(docId);
        ProjectEntity proj = mock(ProjectEntity.class);
        when(proj.getId()).thenReturn(projId);
        ConversationEntity conv = mock(ConversationEntity.class);
        when(conv.getId()).thenReturn(convId);
        Instant c = Instant.parse("2026-02-01T00:00:00Z");
        Instant u = Instant.parse("2026-02-02T00:00:00Z");
        ReindexEventEntity e = mock(ReindexEventEntity.class);
        when(e.getId()).thenReturn(id);
        when(e.getDocument()).thenReturn(doc);
        when(e.getProject()).thenReturn(proj);
        when(e.getConversation()).thenReturn(conv);
        when(e.getReason()).thenReturn("R");
        when(e.getTargetSignatureHash()).thenReturn("t");
        when(e.getStatus()).thenReturn(ReindexEventStatus.PENDING);
        when(e.getAsyncTaskId()).thenReturn(taskId);
        when(e.getCreatedAt()).thenReturn(c);
        when(e.getUpdatedAt()).thenReturn(u);

        ReindexEvent d = ReindexEventMapper.toDomain(e);
        assertEquals(id, d.id());
        assertEquals(docId, d.documentId());
        assertEquals(projId, d.projectId());
        assertEquals(convId, d.conversationId());
        assertEquals("R", d.reason());
        assertEquals("t", d.targetSignatureHash());
        assertEquals(ReindexEventStatus.PENDING, d.status());
        assertEquals(taskId, d.asyncTaskId());
        assertEquals(c, d.createdAt());
        assertEquals(u, d.updatedAt());
    }

    @Test
    void toDomain_nullDocumentProjectConversation() {
        ReindexEventEntity e = mock(ReindexEventEntity.class);
        when(e.getId()).thenReturn(UUID.randomUUID());
        when(e.getDocument()).thenReturn(null);
        when(e.getProject()).thenReturn(null);
        when(e.getConversation()).thenReturn(null);
        when(e.getReason()).thenReturn("x");
        when(e.getTargetSignatureHash()).thenReturn("h");
        when(e.getStatus()).thenReturn(ReindexEventStatus.COMPLETED);
        when(e.getAsyncTaskId()).thenReturn(null);
        when(e.getCreatedAt()).thenReturn(Instant.now());
        when(e.getUpdatedAt()).thenReturn(Instant.now());

        ReindexEvent d = ReindexEventMapper.toDomain(e);
        assertNull(d.documentId());
        assertNull(d.projectId());
        assertNull(d.conversationId());
        assertNull(d.asyncTaskId());
    }
}
