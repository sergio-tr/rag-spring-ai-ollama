package com.uniovi.rag.domain.runtime.engine;

import java.util.List;
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
        Optional<String> chatSnapshotSignatureHash) {

    public KnowledgeSnapshotSelection {
        orderedSnapshotIds = List.copyOf(orderedSnapshotIds);
        projectSharedSnapshotId = projectSharedSnapshotId == null ? Optional.empty() : projectSharedSnapshotId;
        chatLocalSnapshotId = chatLocalSnapshotId == null ? Optional.empty() : chatLocalSnapshotId;
        projectSnapshotSignatureHash =
                projectSnapshotSignatureHash == null ? Optional.empty() : projectSnapshotSignatureHash;
        chatSnapshotSignatureHash =
                chatSnapshotSignatureHash == null ? Optional.empty() : chatSnapshotSignatureHash;
    }

    public static KnowledgeSnapshotSelection empty() {
        return new KnowledgeSnapshotSelection(
                List.of(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }
}
