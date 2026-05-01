package com.uniovi.rag.services.retriever;

import org.springframework.ai.document.Document;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.ai.vectorstore.SearchRequest;

import java.util.List;
import java.util.stream.Collectors;

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
    public String retrieve(String query) {
        SearchRequest req = SearchRequest.query(query).withTopK(topK);
        List<Document> retrievedDocs = vectorStore.similaritySearch(req);

        return retrievedDocs.stream()
                .map(doc -> filterContentByQuestion(doc, query))
                .reduce("", (a, b) -> a + "\n" + b);
    }

    @Override
    public String retrieve(String query, String context) {
        SearchRequest req = SearchRequest.query(query).withTopK(topK);
        List<Document> retrievedDocs = vectorStore.similaritySearch(req);

        return retrievedDocs.stream()
                .map(doc -> filterContentByQuestion(doc, query, context))
                .collect(Collectors.joining("\n"));
    }

    protected abstract String filterContentByQuestion(Document doc, String query);
    protected abstract String filterContentByQuestion(Document doc, String query, String context);


}
