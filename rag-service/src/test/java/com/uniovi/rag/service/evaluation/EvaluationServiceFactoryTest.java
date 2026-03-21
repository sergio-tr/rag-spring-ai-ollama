package com.uniovi.rag.service.evaluation;

import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.service.extraction.DocumentContentExtractor;
import com.uniovi.rag.service.query.QueryService;
import com.uniovi.rag.service.query.ResponseValidator;
import com.uniovi.rag.service.query.SimpleQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EvaluationServiceFactoryTest {

    private EvaluationServiceFactory factory;
    private RagFeatureConfiguration featureConfig;

    @BeforeEach
    void setUp() {
        ChatClient chatClient = mock(ChatClient.class);
        PgVectorStore vectorStore = mock(PgVectorStore.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        ResponseValidator responseValidator = mock(ResponseValidator.class);
        DocumentContentExtractor documentContentExtractor = mock(DocumentContentExtractor.class);

        factory = new EvaluationServiceFactory(
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
        featureConfig = new RagFeatureConfiguration();
    }

    @Test
    void createQueryService_simple_returnsSimpleQueryService() {
        featureConfig.setQueryServiceImpl("simple");
        featureConfig.setRetrieverImpl("basic");
        featureConfig.setAnalyserImpl("no-op");

        QueryService service = factory.createQueryService(featureConfig);

        assertNotNull(service);
        assertTrue(service instanceof SimpleQueryService);
    }

    @Test
    void createQueryService_process_returnsProcessQueryService() {
        featureConfig.setQueryServiceImpl("process");
        featureConfig.setRetrieverImpl("basic");
        featureConfig.setAnalyserImpl("no-op");

        QueryService service = factory.createQueryService(featureConfig);

        assertNotNull(service);
        assertEquals("com.uniovi.rag.service.query.ProcessQueryService", service.getClass().getName());
    }
}
