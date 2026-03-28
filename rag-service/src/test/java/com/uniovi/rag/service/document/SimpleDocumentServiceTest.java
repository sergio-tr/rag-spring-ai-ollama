package com.uniovi.rag.service.document;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link SimpleDocumentService}.
 */
class SimpleDocumentServiceTest {

    @Test
    void constructor_createsServiceWithGivenChunkSize() {
        PgVectorStore vectorStore = mock(PgVectorStore.class);
        ChatClient chatClient = mock(ChatClient.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

        SimpleDocumentService service = new SimpleDocumentService(
                vectorStore, chatClient, jdbcTemplate, 500);

        assertNotNull(service);
    }

    @Test
    void processDocument_emptyTextContent_throwsIllegalArgumentException() {
        PgVectorStore vectorStore = mock(PgVectorStore.class);
        ChatClient chatClient = mock(ChatClient.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

        SimpleDocumentService service = new SimpleDocumentService(
                vectorStore, chatClient, jdbcTemplate, 400);

        MultipartFile emptyFile = new MockMultipartFile(
                "file", "empty.txt", "text/plain", new byte[0]);

        assertThrows(IllegalArgumentException.class, () -> service.processDocument(emptyFile));
    }
}
