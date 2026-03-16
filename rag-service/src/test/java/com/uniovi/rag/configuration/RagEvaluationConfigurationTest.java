package com.uniovi.rag.configuration;

import com.uniovi.rag.service.evaluation.EvaluationServiceFactory;
import com.uniovi.rag.service.extraction.DocumentContentExtractor;
import com.uniovi.rag.service.query.ResponseValidator;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;
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
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        ResponseValidator responseValidator = mock(ResponseValidator.class);
        DocumentContentExtractor documentContentExtractor = mock(DocumentContentExtractor.class);

        EvaluationServiceFactory factory = config.evaluationServiceFactory(
                chatClient,
                vectorStore,
                jdbcTemplate,
                embeddingModel,
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
                200
        );
        assertNotNull(factory);
    }
}
