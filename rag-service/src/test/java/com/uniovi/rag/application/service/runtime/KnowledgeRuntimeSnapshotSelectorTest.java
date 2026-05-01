package com.uniovi.rag.application.service.runtime;

import com.uniovi.rag.application.service.knowledge.KnowledgeSnapshotService;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeIndexSnapshotEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeRuntimeSnapshotSelectorTest {

    @Mock private KnowledgeSnapshotService knowledgeSnapshotService;

    @InjectMocks private KnowledgeRuntimeSnapshotSelector selector;

    @Test
    void select_returnsEmpty_whenProjectIdNull() {
        assertThat(selector.select(null, UUID.randomUUID()).orderedSnapshotIds()).isEmpty();
    }

    @Test
    void select_projectOnly_whenConversationNull() {
        UUID pid = UUID.randomUUID();
        UUID sid = UUID.randomUUID();
        KnowledgeIndexSnapshotEntity snap = mock(KnowledgeIndexSnapshotEntity.class);
        when(snap.getId()).thenReturn(sid);
        when(snap.getSignatureHash()).thenReturn("ph");
        when(knowledgeSnapshotService.findActiveProjectSnapshot(pid)).thenReturn(Optional.of(snap));
        var sel = selector.select(pid, null);
        assertThat(sel.orderedSnapshotIds()).containsExactly(sid);
        assertThat(sel.projectSharedSnapshotId()).contains(sid);
        assertThat(sel.projectSnapshotSignatureHash()).contains("ph");
        assertThat(sel.chatLocalSnapshotId()).isEmpty();
    }

    @Test
    void select_dedupesProjectAndChatSnapshots_whenConversationPresent() {
        UUID pid = UUID.randomUUID();
        UUID cid = UUID.randomUUID();
        UUID sharedId = UUID.randomUUID();
        KnowledgeIndexSnapshotEntity project = mock(KnowledgeIndexSnapshotEntity.class);
        when(project.getId()).thenReturn(sharedId);
        when(project.getSignatureHash()).thenReturn("a");
        KnowledgeIndexSnapshotEntity chat = mock(KnowledgeIndexSnapshotEntity.class);
        when(chat.getId()).thenReturn(sharedId);
        when(chat.getSignatureHash()).thenReturn("b");
        when(knowledgeSnapshotService.findActiveProjectSnapshot(pid)).thenReturn(Optional.of(project));
        when(knowledgeSnapshotService.findActiveConversationSnapshot(cid)).thenReturn(Optional.of(chat));
        var sel = selector.select(pid, cid);
        assertThat(sel.orderedSnapshotIds()).containsExactly(sharedId);
    }

    @Test
    void select_ordersProjectThenChat_whenDistinct() {
        UUID pid = UUID.randomUUID();
        UUID cid = UUID.randomUUID();
        UUID pSnap = UUID.randomUUID();
        UUID cSnap = UUID.randomUUID();
        KnowledgeIndexSnapshotEntity project = mock(KnowledgeIndexSnapshotEntity.class);
        when(project.getId()).thenReturn(pSnap);
        when(project.getSignatureHash()).thenReturn("p");
        KnowledgeIndexSnapshotEntity chat = mock(KnowledgeIndexSnapshotEntity.class);
        when(chat.getId()).thenReturn(cSnap);
        when(chat.getSignatureHash()).thenReturn("c");
        when(knowledgeSnapshotService.findActiveProjectSnapshot(pid)).thenReturn(Optional.of(project));
        when(knowledgeSnapshotService.findActiveConversationSnapshot(cid)).thenReturn(Optional.of(chat));
        var sel = selector.select(pid, cid);
        assertThat(sel.orderedSnapshotIds()).isEqualTo(List.of(pSnap, cSnap));
        assertThat(sel.chatSnapshotSignatureHash()).contains("c");
    }
}
