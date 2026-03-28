package com.uniovi.rag.service.document;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link MetadataMinuteDocumentService}. Covers constructor and public constants.
 */
class MetadataMinuteDocumentServiceTest {

    @Test
    void constructor_createsService() {
        PgVectorStore vectorStore = mock(PgVectorStore.class);
        ChatClient chatClient = mock(ChatClient.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        MetadataMinuteDocumentService service = new MetadataMinuteDocumentService(
                vectorStore, chatClient, jdbcTemplate, 400);
        assertNotNull(service);
    }

    @Test
    void promptConstants_areNonEmpty() {
        assertNotNull(MetadataMinuteDocumentService.PROMPT_DECISIONS);
        assertFalse(MetadataMinuteDocumentService.PROMPT_DECISIONS.isBlank());
        assertNotNull(MetadataMinuteDocumentService.PROMPT_ENTITIES);
        assertFalse(MetadataMinuteDocumentService.PROMPT_ENTITIES.isBlank());
        assertNotNull(MetadataMinuteDocumentService.PROMPT_TOPICS);
        assertNotNull(MetadataMinuteDocumentService.PROMPT_SUMMARY);
        assertNotNull(MetadataMinuteDocumentService.SYSTEM_PROMPT_LINE_DATA);
        assertNotNull(MetadataMinuteDocumentService.SYSTEM_PROMPT_SUMMARY);
    }
}
