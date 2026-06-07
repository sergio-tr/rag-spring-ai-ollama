package com.uniovi.rag.application.service.runtime.retrieval;

import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class BasicContextRetrieverTest {

    private PgVectorStore vectorStore;
    private ChatClient chatClient;
    private BasicContextRetriever retriever;

    @BeforeEach
    void setUp() {
        vectorStore = mock(PgVectorStore.class);
        chatClient = mock(ChatClient.class);
        retriever = new BasicContextRetriever(vectorStore, chatClient, 10, 0.7);
    }

    @Test
    void createContext_emptyList_returnsEmptyString() {
        String ctx = retriever.createContext(List.of(), "query", null);
        assertEquals("", ctx);
    }

    @Test
    void createContext_singleDocument_returnsContent() {
        Document doc = new Document("content text", Map.of());
        String ctx = retriever.createContext(List.of(doc), "query", new JSONObject());
        assertTrue(ctx.contains("content text"));
    }

    @Test
    void createContext_documentWithMetadata_includesPrefix() {
        Document doc = new Document("body", Map.of("date_iso", "2025-01-15", "president", "Juan"));
        String ctx = retriever.createContext(List.of(doc), "query", null);
        assertTrue(ctx.contains("Acta:"));
        assertTrue(ctx.contains("2025-01-15"));
        assertTrue(ctx.contains("Presidente: Juan"));
        assertTrue(ctx.contains("body"));
    }

    @Test
    void setTopKAndRestore() {
        retriever.setTopK(20);
        assertEquals(20, retriever.getTopK());
        retriever.restoreDefaultSettings();
        assertEquals(10, retriever.getTopK());
    }
}
