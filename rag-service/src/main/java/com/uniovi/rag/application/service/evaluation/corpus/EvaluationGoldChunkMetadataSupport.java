package com.uniovi.rag.application.service.evaluation.corpus;

import java.util.Map;

/** Stamps workbook gold ids into vector-store chunk metadata for embedding evaluation scoring. */
public final class EvaluationGoldChunkMetadataSupport {

    public static final String KEY_EVALUATION_DOCUMENT_ID = "evaluationDocumentId";
    public static final String KEY_EVALUATION_CHUNK_ID = "evaluationChunkId";

    private EvaluationGoldChunkMetadataSupport() {}

    public static void mergeGoldIds(Map<String, Object> metadata, String evaluationDocumentId, String evaluationChunkId) {
        if (metadata == null) {
            return;
        }
        if (evaluationDocumentId != null && !evaluationDocumentId.isBlank()) {
            String doc = evaluationDocumentId.trim();
            metadata.put(KEY_EVALUATION_DOCUMENT_ID, doc);
            metadata.put("document_id", doc);
            metadata.put("documentId", doc);
        }
        if (evaluationChunkId != null && !evaluationChunkId.isBlank()) {
            String chunk = evaluationChunkId.trim();
            metadata.put(KEY_EVALUATION_CHUNK_ID, chunk);
            metadata.put("chunk_id", chunk);
            metadata.put("chunkId", chunk);
        }
    }
}
