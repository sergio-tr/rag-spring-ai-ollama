package com.uniovi.rag.application.service.runtime.retrieval;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NerDateMetadataFilterTest {

    private PgVectorStore vectorStore;
    private BasicContextRetriever retriever;

    @BeforeEach
    void setUp() {
        vectorStore = mock(PgVectorStore.class);
        retriever = new BasicContextRetriever(vectorStore, mock(ChatClient.class), 10, 0.7);
    }

    @Test
    void nerDateFilterAcceptsStringDateMetadata() {
        Document ok = new Document("x", Map.of("document_id", "d1", "chunk_index", 0, "date_iso", "2024-01-15", "year", 2024));
        Document wrong = new Document("y", Map.of("document_id", "d2", "chunk_index", 0, "date_iso", "2024-02-01", "year", 2024));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(ok, wrong));

        JSONObject ner = new JSONObject().put("date", "2024-01-15");
        List<Document> out = retriever.retrieveWithMetadataFilters("q", ner);

        assertEquals(1, out.size());
        assertEquals("x", out.getFirst().getText());
    }

    @Test
    void nerDateFilterAcceptsArrayDateMetadata() {
        Document ok = new Document("x", Map.of("document_id", "d1", "chunk_index", 0, "date_iso", "2024-01-15", "year", 2024));
        Document wrong = new Document("y", Map.of("document_id", "d2", "chunk_index", 0, "date_iso", "2024-02-01", "year", 2024));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(ok, wrong));

        JSONObject ner = new JSONObject().put("date", new JSONArray().put("2024-01-15"));
        List<Document> out = retriever.retrieveWithMetadataFilters("q", ner);

        assertEquals(1, out.size());
        assertEquals("x", out.getFirst().getText());
    }

    @Test
    void explicitSpanishDateFiltersDocuments() {
        Document ok = new Document("x", Map.of("document_id", "d1", "chunk_index", 0, "date_iso", "2025-02-24", "year", 2025));
        Document wrong = new Document("y", Map.of("document_id", "d2", "chunk_index", 0, "date_iso", "2025-03-01", "year", 2025));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(ok, wrong));

        JSONObject ner = new JSONObject().put("date", "24 de febrero de 2025");
        List<Document> out = retriever.retrieveWithMetadataFilters("q", ner);

        assertEquals(1, out.size());
        assertEquals("x", out.getFirst().getText());
    }

    @Test
    void dateShapeMismatchDoesNotReturnUnfilteredThirtyDocuments() {
        List<Document> docs = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            String iso = String.format("2025-01-%02d", (i % 28) + 1);
            docs.add(new Document("doc" + i, Map.of(
                    "document_id", "d" + i,
                    "chunk_index", 0,
                    "date_iso", iso,
                    "year", 2025)));
        }
        docs.set(10, new Document("target", Map.of(
                "document_id", "d10",
                "chunk_index", 0,
                "date_iso", "2025-02-24",
                "year", 2025)));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(docs);

        JSONObject ner = new JSONObject().put("date", "24 de febrero de 2025");
        List<Document> out = retriever.retrieveWithMetadataFilters("q", ner);

        assertTrue(out.size() < 30, "String date must not bypass filtering and return all retrieved docs");
        assertEquals(1, out.size());
        assertEquals("target", out.getFirst().getText());
    }
}
