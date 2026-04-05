package com.uniovi.rag.service.retriever;

import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;

public class BasicContextRetriever extends AbstractContextRetriever {

    public BasicContextRetriever(PgVectorStore vectorStore, ChatClient chatClient, int topK, double similarityThreshold) {
        super(vectorStore, chatClient, topK, similarityThreshold);
    }

    public BasicContextRetriever(
            PgVectorStore vectorStore,
            ChatClient chatClient,
            int topK,
            double similarityThreshold,
            boolean knowledgeChatOverlayEnabled) {
        super(vectorStore, chatClient, topK, similarityThreshold, knowledgeChatOverlayEnabled);
    }

    @Override
    public String filterDocumentContent(Document doc, String query, JSONObject entities) {
        String content = doc.getText() != null ? doc.getText() : "";
        return buildContentWithOptionalMetadataPrefix(doc, content);
    }
}
