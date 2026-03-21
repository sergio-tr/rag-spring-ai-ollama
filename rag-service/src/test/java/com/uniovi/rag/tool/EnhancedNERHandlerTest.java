package com.uniovi.rag.tool;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EnhancedNERHandlerTest {

    private ChatClient chatClient;
    private EnhancedNERHandler handler;

    @BeforeEach
    void setUp() {
        chatClient = mock(ChatClient.class);
        handler = new EnhancedNERHandler(chatClient);
    }

    @Test
    void matchesDocumentWithNER_nullOrEmptyNer_returnsTrue() {
        Document doc = new Document("content", Map.of());
        assertTrue(handler.matchesDocumentWithNER(doc, null));
        assertTrue(handler.matchesDocumentWithNER(doc, new JSONObject()));
    }

    @Test
    void matchesDocumentWithNER_nullDoc_returnsFalse() throws JSONException {
        assertFalse(handler.matchesDocumentWithNER(null, new JSONObject().put("date", "2025-01-01")));
    }

    @Test
    void matchesDocumentWithNER_emptyDocText_returnsFalse() throws JSONException {
        Document doc = new Document("", Map.of());
        assertFalse(handler.matchesDocumentWithNER(doc, new JSONObject().put("key", "val")));
    }
}
