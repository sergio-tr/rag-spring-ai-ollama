package com.uniovi.rag.domain.runtime.advisor;

import java.util.List;
import java.util.Objects;

/**
 * Canonical packed context artifact for dense workflows (advisor path).
 */
public record PackedContextSet(
        List<PackedContextBlock> blocks,
        String packingStrategyId,
        int totalSourceCount,
        int totalBlockCount,
        List<String> packingNotes,
        String promptContextText) {

    public PackedContextSet {
        blocks = List.copyOf(Objects.requireNonNull(blocks, "blocks"));
        packingStrategyId = packingStrategyId != null ? packingStrategyId : "";
        packingNotes = List.copyOf(Objects.requireNonNull(packingNotes, "packingNotes"));
        promptContextText = promptContextText != null ? promptContextText : "";
        if (totalBlockCount != blocks.size()) {
            throw new IllegalArgumentException("totalBlockCount must equal blocks.size()");
        }
    }
}
