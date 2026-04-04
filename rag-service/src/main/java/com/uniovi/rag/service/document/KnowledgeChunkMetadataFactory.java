package com.uniovi.rag.service.document;

import com.uniovi.rag.domain.knowledge.CorpusScope;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Minimum chunk metadata for {@code vector_store.metadata} (schema §4.2), camelCase keys.
 */
public final class KnowledgeChunkMetadataFactory {

    private KnowledgeChunkMetadataFactory() {
    }

    public static Map<String, Object> buildV2(
            CorpusScope corpusScope,
            UUID documentId,
            UUID projectId,
            UUID conversationId,
            UUID indexSnapshotId,
            String indexSignatureHashHex,
            String filename,
            int chunkIndex,
            int totalChunks,
            String legacyDocumentIdHash) {
        Map<String, Object> m = new HashMap<>();
        m.put("corpusScope", corpusScope.name());
        m.put("documentId", documentId.toString());
        if (projectId != null) {
            m.put("projectId", projectId.toString());
        }
        if (conversationId != null) {
            m.put("conversationId", conversationId.toString());
        }
        if (indexSnapshotId != null) {
            m.put("indexSnapshotId", indexSnapshotId.toString());
        }
        m.put("indexSignatureHash", indexSignatureHashHex);
        m.put("filename", filename != null ? filename : "unknown");
        m.put("chunkIndex", chunkIndex);
        m.put("totalChunks", totalChunks);
        m.put("projectDocumentId", documentId.toString());
        if (legacyDocumentIdHash != null) {
            m.put("document_id", legacyDocumentIdHash);
        }
        return m;
    }

    public static String legacyContentHashId(String filename, String content, UUID projectDocumentId) {
        String base = (filename != null ? filename : "unknown") + "_" + projectDocumentId + "_"
                + (content != null ? String.valueOf(content.hashCode()) : "0");
        return String.valueOf(Math.abs(base.hashCode()));
    }
}
