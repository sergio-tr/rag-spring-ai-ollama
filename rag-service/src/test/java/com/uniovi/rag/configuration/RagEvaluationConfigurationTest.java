package com.uniovi.rag.configuration;

import com.uniovi.rag.application.service.runtime.ChatGenerationModelSelector;
import com.uniovi.rag.interfaces.rest.support.OllamaConnectivityChecker;
import com.uniovi.rag.application.service.runtime.ExecutionContextFactory;
import com.uniovi.rag.application.service.runtime.RagExecutionOrchestrator;
import com.uniovi.rag.application.service.runtime.tracepersistence.RuntimeTracePersistenceService;
import com.uniovi.rag.application.service.evaluation.EvaluationServiceFactory;
import com.uniovi.rag.application.service.evaluation.judge.EvaluationJudgeLlmExecutor;
import com.uniovi.rag.application.service.llm.LlmErrorComposer;
import com.uniovi.rag.testsupport.ClassifierClientTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertNotNull;
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
        OllamaConnectivityChecker ollamaConnectivityChecker = mock(OllamaConnectivityChecker.class);
        ExecutionContextFactory executionContextFactory = mock(ExecutionContextFactory.class);
        RagExecutionOrchestrator ragExecutionOrchestrator = mock(RagExecutionOrchestrator.class);
        RuntimeTracePersistenceService runtimeTracePersistenceService = mock(RuntimeTracePersistenceService.class);
        doNothing().when(ollamaConnectivityChecker).prepareForQuery(any());

        EvaluationServiceFactory factory = config.evaluationServiceFactory(
                chatClient,
                vectorStore,
                jdbcTemplate,
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
                ollamaConnectivityChecker,
                executionContextFactory,
                ragExecutionOrchestrator,
                runtimeTracePersistenceService,
                mock(ChatGenerationModelSelector.class),
                false,
                mock(EvaluationJudgeLlmExecutor.class),
                mock(LlmErrorComposer.class));

        assertNotNull(factory);
    }
}
