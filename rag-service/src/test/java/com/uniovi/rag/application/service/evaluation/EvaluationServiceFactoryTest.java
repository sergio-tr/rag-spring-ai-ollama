package com.uniovi.rag.application.service.evaluation;

import com.uniovi.rag.application.service.runtime.ChatGenerationModelSelector;
import com.uniovi.rag.application.service.runtime.ExecutionContextFactory;
import com.uniovi.rag.application.service.runtime.RagExecutionOrchestrator;
import com.uniovi.rag.application.service.runtime.execution.QueryExecutionService;
import com.uniovi.rag.application.service.runtime.execution.RuntimeQueryExecutionService;
import com.uniovi.rag.application.service.runtime.tracepersistence.RuntimeTracePersistenceService;
import com.uniovi.rag.configuration.RagImplementationProperties;
import com.uniovi.rag.interfaces.rest.support.OllamaConnectivityChecker;
import com.uniovi.rag.testsupport.ClassifierClientTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;

class EvaluationServiceFactoryTest {

    private EvaluationServiceFactory factory;
    private RagImplementationProperties implProps;

    @BeforeEach
    void setUp() {
        ChatClient chatClient = mock(ChatClient.class);
        PgVectorStore vectorStore = mock(PgVectorStore.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        OllamaConnectivityChecker ollamaConnectivityChecker = mock(OllamaConnectivityChecker.class);
        ExecutionContextFactory executionContextFactory = mock(ExecutionContextFactory.class);
        RagExecutionOrchestrator ragExecutionOrchestrator = mock(RagExecutionOrchestrator.class);
        RuntimeTracePersistenceService runtimeTracePersistenceService = mock(RuntimeTracePersistenceService.class);
        doNothing().when(ollamaConnectivityChecker).prepareForQuery(any());

        EvaluationServiceFactory.Settings settings =
                new EvaluationServiceFactory.Settings(
                        10,
                        0.7,
                        ClassifierClientTestSupport.defaultBaseUrl(),
                        "default",
                        5000,
                        400,
                        "COT",
                        1,
                        350,
                        512,
                        500,
                        200,
                        false);
        ChatGenerationModelSelector chatGenerationModelSelector = mock(ChatGenerationModelSelector.class);
        factory =
                new EvaluationServiceFactory(
                        chatClient,
                        vectorStore,
                        jdbcTemplate,
                        settings,
                        ollamaConnectivityChecker,
                        executionContextFactory,
                        ragExecutionOrchestrator,
                        runtimeTracePersistenceService,
                        chatGenerationModelSelector);
        implProps = new RagImplementationProperties();
    }

    @Test
    void createQueryService_alwaysReturnsOrchestratedRuntimeQueryExecutionService() {
        implProps.setQueryServiceImpl("simple");
        QueryExecutionService service = factory.createQueryService(implProps);
        assertNotNull(service);
        assertTrue(service instanceof RuntimeQueryExecutionService);

        implProps.setQueryServiceImpl("process");
        assertTrue(factory.createQueryService(implProps) instanceof RuntimeQueryExecutionService);
    }
}
