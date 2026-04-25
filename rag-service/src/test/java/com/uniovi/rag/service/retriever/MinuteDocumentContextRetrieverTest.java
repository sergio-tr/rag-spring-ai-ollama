package com.uniovi.rag.service.retriever;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.json.JSONObject;
import org.json.JSONArray;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

    @Test
    void retrieveWithMetadataFilters_filtersByDate() {
        Document ok = new Document("x", Map.of("document_id", "d1", "chunk_index", 0, "date_iso", "2024-01-15", "year", 2024));
        Document wrong = new Document("y", Map.of("document_id", "d2", "chunk_index", 0, "date_iso", "2024-02-01", "year", 2024));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(ok, wrong));

        JSONObject ner = new JSONObject().put("date", new JSONArray().put("2024-01-15"));
        List<Document> out = retriever.retrieveWithMetadataFilters("q", ner);

        assertEquals(1, out.size());
        assertEquals("x", out.getFirst().getText());
    }

    @Test
    void retrieveWithMetadataFilters_fallsBackToUnfiltered_whenStrictAndLenientRemoveAll() {
        Document doc = new Document("x", Map.of("document_id", "d1", "chunk_index", 0, "date_iso", "2024-01-15", "year", 2024));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc));

        // Different year should cause lenient filtering to reject, then fallback to unfiltered
        JSONObject ner = new JSONObject().put("date", new JSONArray().put("2023-01-01"));
        List<Document> out = retriever.retrieveWithMetadataFilters("q", ner);

        assertEquals(1, out.size());
        assertEquals("x", out.getFirst().getText());
    }
}
