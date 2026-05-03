package com.uniovi.rag.service.postretrieval;

import org.springframework.ai.document.Document;

import java.util.List;

/**
 * Keeps top-K documents by order (assumes retrieval already sorted by relevance).
 * Reduces context size when many documents are retrieved.
 */
public class DefaultPostRetrievalProcessor implements PostRetrievalProcessor {

    private final int topK;

    public DefaultPostRetrievalProcessor(int topK) {
        this.topK = Math.max(1, topK);
    }

    @Override
    public List<Document> process(List<Document> documents, String query) {
        if (documents == null || documents.size() <= topK) {
            return documents;
        }
        return documents.subList(0, topK);
    }
}
