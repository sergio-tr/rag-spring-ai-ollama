package com.uniovi.rag.services.retriever;

import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.vectorstore.PgVectorStore;

public abstract class AbstractContextRetriever implements ContextRetriever {

    protected final PgVectorStore vectorStore;
    protected final OllamaChatModel model;
    protected int topK;

    public AbstractContextRetriever(PgVectorStore vectorStore, OllamaChatModel model, int topK) {
        this.vectorStore = vectorStore;
        this.model = model;
        this.topK = topK;
    }

    @Override
    public String retrieve(String query, String context) {
        return retrieve(query);
    }
}
