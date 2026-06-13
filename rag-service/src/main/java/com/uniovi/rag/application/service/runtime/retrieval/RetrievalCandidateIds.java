package com.uniovi.rag.application.service.runtime.retrieval;

import org.springframework.ai.document.Document;

import java.util.Map;
import java.util.UUID;

/**
 * Stable candidate identity for fusion: {@code snapshotId:projectDocumentId:chunkIndex|DOC}.
 */
public final class RetrievalCandidateIds {

    private RetrievalCandidateIds() {}

    public static String fromDocument(Document d, UUID snapshotId) {
        String docPart = extractProjectDocumentId(d.getMetadata());
        String chunkPart = extractChunkPart(d.getMetadata());
        return snapshotId + ":" + docPart + ":" + chunkPart;
    }

    public static String fromSparseRow(UUID snapshotId, Map<String, Object> metadata, Integer chunkIndex) {
        String docPart = extractProjectDocumentId(metadata);
        String chunkPart = chunkIndex != null ? String.valueOf(chunkIndex) : "DOC";
        return snapshotId + ":" + docPart + ":" + chunkPart;
    }

    static String extractProjectDocumentId(Map<String, Object> meta) {
        if (meta == null) {
            return "unknown";
        }
        Object id = meta.get("document_id");
        if (id == null) {
            id = meta.get("documentId");
        }
        if (id == null) {
            id = meta.get("projectDocumentId");
        }
        if (id == null) {
            id = meta.get("id");
        }
        return id != null ? String.valueOf(id) : "unknown";
    }

    private static String extractChunkPart(Map<String, Object> meta) {
        if (meta == null) {
            return "DOC";
        }
        Object chunkIndex = meta.get("chunk_index");
        if (chunkIndex == null) {
            chunkIndex = meta.get("chunkIndex");
        }
        if (chunkIndex instanceof Number n) {
            return String.valueOf(n.intValue());
        }
        if (chunkIndex != null) {
            return String.valueOf(chunkIndex);
        }
        return "DOC";
    }
}
