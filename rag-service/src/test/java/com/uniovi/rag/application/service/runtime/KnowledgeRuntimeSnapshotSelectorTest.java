package com.uniovi.rag.application.service.runtime;

import com.uniovi.rag.application.service.knowledge.KnowledgeSnapshotService;
import com.uniovi.rag.infrastructure.persistence.KnowledgeIndexSnapshotRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeIndexSnapshotEntity;
import org.springframework.web.server.ResponseStatusException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeRuntimeSnapshotSelectorTest {

    @Mock private KnowledgeSnapshotService knowledgeSnapshotService;
    @Mock private KnowledgeIndexSnapshotRepository knowledgeIndexSnapshotRepository;

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

    @Test
    void select_blocksProjectAndChatSnapshotsWithDifferentEmbeddingModels() {
        UUID pid = UUID.randomUUID();
        UUID cid = UUID.randomUUID();
        KnowledgeIndexSnapshotEntity project = mock(KnowledgeIndexSnapshotEntity.class);
        when(project.getId()).thenReturn(UUID.randomUUID());
        when(project.getSignatureHash()).thenReturn("p");
        when(project.getIndexProfileJsonb()).thenReturn(Map.of("embeddingModelId", "mxbai-embed-large"));
        KnowledgeIndexSnapshotEntity chat = mock(KnowledgeIndexSnapshotEntity.class);
        when(chat.getId()).thenReturn(UUID.randomUUID());
        when(chat.getSignatureHash()).thenReturn("c");
        when(chat.getIndexProfileJsonb()).thenReturn(Map.of("embeddingModelId", "nomic-embed-text"));
        when(knowledgeSnapshotService.findActiveProjectSnapshot(pid)).thenReturn(Optional.of(project));
        when(knowledgeSnapshotService.findActiveConversationSnapshot(cid)).thenReturn(Optional.of(chat));

        assertThatThrownBy(() -> selector.select(pid, cid))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("EMBEDDING_MODEL_SNAPSHOT_MISMATCH");
    }

    @Test
    void selectExplicit_blocksSnapshotsWithDifferentEmbeddingModels() {
        UUID projectId = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        KnowledgeIndexSnapshotEntity a = mock(KnowledgeIndexSnapshotEntity.class);
        when(a.getIndexProfileJsonb()).thenReturn(Map.of("embeddingModelId", "mxbai-embed-large"));
        KnowledgeIndexSnapshotEntity b = mock(KnowledgeIndexSnapshotEntity.class);
        when(b.getIndexProfileJsonb()).thenReturn(Map.of("embeddingModelId", "nomic-embed-text"));
        when(knowledgeIndexSnapshotRepository.findById(first)).thenReturn(Optional.of(a));
        when(knowledgeIndexSnapshotRepository.findById(second)).thenReturn(Optional.of(b));

        assertThatThrownBy(() -> selector.selectExplicit(projectId, List.of(first, second)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("EMBEDDING_MODEL_SNAPSHOT_MISMATCH");
    }
}
