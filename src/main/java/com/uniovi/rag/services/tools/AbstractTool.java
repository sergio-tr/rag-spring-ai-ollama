package com.uniovi.rag.services.tools;

import com.uniovi.rag.services.retriever.ContextRetriever;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

import java.util.List;

public abstract class AbstractTool implements Tool {

    protected final ChatClient chatClient;
    protected final ContextRetriever retriever;
    protected final EnhancedNERHandler nerHandler;

    public AbstractTool(ChatClient chatClient, ContextRetriever retriever) {
        this.chatClient = chatClient;
        this.retriever = retriever;
        this.nerHandler = new EnhancedNERHandler(chatClient);
    }

    /**
     * Retrieves all documents matching the query with maximum recall.
     * If more documents are needed, use retrieveDocumentsIntelligently().
     */
    protected List<Document> retrieveAllDocuments(String query) {
        retriever.setTopK(100);  // Reduced from 1000 to improve performance
        retriever.setSimilarityThreshold(0);
        return retriever.retrieve(query);
    }
    
    /**
     * Retrieves documents intelligently with a configurable limit.
     * Use this when you need more control over the number of documents retrieved.
     */
    protected List<Document> retrieveDocumentsIntelligently(String query, int maxDocuments) {
        retriever.setTopK(Math.min(maxDocuments, 200));  // Maximum 200 to avoid overload
        retriever.setSimilarityThreshold(0);
        return retriever.retrieve(query);
    }

    protected List<Document> retrieveDocuments(String query) {
        retriever.restoreDefaultSettings();
        return retriever.retrieve(query);
    }

    protected List<Document> retrieveDocumentsWithTopK(String query, int topK) {
        retriever.restoreDefaultSettings();
        retriever.setTopK(topK);
        return retriever.retrieve(query);
    }


}
