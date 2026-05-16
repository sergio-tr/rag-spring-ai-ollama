package com.uniovi.rag.domain.runtime.engine;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * ACTIVE snapshot binding for one orchestrated turn. Immutable.
 */
public record KnowledgeSnapshotSelection(
        List<UUID> orderedSnapshotIds,
        Optional<UUID> projectSharedSnapshotId,
        Optional<UUID> chatLocalSnapshotId,
        Optional<String> projectSnapshotSignatureHash,
        Optional<String> chatSnapshotSignatureHash,
        /**
         * Ollama embedding tag taken from the active project snapshot profile (used for dense query embedding).
         */
        Optional<String> denseRetrievalEmbeddingModelId) {

    public KnowledgeSnapshotSelection {
        orderedSnapshotIds = List.copyOf(orderedSnapshotIds);
        projectSharedSnapshotId = Objects.requireNonNullElseGet(projectSharedSnapshotId, Optional::empty);
        chatLocalSnapshotId = Objects.requireNonNullElseGet(chatLocalSnapshotId, Optional::empty);
        projectSnapshotSignatureHash = Objects.requireNonNullElseGet(projectSnapshotSignatureHash, Optional::empty);
        chatSnapshotSignatureHash = Objects.requireNonNullElseGet(chatSnapshotSignatureHash, Optional::empty);
        denseRetrievalEmbeddingModelId =
                Objects.requireNonNullElseGet(denseRetrievalEmbeddingModelId, Optional::empty);
    }

    public static KnowledgeSnapshotSelection empty() {
        return new KnowledgeSnapshotSelection(
                List.of(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }
}
