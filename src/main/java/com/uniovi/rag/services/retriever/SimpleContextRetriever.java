package com.uniovi.rag.services.retriever;

import org.springframework.ai.document.Document;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.ai.vectorstore.SearchRequest;

import java.util.List;

public class SimpleContextRetriever extends AbstractContextRetriever  {

    public SimpleContextRetriever(PgVectorStore vectorStore, OllamaChatModel model, int topK) {
        super(vectorStore, model, topK);
    }

    @Override
    public String retrieve(String query) {
        SearchRequest req = SearchRequest
                .query(query)
                .withTopK(10);
        List<Document> relevantDocs = vectorStore.similaritySearch(req);

        return relevantDocs.stream()
                .map(Document::getContent)
                .reduce("", (a, b) -> a + "\n\n" + b);
    }
}
