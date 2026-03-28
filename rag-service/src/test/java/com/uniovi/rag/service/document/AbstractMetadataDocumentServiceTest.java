package com.uniovi.rag.service.document;

import com.uniovi.rag.model.Minute;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** Covers {@link AbstractMetadataDocumentService} via concrete subclass. */
class AbstractMetadataDocumentServiceTest {

    @Test
    void metadataMinuteDocumentService_isInstanceOfAbstractMetadataDocumentService() {
        PgVectorStore vectorStore = mock(PgVectorStore.class);
        ChatClient chatClient = mock(ChatClient.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        MetadataMinuteDocumentService service = new MetadataMinuteDocumentService(
                vectorStore, chatClient, jdbcTemplate, 400);
        assertNotNull(service);
        assertTrue(service instanceof AbstractMetadataDocumentService);
    }
}
