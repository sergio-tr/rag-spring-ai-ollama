package com.uniovi.rag.services.retriever;

import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.PgVectorStore;

public class BasicContextRetriever extends AbstractContextRetriever {

    public BasicContextRetriever(PgVectorStore vectorStore, ChatClient chatClient, int topK, double similarityThreshold) {
        super(vectorStore, chatClient, topK, similarityThreshold);
    }

    @Override
    public String filterDocumentContent(Document doc, String query, JSONObject entities) {
        String content = doc.getContent() != null ? doc.getContent() : "";
        return buildContentWithOptionalMetadataPrefix(doc, content);
    }
}
