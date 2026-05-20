package com.uniovi.rag.application.service.runtime.retrieval.post;

import org.springframework.ai.document.Document;

import java.util.List;

/**
 * Optional post-retrieval step: re-rank or filter documents before context is built.
 */
public interface PostRetrievalProcessor {

    /**
     * Optionally re-rank or reduce the retrieved documents (e.g. top-K by relevance to query).
     */
    List<Document> process(List<Document> documents, String query);
}
