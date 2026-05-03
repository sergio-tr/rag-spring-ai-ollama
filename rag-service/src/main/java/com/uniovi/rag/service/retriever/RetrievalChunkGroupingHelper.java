package com.uniovi.rag.service.retriever;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;

import java.util.List;

/**
 * Exposes {@link AbstractContextRetriever#groupAndCombineChunks(List)} for advanced retrieval prompt assembly.
 */
public final class RetrievalChunkGroupingHelper extends BasicContextRetriever {

    public RetrievalChunkGroupingHelper(
            PgVectorStore vectorStore, ChatClient chatClient, int topK, double similarityThreshold) {
        super(vectorStore, chatClient, topK, similarityThreshold);
    }

    public List<Document> groupDocuments(List<Document> docs) {
        return groupAndCombineChunks(docs);
    }
}
