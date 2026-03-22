package com.uniovi.rag.configuration;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.Builder;
import org.springframework.ai.evaluation.RelevancyEvaluator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.jdbc.core.JdbcTemplate;

import com.uniovi.rag.observability.ObservabilitySupport;
import com.uniovi.rag.observability.TracedEvaluationService;
import com.uniovi.rag.api.OllamaConnectivityChecker;
import com.uniovi.rag.service.document.DocumentService;
import com.uniovi.rag.service.evaluation.DatasetMinuteEvaluationService;
import com.uniovi.rag.service.evaluation.EvaluationService;
import com.uniovi.rag.service.evaluation.EvaluationServiceFactory;
import com.uniovi.rag.service.extraction.DocumentContentExtractor;
import com.uniovi.rag.service.query.QueryService;
import com.uniovi.rag.service.query.ResponseValidator;


@Configuration
public class RagEvaluationConfiguration {

    @Bean
    public RelevancyEvaluator relevancyEvaluator(Builder builder) {
        return new RelevancyEvaluator(builder);
    }

    @Bean
    public EvaluationServiceFactory evaluationServiceFactory(
        ChatClient chatClient,
        PgVectorStore vectorStore,
        JdbcTemplate jdbcTemplate,
        @Value("${spring.ai.ollama.top-k}") int topK,
        @Value("${spring.ai.ollama.similarity-threshold}") double similarityThreshold,
        @Value("${rag.classifier.service.url:http://localhost:8000}") String classifierServiceUrl,
        @Value("${rag.classifier.model-id:default}") String classifierModelId,
        @Value("${rag.classifier.service.timeout-ms:5000}") int classifierTimeoutMs,
        @Value("${rag.chunk.max-chars:400}") int chunkMaxChars,
        ResponseValidator responseValidator,
        DocumentContentExtractor documentContentExtractor,
        @Value("${rag.expansion.strategy:COT}") String expansionStrategy,
        @Value("${rag.expansion.original-repeat:1}") int expansionOriginalRepeat,
        @Value("${rag.expansion.max-expansion-chars:350}") int expansionMaxExpansionChars,
        @Value("${rag.expansion.max-query-total-chars:512}") int expansionMaxQueryTotalChars,
        @Value("${rag.expansion.max-query-length-for-llm:500}") int expansionMaxQueryLengthForLlm,
        @Value("${rag.expansion.retry-query-length:200}") int expansionRetryQueryLength,
        OllamaConnectivityChecker ollamaConnectivityChecker
    ) {
        return new EvaluationServiceFactory(chatClient, vectorStore, jdbcTemplate, topK, similarityThreshold,
                classifierServiceUrl, classifierModelId, classifierTimeoutMs, chunkMaxChars, responseValidator, documentContentExtractor,
                expansionStrategy, expansionOriginalRepeat, expansionMaxExpansionChars, expansionMaxQueryTotalChars,
                expansionMaxQueryLengthForLlm, expansionRetryQueryLength, ollamaConnectivityChecker);
    }

    @Bean
    public EvaluationService evaluationService(
        RagFeatureConfiguration featureConfig,
        RagImplementationProperties implementationProperties,
        ChatClient chatClient,
        DocumentService documentService,
        QueryService queryService,
        EvaluationServiceFactory evaluationServiceFactory,
        @Value("${evaluation.clean-before-load:true}") boolean cleanBeforeLoad,
        @org.springframework.beans.factory.annotation.Autowired(required = false) ObservabilitySupport observability
    ) {
        DatasetMinuteEvaluationService service = new DatasetMinuteEvaluationService(
                featureConfig, implementationProperties, chatClient, documentService, queryService, cleanBeforeLoad);
        service.setEvaluationServiceFactory(evaluationServiceFactory);
        if (observability != null) {
            return new TracedEvaluationService(service, observability);
        }
        return service;
    }
}
