package com.uniovi.rag.service.document;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** Covers {@link AbstractDocumentService} via concrete subclass. */
class AbstractDocumentServiceTest {

    @Test
    void simpleDocumentService_isInstanceOfAbstractDocumentService() {
        PgVectorStore vectorStore = mock(PgVectorStore.class);
        ChatClient chatClient = mock(ChatClient.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        SimpleDocumentService service = new SimpleDocumentService(
                vectorStore, chatClient, jdbcTemplate, 400);
        assertNotNull(service);
        assertTrue(service instanceof AbstractDocumentService);
    }
}
