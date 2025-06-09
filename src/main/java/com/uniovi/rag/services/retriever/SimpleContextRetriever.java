package com.uniovi.rag.services.retriever;

import org.springframework.ai.document.Document;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.vectorstore.PgVectorStore;

public class SimpleContextRetriever extends AbstractContextRetriever  {

    public SimpleContextRetriever(PgVectorStore vectorStore, OllamaChatModel model, int topK) {
        super(vectorStore, model, topK);
    }

    @Override
    protected String filterContentByQuestion(Document doc, String query) {
        return doc.getContent();
    }

    @Override
    protected String filterContentByQuestion(Document doc, String query, String context) {
        return doc.getContent();
    }
}
