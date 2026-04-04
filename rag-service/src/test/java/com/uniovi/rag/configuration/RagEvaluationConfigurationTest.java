package com.uniovi.rag.configuration;

import com.uniovi.rag.interfaces.rest.support.OllamaConnectivityChecker;
import com.uniovi.rag.application.port.ModelCatalogPort;
import com.uniovi.rag.service.config.ChatScopedRagConfigResolver;
import com.uniovi.rag.service.evaluation.EvaluationServiceFactory;
import com.uniovi.rag.service.extraction.DocumentContentExtractor;
import com.uniovi.rag.service.query.ResponseValidator;
import com.uniovi.rag.tool.metadata.MetadataLlmResponseCacheService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link RagEvaluationConfiguration}. Bean factory logic with mocked dependencies.
 */
class RagEvaluationConfigurationTest {

    @Test
    void evaluationServiceFactoryBean_createsFactory() {
        RagEvaluationConfiguration config = new RagEvaluationConfiguration();
        ChatClient chatClient = mock(ChatClient.class);
        PgVectorStore vectorStore = mock(PgVectorStore.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ResponseValidator responseValidator = mock(ResponseValidator.class);
        DocumentContentExtractor documentContentExtractor = mock(DocumentContentExtractor.class);
        OllamaConnectivityChecker ollamaConnectivityChecker = mock(OllamaConnectivityChecker.class);
        MetadataLlmResponseCacheService metadataLlmResponseCacheService = mock(MetadataLlmResponseCacheService.class);
        ModelCatalogPort modelCatalogPort = mock(ModelCatalogPort.class);
        ChatScopedRagConfigResolver chatScopedRagConfigResolver = mock(ChatScopedRagConfigResolver.class);
        doNothing().when(ollamaConnectivityChecker).prepareForQuery(any());

        EvaluationServiceFactory factory = config.evaluationServiceFactory(
                chatClient,
                vectorStore,
                jdbcTemplate,
                10,
                0.7,
                "http://localhost:8000",
                "default",
                5000,
                400,
                responseValidator,
                documentContentExtractor,
                "COT",
                1,
                350,
                512,
                500,
                200,
                ollamaConnectivityChecker,
                metadataLlmResponseCacheService,
                modelCatalogPort,
                chatScopedRagConfigResolver,
                null
        );
        assertNotNull(factory);
    }
}
