package com.uniovi.rag.application.service.runtime.retrieval;

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

class FilteredContextRetrieverTest {

    private PgVectorStore vectorStore;
    private ChatClient chatClient;
    private FilteredContextRetriever retriever;

    @BeforeEach
    void setUp() {
        vectorStore = mock(PgVectorStore.class);
        chatClient = mock(ChatClient.class);
        retriever = new FilteredContextRetriever(vectorStore, chatClient, 10, 0.7);
    }

    @Test
    void createContext_emptyList_returnsEmptyString() {
        assertEquals("", retriever.createContext(List.of(), "query", null));
    }

    @Test
    void filterDocumentContent_nullDoc_returnsEmpty() {
        String result = retriever.filterDocumentContent(null, "query", null);
        assertEquals("", result);
    }

    @Test
    void filterDocumentContent_emptyText_returnsEmpty() {
        Document doc = new Document("", Map.of());
        String result = retriever.filterDocumentContent(doc, "query", null);
        assertEquals("", result);
    }

    @Test
    void filterDocumentContent_nullQuery_returnsContentWithMetadataPrefix() {
        Document doc = new Document("body", Map.of("date_iso", "2025-01-15"));
        String result = retriever.filterDocumentContent(doc, null, null);
        assertTrue(result.contains("body"));
        assertTrue(result.contains("2025-01-15") || result.contains("Acta:"));
    }
}
