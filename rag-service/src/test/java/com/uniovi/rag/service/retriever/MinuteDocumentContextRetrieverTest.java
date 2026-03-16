package com.uniovi.rag.service.retriever;

import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MinuteDocumentContextRetrieverTest {

    private PgVectorStore vectorStore;
    private ChatClient chatClient;
    private MinuteDocumentContextRetriever retriever;

    @BeforeEach
    void setUp() {
        vectorStore = mock(PgVectorStore.class);
        chatClient = mock(ChatClient.class);
        retriever = new MinuteDocumentContextRetriever(vectorStore, chatClient, 10, 0.7);
    }

    @Test
    void createContext_emptyList_returnsEmptyString() {
        assertEquals("", retriever.createContext(List.of(), "query", null));
    }

    @Test
    void filterDocumentContent_nullDoc_returnsEmpty() {
        assertEquals("", retriever.filterDocumentContent(null, "query", null));
    }

    @Test
    void filterDocumentContent_emptyText_returnsEmpty() {
        Document doc = new Document("", Map.of());
        assertEquals("", retriever.filterDocumentContent(doc, "query", null));
    }

    @Test
    void filterDocumentContent_nullQuery_returnsContentWithMetadataPrefix() {
        Document doc = new Document("body", Map.of("date_iso", "2025-01-15"));
        String result = retriever.filterDocumentContent(doc, null, null);
        assertTrue(result.contains("body"));
    }
}
