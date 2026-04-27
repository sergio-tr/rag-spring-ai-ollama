package com.uniovi.rag.service.evaluation;

import com.uniovi.rag.interfaces.rest.support.OllamaConnectivityChecker;
import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.configuration.RagImplementationProperties;
import com.uniovi.rag.service.extraction.DocumentContentExtractor;
import com.uniovi.rag.service.guard.QueryDateExtractor;
import com.uniovi.rag.service.postretrieval.PostRetrievalProcessor;
import com.uniovi.rag.service.ranker.ResponseRanker;
import com.uniovi.rag.service.reasoning.ReasoningStrategy;
import com.uniovi.rag.service.query.QueryService;
import com.uniovi.rag.application.port.ModelCatalogPort;
import com.uniovi.rag.application.service.runtime.ExecutionContextFactory;
import com.uniovi.rag.application.service.runtime.RagExecutionOrchestrator;
import com.uniovi.rag.application.service.runtime.tracepersistence.RuntimeTracePersistenceService;
import com.uniovi.rag.service.config.ChatScopedRagConfigResolver;
import com.uniovi.rag.service.query.ResponseValidator;
import com.uniovi.rag.service.query.SimpleQueryService;
import com.uniovi.rag.tool.metadata.MetadataLlmResponseCacheService;
import com.uniovi.rag.testsupport.ClassifierClientTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;

class EvaluationServiceFactoryTest {

    private EvaluationServiceFactory factory;
    private RagFeatureConfiguration featureConfig;
    private RagImplementationProperties implProps;

    @BeforeEach
    void setUp() {
        ChatClient chatClient = mock(ChatClient.class);
        PgVectorStore vectorStore = mock(PgVectorStore.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ResponseValidator responseValidator = mock(ResponseValidator.class);
        DocumentContentExtractor documentContentExtractor = mock(DocumentContentExtractor.class);
        OllamaConnectivityChecker ollamaConnectivityChecker = mock(OllamaConnectivityChecker.class);
        MetadataLlmResponseCacheService metadataLlmResponseCacheService = mock(MetadataLlmResponseCacheService.class);
        ModelCatalogPort modelCatalogPort = mock(ModelCatalogPort.class);
        ChatScopedRagConfigResolver chatScopedRagConfigResolver = mock(ChatScopedRagConfigResolver.class);
        ExecutionContextFactory executionContextFactory = mock(ExecutionContextFactory.class);
        RagExecutionOrchestrator ragExecutionOrchestrator = mock(RagExecutionOrchestrator.class);
        RuntimeTracePersistenceService runtimeTracePersistenceService = mock(RuntimeTracePersistenceService.class);
        ReasoningStrategy reasoningStrategy = mock(ReasoningStrategy.class);
        ResponseRanker responseRanker = mock(ResponseRanker.class);
        PostRetrievalProcessor postRetrievalProcessor = mock(PostRetrievalProcessor.class);
        QueryDateExtractor queryDateExtractor = mock(QueryDateExtractor.class);
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
        factory = new EvaluationServiceFactory(
                chatClient,
                vectorStore,
                jdbcTemplate,
                settings,
                responseValidator,
                documentContentExtractor,
                ollamaConnectivityChecker,
                metadataLlmResponseCacheService,
                modelCatalogPort,
                chatScopedRagConfigResolver,
                executionContextFactory,
                ragExecutionOrchestrator,
                runtimeTracePersistenceService,
                reasoningStrategy,
                responseRanker,
                postRetrievalProcessor,
                queryDateExtractor,
                false,
                null
        );
        featureConfig = new RagFeatureConfiguration();
        implProps = new RagImplementationProperties();
    }

    @Test
    void createQueryService_simple_returnsSimpleQueryService() {
        implProps.setQueryServiceImpl("simple");
        implProps.setRetrieverImpl("basic");
        implProps.setAnalyserImpl("no-op");

        QueryService service = factory.createQueryService(featureConfig, implProps);

        assertNotNull(service);
        assertTrue(service instanceof SimpleQueryService);
    }

    @Test
    void createQueryService_process_returnsProcessQueryService() {
        implProps.setQueryServiceImpl("process");
        implProps.setRetrieverImpl("basic");
        implProps.setAnalyserImpl("no-op");

        QueryService service = factory.createQueryService(featureConfig, implProps);

        assertNotNull(service);
        assertEquals("com.uniovi.rag.service.query.ProcessQueryService", service.getClass().getName());
    }
}
