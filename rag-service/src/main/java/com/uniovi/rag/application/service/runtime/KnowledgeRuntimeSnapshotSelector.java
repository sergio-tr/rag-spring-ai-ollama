package com.uniovi.rag.application.service.runtime;

import com.uniovi.rag.application.service.knowledge.KnowledgeSnapshotService;
import com.uniovi.rag.domain.runtime.engine.KnowledgeSnapshotSelection;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeIndexSnapshotEntity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Binds ACTIVE {@code knowledge_index_snapshot} ids for runtime execution. No {@code vector_store} access.
 */
@Service
public class KnowledgeRuntimeSnapshotSelector {

    private final KnowledgeSnapshotService knowledgeSnapshotService;

    public KnowledgeRuntimeSnapshotSelector(KnowledgeSnapshotService knowledgeSnapshotService) {
        this.knowledgeSnapshotService = knowledgeSnapshotService;
    }

    /**
     * @param conversationId {@code null} for stateless (legacy HTTP) requests
     */
    public KnowledgeSnapshotSelection select(UUID projectId, UUID conversationId) {
        if (projectId == null) {
            return KnowledgeSnapshotSelection.empty();
        }
        Optional<KnowledgeIndexSnapshotEntity> projectSnap =
                knowledgeSnapshotService.findActiveProjectSnapshot(projectId);
        Optional<KnowledgeIndexSnapshotEntity> chatSnap =
                conversationId == null
                        ? Optional.empty()
                        : knowledgeSnapshotService.findActiveConversationSnapshot(conversationId);

        Optional<UUID> projectIdOpt = projectSnap.map(KnowledgeIndexSnapshotEntity::getId);
        Optional<UUID> chatIdOpt = chatSnap.map(KnowledgeIndexSnapshotEntity::getId);

        List<UUID> ordered = new ArrayList<>();
        if (conversationId == null) {
            projectIdOpt.ifPresent(ordered::add);
        } else {
            LinkedHashSet<UUID> dedup = new LinkedHashSet<>();
            projectIdOpt.ifPresent(dedup::add);
            chatIdOpt.ifPresent(dedup::add);
            ordered.addAll(dedup);
        }

        Optional<String> projHash = projectSnap.map(KnowledgeIndexSnapshotEntity::getSignatureHash);
        Optional<String> chatHash = chatSnap.map(KnowledgeIndexSnapshotEntity::getSignatureHash);

        return new KnowledgeSnapshotSelection(
                ordered, projectIdOpt, chatIdOpt, projHash, chatHash);
    }
}
