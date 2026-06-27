package com.uniovi.rag.application.service.runtime;

import com.uniovi.rag.application.service.knowledge.KnowledgeSnapshotService;
import com.uniovi.rag.application.service.runtime.config.MaterializationAwareSnapshotResolver;
import com.uniovi.rag.infrastructure.persistence.KnowledgeIndexSnapshotRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeIndexSnapshotEntity;
import org.springframework.web.server.ResponseStatusException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.uniovi.rag.application.service.runtime.config.IndexSnapshotCapabilities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KnowledgeRuntimeSnapshotSelectorTest {

    @Mock private KnowledgeSnapshotService knowledgeSnapshotService;
    @Mock private KnowledgeIndexSnapshotRepository knowledgeIndexSnapshotRepository;
    @Mock private MaterializationAwareSnapshotResolver materializationAwareSnapshotResolver;

    @InjectMocks private KnowledgeRuntimeSnapshotSelector selector;

    private void stubProjectSnapshot(UUID pid, UUID snapId, KnowledgeIndexSnapshotEntity snap) {
        when(materializationAwareSnapshotResolver.resolveProjectSnapshot(eq(pid), any()))
                .thenReturn(
                        Optional.of(
                                new MaterializationAwareSnapshotResolver.ResolvedProjectSnapshot(
                                        snapId,
                                        "hash",
                                        Map.of("embeddingModelId", "mxbai-embed-large"),
                                        IndexSnapshotCapabilities.fromIndexProfile(
                                                Map.of("embeddingModelId", "mxbai-embed-large")),
                                        true)));
        when(knowledgeIndexSnapshotRepository.findById(snapId)).thenReturn(Optional.of(snap));
    }

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
        when(snap.getIndexProfileJsonb()).thenReturn(Map.of("embeddingModelId", "mxbai-embed-large"));
        when(snap.getIndexProfileHash()).thenReturn("hash");
        stubProjectSnapshot(pid, sid, snap);
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
        when(project.getIndexProfileJsonb()).thenReturn(Map.of("embeddingModelId", "mxbai-embed-large"));
        when(project.getIndexProfileHash()).thenReturn("hash");
        KnowledgeIndexSnapshotEntity chat = mock(KnowledgeIndexSnapshotEntity.class);
        when(chat.getId()).thenReturn(sharedId);
        when(chat.getSignatureHash()).thenReturn("b");
        stubProjectSnapshot(pid, sharedId, project);
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
        when(project.getIndexProfileJsonb()).thenReturn(Map.of("embeddingModelId", "mxbai-embed-large"));
        when(project.getIndexProfileHash()).thenReturn("hash");
        KnowledgeIndexSnapshotEntity chat = mock(KnowledgeIndexSnapshotEntity.class);
        when(chat.getId()).thenReturn(cSnap);
        when(chat.getSignatureHash()).thenReturn("c");
        stubProjectSnapshot(pid, pSnap, project);
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
        when(project.getIndexProfileHash()).thenReturn("hash");
        KnowledgeIndexSnapshotEntity chat = mock(KnowledgeIndexSnapshotEntity.class);
        when(chat.getId()).thenReturn(UUID.randomUUID());
        when(chat.getSignatureHash()).thenReturn("c");
        when(chat.getIndexProfileJsonb()).thenReturn(Map.of("embeddingModelId", "nomic-embed-text"));
        stubProjectSnapshot(pid, UUID.randomUUID(), project);
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
