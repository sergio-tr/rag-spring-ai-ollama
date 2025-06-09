package com.uniovi.rag.services.retriever;

import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.ai.vectorstore.SearchRequest;

import java.util.List;
import java.util.stream.Collectors;

public abstract class AbstractContextRetriever implements ContextRetriever {

    protected final PgVectorStore vectorStore;
    protected final ChatClient chatClient;
    protected int topK;
    protected double similarityThreshold;

    private final int defaultTopK;
    private final double defaultSimilarityThreshold;

    public AbstractContextRetriever(PgVectorStore vectorStore, ChatClient chatClient, int topK, double similarityThreshold) {
        this.vectorStore = vectorStore;
        this.chatClient = chatClient;
        this.defaultTopK = topK;
        this.defaultSimilarityThreshold = similarityThreshold;
        this.topK = topK;
        this.similarityThreshold = similarityThreshold;
    }

    @Override
    public List<Document> retrieve(String query) {
        SearchRequest req = SearchRequest.
                query(query).
                withTopK(topK).
                withSimilarityThreshold(similarityThreshold);
        return vectorStore.similaritySearch(req);
    }

    @Override
    public String createContext(List<Document> documents, String query, JSONObject entities) {
        if (documents.isEmpty()) {
            return "";
        }

        return documents.stream()
                .map(doc -> filterDocumentContent(doc, query, entities))
                .collect(Collectors.joining("\n"));
    }

    @Override
    public void setTopK(int topK) {
        if (topK > 0) {
            this.topK = topK;
        }
    }

    @Override
    public void setSimilarityThreshold(double similarityThreshold) {
        if (similarityThreshold > 0 && similarityThreshold <= 1) {
            this.similarityThreshold = similarityThreshold;
        }
    }

    @Override
    public void restoreDefaultSettings() {
        this.topK = defaultTopK;
        this.similarityThreshold = defaultSimilarityThreshold;
    }

    public abstract String filterDocumentContent(Document doc, String query, JSONObject entities);

}
