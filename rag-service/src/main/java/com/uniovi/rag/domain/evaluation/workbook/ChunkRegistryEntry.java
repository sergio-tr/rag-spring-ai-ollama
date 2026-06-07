package com.uniovi.rag.domain.evaluation.workbook;

/** Row from {@code chunk_registry} sheet. */
public record ChunkRegistryEntry(
        String chunkId,
        String documentId,
        String chunkType,
        String goldEvidenceText) {

    public ChunkRegistryEntry {
        if (chunkId == null || chunkId.isBlank()) {
            throw new IllegalArgumentException("chunkId required");
        }
    }
}
