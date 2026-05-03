package com.uniovi.rag.domain.runtime.advisor;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable packed block produced only by {@code ContextPackingAdvisor}.
 */
public record PackedContextBlock(
        String sourceId,
        String documentId,
        String blockId,
        UUID snapshotId,
        String blockText,
        int orderingIndex,
        List<String> packingNotes) {

    public PackedContextBlock {
        sourceId = sourceId != null ? sourceId : "";
        documentId = documentId != null ? documentId : "";
        blockId = Objects.requireNonNull(blockId, "blockId");
        snapshotId = Objects.requireNonNull(snapshotId, "snapshotId");
        blockText = blockText != null ? blockText : "";
        packingNotes = List.copyOf(Objects.requireNonNull(packingNotes, "packingNotes"));
    }
}
