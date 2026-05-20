package com.uniovi.rag.application.service.runtime;

import com.uniovi.rag.application.service.knowledge.IndexProfileJsonSupport;
import com.uniovi.rag.application.service.knowledge.KnowledgeSnapshotService;
import com.uniovi.rag.domain.runtime.engine.KnowledgeSnapshotSelection;
import com.uniovi.rag.infrastructure.persistence.KnowledgeIndexSnapshotRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeIndexSnapshotEntity;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Binds ACTIVE {@code knowledge_index_snapshot} ids for runtime execution. No {@code vector_store} access.
 */
@Service
public class KnowledgeRuntimeSnapshotSelector {

    private final KnowledgeSnapshotService knowledgeSnapshotService;
    private final KnowledgeIndexSnapshotRepository knowledgeIndexSnapshotRepository;

    public KnowledgeRuntimeSnapshotSelector(
            KnowledgeSnapshotService knowledgeSnapshotService,
            KnowledgeIndexSnapshotRepository knowledgeIndexSnapshotRepository) {
        this.knowledgeSnapshotService = knowledgeSnapshotService;
        this.knowledgeIndexSnapshotRepository = knowledgeIndexSnapshotRepository;
    }

    /**
     * @param conversationId {@code null} for stateless (removed HTTP) requests
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

        Optional<String> dense = resolveDenseEmbeddingFromPair(projectSnap, chatSnap);

        return new KnowledgeSnapshotSelection(ordered, projectIdOpt, chatIdOpt, projHash, chatHash, dense);
    }

    /**
     * Lab-only explicit snapshot selection (bypass ACTIVE snapshot lookup).
     *
     * <p>Ordered ids are used as-is; projectSharedSnapshotId is set to the first id when present.</p>
     */
    public KnowledgeSnapshotSelection selectExplicit(UUID projectId, List<UUID> orderedSnapshotIds) {
        if (projectId == null || orderedSnapshotIds == null || orderedSnapshotIds.isEmpty()) {
            return KnowledgeSnapshotSelection.empty();
        }
        List<UUID> ordered = new ArrayList<>(new LinkedHashSet<>(orderedSnapshotIds));
        Optional<UUID> projectShared = Optional.of(ordered.getFirst());
        Optional<String> dense = resolveDenseEmbeddingFromSnapshotIds(ordered);
        return new KnowledgeSnapshotSelection(
                ordered, projectShared, Optional.empty(), Optional.empty(), Optional.empty(), dense);
    }

    private static Optional<String> resolveDenseEmbeddingFromPair(
            Optional<KnowledgeIndexSnapshotEntity> projectSnap, Optional<KnowledgeIndexSnapshotEntity> chatSnap) {
        Optional<String> fromProject =
                projectSnap.flatMap(s -> IndexProfileJsonSupport.readEmbeddingModelId(s.getIndexProfileJsonb()));
        Optional<String> fromChat =
                chatSnap.flatMap(s -> IndexProfileJsonSupport.readEmbeddingModelId(s.getIndexProfileJsonb()));
        if (fromProject.isPresent()
                && fromChat.isPresent()
                && !IndexProfileJsonSupport.normalizeEmbeddingKey(fromProject.get())
                        .equals(IndexProfileJsonSupport.normalizeEmbeddingKey(fromChat.get()))) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "EMBEDDING_MODEL_SNAPSHOT_MISMATCH: project and chat ACTIVE knowledge snapshots use different embeddingModelId values (project="
                            + fromProject.get()
                            + ", chat="
                            + fromChat.get()
                            + "). Reindex one scope with the same embedding model before retrieval.");
        }
        return fromProject.or(() -> fromChat);
    }

    private Optional<String> resolveDenseEmbeddingFromSnapshotIds(List<UUID> ordered) {
        String canonical = null;
        for (UUID id : ordered) {
            KnowledgeIndexSnapshotEntity snap =
                    knowledgeIndexSnapshotRepository.findById(id).orElseThrow(
                            () -> new IllegalStateException("knowledge_index_snapshot not found: " + id));
            Optional<String> emb = IndexProfileJsonSupport.readEmbeddingModelId(snap.getIndexProfileJsonb());
            if (emb.isEmpty()) {
                continue;
            }
            if (canonical == null) {
                canonical = emb.get();
            } else if (!IndexProfileJsonSupport.normalizeEmbeddingKey(canonical)
                    .equals(IndexProfileJsonSupport.normalizeEmbeddingKey(emb.get()))) {
                throw new ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "EMBEDDING_MODEL_SNAPSHOT_MISMATCH: explicit Lab knowledge snapshots use different embeddingModelId values ("
                                + canonical
                                + " vs "
                                + emb.get()
                                + "). Select snapshots indexed with the same embedding model.");
            }
        }
        return Optional.ofNullable(canonical);
    }
}
