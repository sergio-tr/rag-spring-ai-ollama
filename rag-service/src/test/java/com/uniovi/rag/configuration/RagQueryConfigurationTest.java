package com.uniovi.rag.configuration;

import com.uniovi.rag.application.service.runtime.ExecutionContextFactory;
import com.uniovi.rag.application.service.runtime.RagExecutionOrchestrator;
import com.uniovi.rag.application.service.runtime.tracepersistence.RuntimeTracePersistenceService;
import com.uniovi.rag.infrastructure.classifier.ClassifierInferenceMetricsDecorator;
import com.uniovi.rag.infrastructure.classifier.ClassifierServiceClient;
import com.uniovi.rag.infrastructure.classifier.QueryClassifier;
import com.uniovi.rag.infrastructure.observability.ObservabilitySupport;
import com.uniovi.rag.infrastructure.observability.TracedQueryService;
import com.uniovi.rag.interfaces.rest.support.OllamaConnectivityChecker;
import com.uniovi.rag.service.analyser.MinuteNERQueryAnalyser;
import com.uniovi.rag.service.analyser.QueryAnalyser;
import com.uniovi.rag.service.expand.MinuteDocumentStructureExpander;
import com.uniovi.rag.service.expand.QueryExpander;
import com.uniovi.rag.service.guard.DateExistenceGuard;
import com.uniovi.rag.service.guard.DefaultDateExistenceGuard;
import com.uniovi.rag.service.guard.QueryDateExtractor;
import com.uniovi.rag.infrastructure.persistence.KnowledgeDocumentRepository;
import com.uniovi.rag.service.query.ProcessQueryService;
import com.uniovi.rag.service.query.QueryService;
import com.uniovi.rag.service.query.ResponseValidator;
import com.uniovi.rag.service.query.SimpleProcessQueryService;
import com.uniovi.rag.service.query.SimpleQueryService;
import com.uniovi.rag.service.ranker.FaithfulnessRanker;
import com.uniovi.rag.service.ranker.LLMAsJudgeRanker;
import com.uniovi.rag.service.reasoning.ReasoningStrategy;
import com.uniovi.rag.service.reasoning.SelectingReasoningStrategy;
import com.uniovi.rag.service.retriever.ContextRetriever;
import com.uniovi.rag.testsupport.ClassifierClientTestSupport;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.tracing.test.simple.SimpleTracer;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link RagQueryConfiguration}. Bean logic is tested in isolation where possible.
 */
class RagQueryConfigurationTest {

    @Test
    void responseValidatorBean_returnsLLMValidator() {
        RagQueryConfiguration config = new RagQueryConfiguration();
        ResponseValidator validator = config.responseValidator(null);
        assertNotNull(validator);
    }

    @Test
    void reasoningStrategyBean_defaultStrategy_returnsSelectingReasoningStrategy() {
        RagQueryConfiguration config = new RagQueryConfiguration();
        RagReasoningProperties props = new RagReasoningProperties();
        props.setStrategy(null);
        ChatClient chatClient = mock(ChatClient.class);
        ReasoningStrategy strategy = config.reasoningStrategy(props, chatClient, null);
        assertNotNull(strategy);
        assertTrue(strategy instanceof SelectingReasoningStrategy);
    }

    @Test
    void queryClassifierBean_returnsClassifierServiceClient() {
        RagQueryConfiguration config = new RagQueryConfiguration();
        QueryClassifier classifier =
                config.queryClassifier(ClassifierClientTestSupport.defaultBaseUrl(), "default", 5000, null, null, new RestTemplate());
        assertNotNull(classifier);
        assertTrue(classifier instanceof ClassifierServiceClient);
    }

    @Test
    void queryDateExtractorBean_returnsInstance() {
        RagQueryConfiguration config = new RagQueryConfiguration();
        assertNotNull(config.queryDateExtractor());
    }

    @Test
    void questionAnswerAdvisor_buildsWithVectorStore() {
        RagQueryConfiguration config = new RagQueryConfiguration();
        QuestionAnswerAdvisor advisor = config.questionAnswerAdvisor(mock(PgVectorStore.class), 3, 0.5);
        assertNotNull(advisor);
    }

    @Test
    void responseRanker_llmAsJudgeWhenStrategyNull() {
        RagQueryConfiguration config = new RagQueryConfiguration();
        RagRankerProperties props = new RagRankerProperties();
        props.setStrategy(null);
        assertInstanceOf(
                LLMAsJudgeRanker.class,
                config.responseRanker(props, mock(ChatClient.class), null));
    }

    @Test
    void responseRanker_faithfulnessBranchWithoutObservability() {
        RagQueryConfiguration config = new RagQueryConfiguration();
        RagRankerProperties props = new RagRankerProperties();
        props.setStrategy("faithfulness");
        assertInstanceOf(
                FaithfulnessRanker.class,
                config.responseRanker(props, mock(ChatClient.class), null));
    }

    @Test
    void queryExpander_invalidStrategyFallsBackToCot() {
        RagQueryConfiguration config = new RagQueryConfiguration();
        QueryExpander expander =
                config.queryExpander(mock(ChatClient.class), "not-a-strategy", 1, 350, 512, 500, 200, null);
        assertInstanceOf(MinuteDocumentStructureExpander.class, expander);
    }

    @Test
    void classifierRestTemplate_buildsWithRestTemplateBuilder() {
        RagQueryConfiguration config = new RagQueryConfiguration();
        assertNotNull(config.classifierRestTemplate(new RestTemplateBuilder(), 3000));
        assertNotNull(config.classifierRestTemplate(new RestTemplateBuilder(), 0));
    }

    @Test
    void queryClassifier_withMeterRegistryOnly_wrapsMetricsDecorator() {
        RagQueryConfiguration config = new RagQueryConfiguration();
        QueryClassifier qc =
                config.queryClassifier(
                        ClassifierClientTestSupport.defaultBaseUrl(),
                        "mid",
                        1000,
                        null,
                        new SimpleMeterRegistry(),
                        new RestTemplate());
        assertInstanceOf(ClassifierInferenceMetricsDecorator.class, qc);
    }

    @Test
    void queryAnalyser_defaultImplUsesMinuteNer() {
        RagQueryConfiguration config = new RagQueryConfiguration();
        RagImplementationProperties impl = new RagImplementationProperties();
        impl.setAnalyserImpl(null);
        QueryAnalyser analyser = config.queryAnalyser(mock(ChatClient.class), impl, null);
        assertInstanceOf(MinuteNERQueryAnalyser.class, analyser);
    }

    @Test
    void nerCacheKeyGenerator_stringAndNonStringBranches() throws Exception {
        RagQueryConfiguration config = new RagQueryConfiguration();
        KeyGenerator kg = config.nerCacheKeyGenerator();
        Object stringKey = kg.generate(null, null, new Object[] {"  q  "});
        assertTrue(stringKey.toString().startsWith("ner::"));
        Object otherKey = kg.generate(null, null, new Object[] {42});
        assertTrue(otherKey.toString().startsWith("ner::"));
    }

    @Test
    void nerQueryEnricher_returnsInstance() {
        RagQueryConfiguration config = new RagQueryConfiguration();
        assertNotNull(config.nerQueryEnricher(10, 200));
    }

    @Test
    void dateExistenceGuard_withoutObservability() {
        RagQueryConfiguration config = new RagQueryConfiguration();
        DateExistenceGuard guard =
                config.dateExistenceGuard(mock(ContextRetriever.class), new QueryDateExtractor(), null);
        assertInstanceOf(DefaultDateExistenceGuard.class, guard);
    }

    @Test
    void queryService_simpleImplementationBranch() {
        RagQueryConfiguration config = new RagQueryConfiguration();
        RagImplementationProperties impl = new RagImplementationProperties();
        impl.setQueryServiceImpl("simple");
        QueryService qs =
                config.queryService(
                        mock(QueryExpander.class),
                        mock(QueryAnalyser.class),
                        mock(ContextRetriever.class),
                        mock(ChatClient.class),
                        mock(OllamaConnectivityChecker.class),
                        mock(ExecutionContextFactory.class),
                        mock(RagExecutionOrchestrator.class),
                        mock(RuntimeTracePersistenceService.class),
                        mock(KnowledgeDocumentRepository.class),
                        impl,
                        null);
        assertInstanceOf(SimpleQueryService.class, qs);
    }

    @Test
    void queryService_simpleProcessImplementationBranch() {
        RagQueryConfiguration config = new RagQueryConfiguration();
        RagImplementationProperties impl = new RagImplementationProperties();
        impl.setQueryServiceImpl("simple-process");
        QueryService qs =
                config.queryService(
                        mock(QueryExpander.class),
                        mock(QueryAnalyser.class),
                        mock(ContextRetriever.class),
                        mock(ChatClient.class),
                        mock(OllamaConnectivityChecker.class),
                        mock(ExecutionContextFactory.class),
                        mock(RagExecutionOrchestrator.class),
                        mock(RuntimeTracePersistenceService.class),
                        mock(KnowledgeDocumentRepository.class),
                        impl,
                        null);
        assertInstanceOf(SimpleProcessQueryService.class, qs);
    }

    @Test
    void queryService_defaultProcessImplementationBranch() {
        RagQueryConfiguration config = new RagQueryConfiguration();
        RagImplementationProperties impl = new RagImplementationProperties();
        impl.setQueryServiceImpl("process");
        QueryService qs =
                config.queryService(
                        mock(QueryExpander.class),
                        mock(QueryAnalyser.class),
                        mock(ContextRetriever.class),
                        mock(ChatClient.class),
                        mock(OllamaConnectivityChecker.class),
                        mock(ExecutionContextFactory.class),
                        mock(RagExecutionOrchestrator.class),
                        mock(RuntimeTracePersistenceService.class),
                        mock(KnowledgeDocumentRepository.class),
                        impl,
                        null);
        assertInstanceOf(ProcessQueryService.class, qs);
    }

    @Test
    void queryService_withObservability_wrapsTracedQueryService() {
        RagQueryConfiguration config = new RagQueryConfiguration();
        RagImplementationProperties impl = new RagImplementationProperties();
        impl.setQueryServiceImpl("simple");
        ObservabilitySupport obs = new ObservabilitySupport(new SimpleTracer(), new SimpleMeterRegistry());
        QueryService qs =
                config.queryService(
                        mock(QueryExpander.class),
                        mock(QueryAnalyser.class),
                        mock(ContextRetriever.class),
                        mock(ChatClient.class),
                        mock(OllamaConnectivityChecker.class),
                        mock(ExecutionContextFactory.class),
                        mock(RagExecutionOrchestrator.class),
                        mock(RuntimeTracePersistenceService.class),
                        mock(KnowledgeDocumentRepository.class),
                        impl,
                        obs);
        assertInstanceOf(TracedQueryService.class, qs);
    }
}
