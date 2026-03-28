package com.uniovi.rag.configuration;

import com.uniovi.rag.observability.ObservabilitySupport;
import com.uniovi.rag.observability.TracedMinuteDocumentRepository;
import com.uniovi.rag.service.document.DocumentService;
import com.uniovi.rag.service.document.MetadataMinuteDocumentService;
import com.uniovi.rag.service.document.SimpleDocumentService;
import com.uniovi.rag.repository.MinuteDocumentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link RagDocumentConfiguration}.
 */
class RagDocumentConfigurationTest {

    @Test
    void documentServiceBean_metadataEnabled_returnsMetadataMinuteDocumentService() {
        RagDocumentConfiguration config = new RagDocumentConfiguration();
        RagFeatureConfiguration featureConfig = new RagFeatureConfiguration();
        featureConfig.setMetadataEnabled(true);
        PgVectorStore vectorStore = mock(PgVectorStore.class);
        ChatClient chatClient = mock(ChatClient.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        MetadataMinuteDocumentService metadataService = new MetadataMinuteDocumentService(
                vectorStore, chatClient, jdbcTemplate, 400);
        SimpleDocumentService simpleService = new SimpleDocumentService(
                vectorStore, chatClient, jdbcTemplate, 400);

        DocumentService result = config.documentService(
                featureConfig, vectorStore, chatClient, jdbcTemplate, metadataService, simpleService, null);
        assertNotNull(result);
        assertSame(metadataService, result);
    }

    @Test
    void documentServiceBean_metadataDisabled_returnsSimpleDocumentService() {
        RagDocumentConfiguration config = new RagDocumentConfiguration();
        RagFeatureConfiguration featureConfig = new RagFeatureConfiguration();
        featureConfig.setMetadataEnabled(false);
        PgVectorStore vectorStore = mock(PgVectorStore.class);
        ChatClient chatClient = mock(ChatClient.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        MetadataMinuteDocumentService metadataService = new MetadataMinuteDocumentService(
                vectorStore, chatClient, jdbcTemplate, 400);
        SimpleDocumentService simpleService = new SimpleDocumentService(
                vectorStore, chatClient, jdbcTemplate, 400);

        DocumentService result = config.documentService(
                featureConfig, vectorStore, chatClient, jdbcTemplate, metadataService, simpleService, null);
        assertNotNull(result);
        assertSame(simpleService, result);
    }

    @Test
    void documentServiceBean_withObservability_returnsTracedDocumentService() {
        RagDocumentConfiguration config = new RagDocumentConfiguration();
        RagFeatureConfiguration featureConfig = new RagFeatureConfiguration();
        featureConfig.setMetadataEnabled(false);
        PgVectorStore vectorStore = mock(PgVectorStore.class);
        ChatClient chatClient = mock(ChatClient.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        MetadataMinuteDocumentService metadataService = new MetadataMinuteDocumentService(
                vectorStore, chatClient, jdbcTemplate, 400);
        SimpleDocumentService simpleService = new SimpleDocumentService(
                vectorStore, chatClient, jdbcTemplate, 400);
        ObservabilitySupport observability = mock(ObservabilitySupport.class);

        DocumentService result = config.documentService(
                featureConfig, vectorStore, chatClient, jdbcTemplate, metadataService, simpleService, observability);
        assertNotNull(result);
        assertInstanceOf(com.uniovi.rag.observability.TracedDocumentService.class, result);
    }

    @Test
    void minuteDocumentRepository_withoutObservability_returnsImpl() {
        RagDocumentConfiguration config = new RagDocumentConfiguration();
        DocumentService documentService = mock(DocumentService.class);
        MetadataMinuteDocumentService metadataMinuteDocumentService = mock(MetadataMinuteDocumentService.class);

        MinuteDocumentRepository repo = config.minuteDocumentRepository(
                documentService, metadataMinuteDocumentService, null);
        assertNotNull(repo);
        assertInstanceOf(com.uniovi.rag.repository.impl.MinuteDocumentRepositoryImpl.class, repo);
    }

    @Test
    void minuteDocumentRepository_withObservability_returnsTraced() {
        RagDocumentConfiguration config = new RagDocumentConfiguration();
        DocumentService documentService = mock(DocumentService.class);
        MetadataMinuteDocumentService metadataMinuteDocumentService = mock(MetadataMinuteDocumentService.class);
        ObservabilitySupport observability = mock(ObservabilitySupport.class);

        MinuteDocumentRepository repo = config.minuteDocumentRepository(
                documentService, metadataMinuteDocumentService, observability);
        assertNotNull(repo);
        assertInstanceOf(TracedMinuteDocumentRepository.class, repo);
    }
}
