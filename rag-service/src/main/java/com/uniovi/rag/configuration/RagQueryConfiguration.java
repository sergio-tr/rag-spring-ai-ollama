package com.uniovi.rag.configuration;

import com.uniovi.rag.domain.model.ExpansionStrategy;
import com.uniovi.rag.service.analyser.MinuteNERQueryAnalyser;
import com.uniovi.rag.service.analyser.NERQueryEnricher;
import com.uniovi.rag.service.analyser.NoOpQueryAnalyser;
import com.uniovi.rag.service.analyser.QueryAnalyser;
import com.uniovi.rag.infrastructure.observability.ObservabilitySupport;
import com.uniovi.rag.infrastructure.observability.TracedDateExistenceGuard;
import com.uniovi.rag.infrastructure.observability.TracedQueryAnalyser;
import com.uniovi.rag.infrastructure.observability.TracedQueryClassifier;
import com.uniovi.rag.infrastructure.observability.TracedQueryExpander;
import com.uniovi.rag.infrastructure.observability.TracedQueryService;
import io.micrometer.tracing.Tracer;
import com.uniovi.rag.infrastructure.observability.TracedReasoningStrategy;
import com.uniovi.rag.infrastructure.observability.TracedResponseRanker;
import com.uniovi.rag.infrastructure.observability.TracedResponseValidator;
import com.uniovi.rag.infrastructure.classifier.ClassifierInferenceMetricsDecorator;
import com.uniovi.rag.infrastructure.classifier.ClassifierServiceClient;
import com.uniovi.rag.infrastructure.classifier.QueryClassifier;
import io.micrometer.core.instrument.MeterRegistry;
import com.uniovi.rag.service.expand.MinuteDocumentStructureExpander;
import com.uniovi.rag.service.expand.QueryExpander;
import com.uniovi.rag.service.guard.DateExistenceGuard;
import com.uniovi.rag.service.guard.DefaultDateExistenceGuard;
import com.uniovi.rag.service.guard.QueryDateExtractor;
import com.uniovi.rag.service.postretrieval.PostRetrievalProcessor;
import com.uniovi.rag.interfaces.rest.support.OllamaConnectivityChecker;
import com.uniovi.rag.service.query.LLMResponseValidatorService;
import com.uniovi.rag.application.port.ModelCatalogPort;
import com.uniovi.rag.application.service.runtime.ExecutionContextFactory;
import com.uniovi.rag.application.service.runtime.RagExecutionOrchestrator;
import com.uniovi.rag.application.service.runtime.tracepersistence.RuntimeTracePersistenceService;
import com.uniovi.rag.service.query.ProcessQueryService;
import com.uniovi.rag.service.query.QueryService;
import com.uniovi.rag.service.query.ResponseValidator;
import com.uniovi.rag.service.query.SimpleProcessQueryService;
import com.uniovi.rag.service.query.SimpleQueryService;
import com.uniovi.rag.service.ranker.FaithfulnessRanker;
import com.uniovi.rag.service.ranker.LLMAsJudgeRanker;
import com.uniovi.rag.service.ranker.ResponseRanker;
import com.uniovi.rag.service.reasoning.ReasoningStrategy;
import com.uniovi.rag.service.reasoning.SelectingReasoningStrategy;
import com.uniovi.rag.service.retriever.ContextRetriever;
import com.uniovi.rag.service.retriever.NaiveCorpusContextService;
import com.uniovi.rag.tool.MeetingMinutesToolsAdapter;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;

@Configuration
public class RagQueryConfiguration {

    @Bean
    public QuestionAnswerAdvisor questionAnswerAdvisor(
            PgVectorStore vectorStore,
            @Value("${spring.ai.ollama.top-k:10}") int topK,
            @Value("${spring.ai.ollama.similarity-threshold:0.7}") double similarityThreshold
    ) {
        return QuestionAnswerAdvisor.builder(vectorStore)
                .searchRequest(SearchRequest.builder()
                        .topK(topK)
                        .similarityThreshold(similarityThreshold)
                        .build())
                .build();
    }

    @Bean
    public ResponseValidator responseValidator(
            @org.springframework.beans.factory.annotation.Autowired(required = false) ObservabilitySupport observability
    ) {
        ResponseValidator raw = new LLMResponseValidatorService();
        if (observability != null) {
            return new TracedResponseValidator(raw, observability);
        }
        return raw;
    }

    @Bean
    public ReasoningStrategy reasoningStrategy(
            RagReasoningProperties reasoningProperties,
            ChatClient chatClient,
            @org.springframework.beans.factory.annotation.Autowired(required = false) ObservabilitySupport observability
    ) {
        ReasoningStrategy raw = new SelectingReasoningStrategy(chatClient, reasoningProperties);
        if (observability != null) {
            return new TracedReasoningStrategy(raw, observability);
        }
        return raw;
    }

    @Bean
    public ResponseRanker responseRanker(
            RagRankerProperties rankerProperties,
            ChatClient chatClient,
            @org.springframework.beans.factory.annotation.Autowired(required = false) ObservabilitySupport observability
    ) {
        String strategy = rankerProperties.getStrategy() != null ? rankerProperties.getStrategy().toUpperCase() : "LLM_AS_JUDGE";
        ResponseRanker raw = "FAITHFULNESS".equals(strategy)
                ? new FaithfulnessRanker(chatClient)
                : new LLMAsJudgeRanker(chatClient);
        if (observability != null) {
            return new TracedResponseRanker(raw, observability);
        }
        return raw;
    }

    @Bean
    public QueryExpander queryExpander(
            ChatClient chatClient,
            @Value("${rag.expansion.strategy:COT}") String expansionStrategy,
            @Value("${rag.expansion.original-repeat:1}") int originalRepeat,
            @Value("${rag.expansion.max-expansion-chars:350}") int maxExpansionChars,
            @Value("${rag.expansion.max-query-total-chars:512}") int maxQueryTotalChars,
            @Value("${rag.expansion.max-query-length-for-llm:500}") int maxQueryLengthForLlm,
            @Value("${rag.expansion.retry-query-length:200}") int retryQueryLength,
            @org.springframework.beans.factory.annotation.Autowired(required = false) ObservabilitySupport observability
    ) {
        ExpansionStrategy strategy;
        try {
            strategy = ExpansionStrategy.valueOf(expansionStrategy.toUpperCase());
        } catch (Exception e) {
            strategy = ExpansionStrategy.COT;
        }
        QueryExpander raw = new MinuteDocumentStructureExpander(
            chatClient,
            strategy,
            originalRepeat,
            maxExpansionChars,
            maxQueryTotalChars,
            maxQueryLengthForLlm,
            retryQueryLength
        );
        if (observability != null) {
            return new TracedQueryExpander(raw, observability);
        }
        return raw;
    }

    /**
     * Shared {@link RestTemplate} for classifier HTTP calls. Built via {@link RestTemplateBuilder} so
     * Micrometer tracing injects W3C propagation on outbound requests (profile {@code infra}).
     */
    @Bean(name = "classifierRestTemplate")
    public RestTemplate classifierRestTemplate(
            RestTemplateBuilder restTemplateBuilder,
            @Value("${rag.classifier.service.timeout-ms:5000}") int timeoutMs) {
        int t = timeoutMs > 0 ? timeoutMs : 5000;
        return restTemplateBuilder
                .connectTimeout(Duration.ofMillis(t))
                .readTimeout(Duration.ofMillis(t))
                .build();
    }

    @Bean
    public QueryClassifier queryClassifier(
        @Value("${rag.classifier.service.url:http://localhost:8000}") String classifierServiceUrl,
        @Value("${rag.classifier.model-id:default}") String modelId,
        @Value("${rag.classifier.service.timeout-ms:5000}") int timeoutMs,
        @org.springframework.beans.factory.annotation.Autowired(required = false) ObservabilitySupport observability,
        @org.springframework.beans.factory.annotation.Autowired(required = false) MeterRegistry meterRegistry,
        @Qualifier("classifierRestTemplate") RestTemplate classifierRestTemplate
    ) {
        QueryClassifier raw =
                new ClassifierServiceClient(classifierServiceUrl, modelId, timeoutMs, classifierRestTemplate);
        if (meterRegistry != null) {
            raw = new ClassifierInferenceMetricsDecorator(raw, meterRegistry);
        }
        if (observability != null) {
            return new TracedQueryClassifier(raw, observability);
        }
        return raw;
    }

    @Bean
    public QueryAnalyser queryAnalyser(
            ChatClient chatClient,
            RagImplementationProperties implProps,
            @org.springframework.beans.factory.annotation.Autowired(required = false) ObservabilitySupport observability
    ) {
        String impl = implProps.getAnalyserImpl() != null ? implProps.getAnalyserImpl().trim().toLowerCase() : "minute-ner";
        QueryAnalyser raw = "no-op".equals(impl) ? new NoOpQueryAnalyser() : new MinuteNERQueryAnalyser(chatClient);
        if (observability != null) {
            return new TracedQueryAnalyser(raw, observability);
        }
        return raw;
    }

    @Bean(name = "nerCacheKeyGenerator")
    public KeyGenerator nerCacheKeyGenerator() {
        return (target, method, params) -> {
            if (params.length > 0 && params[0] instanceof String q) {
                try {
                    MessageDigest md = MessageDigest.getInstance("SHA-256");
                    byte[] hash = md.digest(q.trim().getBytes(StandardCharsets.UTF_8));
                    StringBuilder hex = new StringBuilder();
                    for (int i = 0; i < Math.min(8, hash.length); i++) {
                        hex.append(String.format("%02x", hash[i]));
                    }
                    return "ner::" + hex;
                } catch (Exception e) {
                    return "ner::" + q.hashCode();
                }
            }
            return "ner::" + java.util.Arrays.hashCode(params);
        };
    }

    @Bean
    public NERQueryEnricher nerQueryEnricher(
            @Value("${rag.ner.enrichment.max-extra-chars:80}") int maxExtraChars,
            @Value("${rag.ner.enrichment.max-total-chars:512}") int maxTotalChars
    ) {
        return new NERQueryEnricher(maxExtraChars, maxTotalChars);
    }

    @Bean
    public QueryDateExtractor queryDateExtractor() {
        return new QueryDateExtractor();
    }

    @Bean
    public DateExistenceGuard dateExistenceGuard(
            ContextRetriever retriever,
            QueryDateExtractor queryDateExtractor,
            @org.springframework.beans.factory.annotation.Autowired(required = false) ObservabilitySupport observability
    ) {
        DateExistenceGuard raw = new DefaultDateExistenceGuard(retriever, queryDateExtractor);
        if (observability != null) {
            return new TracedDateExistenceGuard(raw, observability);
        }
        return raw;
    }

    @Bean
    public QueryService queryService(
            QueryExpander expander,
            QueryAnalyser analyser,
            ContextRetriever retriever,
            ChatClient chatClient,
            OllamaConnectivityChecker ollamaConnectivityChecker,
            ExecutionContextFactory executionContextFactory,
            RagExecutionOrchestrator ragExecutionOrchestrator,
            RuntimeTracePersistenceService runtimeTracePersistenceService,
            RagImplementationProperties implProps,
            @org.springframework.beans.factory.annotation.Autowired(required = false) ObservabilitySupport observability
    ) {
        String impl = implProps.getQueryServiceImpl() != null ? implProps.getQueryServiceImpl().trim().toLowerCase() : "process";
        QueryService raw;
        switch (impl) {
            case "simple":
                raw = new SimpleQueryService(expander, analyser, retriever, chatClient, ollamaConnectivityChecker);
                break;
            case "simple-process":
                raw =
                        new SimpleProcessQueryService(
                                executionContextFactory,
                                ragExecutionOrchestrator,
                                runtimeTracePersistenceService,
                                ollamaConnectivityChecker);
                break;
            default:
                raw = new ProcessQueryService(
                        executionContextFactory,
                        ragExecutionOrchestrator,
                        runtimeTracePersistenceService,
                        chatClient,
                        ollamaConnectivityChecker);
                break;
        }
        if (observability != null) {
            return new TracedQueryService(raw, observability);
        }
        return raw;
    }
}
