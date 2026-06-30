package com.uniovi.rag.configuration;

import com.uniovi.rag.application.service.runtime.ChatGenerationModelSelector;
import com.uniovi.rag.application.service.runtime.ExecutionContextFactory;
import com.uniovi.rag.application.service.runtime.RagExecutionOrchestrator;
import com.uniovi.rag.application.service.runtime.tracepersistence.RuntimeTracePersistenceService;
import com.uniovi.rag.infrastructure.observability.ObservabilitySupport;
import com.uniovi.rag.infrastructure.observability.TracedEvaluationService;
import com.uniovi.rag.application.service.evaluation.judge.EvaluationJudgeLlmExecutor;
import com.uniovi.rag.application.service.llm.LlmErrorComposer;
import com.uniovi.rag.interfaces.rest.support.OllamaConnectivityChecker;
import com.uniovi.rag.application.service.knowledge.document.DocumentService;
import com.uniovi.rag.application.service.evaluation.ReferenceBundleMinuteEvaluationService;
import com.uniovi.rag.application.service.evaluation.EvaluationService;
import com.uniovi.rag.application.service.evaluation.EvaluationServiceFactory;
import com.uniovi.rag.application.service.runtime.execution.QueryExecutionService;
import org.springframework.ai.chat.client.ChatClient.Builder;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.evaluation.RelevancyEvaluator;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;


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
        @Value("${spring.ai.ollama.top-k:80}") int topK,
        @Value("${spring.ai.ollama.similarity-threshold:0.25}") double similarityThreshold,
        @Value("${rag.classifier.service.url:http://localhost:8000}") String classifierServiceUrl,
        @Value("${rag.classifier.model-id:default}") String classifierModelId,
        @Value("${rag.classifier.service.timeout-ms:5000}") int classifierTimeoutMs,
        @Value("${rag.chunk.max-chars:400}") int chunkMaxChars,
        @Value("${rag.expansion.strategy:COT}") String expansionStrategy,
        @Value("${rag.expansion.original-repeat:1}") int expansionOriginalRepeat,
        @Value("${rag.expansion.max-expansion-chars:350}") int expansionMaxExpansionChars,
        @Value("${rag.expansion.max-query-total-chars:512}") int expansionMaxQueryTotalChars,
        @Value("${rag.expansion.max-query-length-for-llm:500}") int expansionMaxQueryLengthForLlm,
        @Value("${rag.expansion.retry-query-length:200}") int expansionRetryQueryLength,
        OllamaConnectivityChecker ollamaConnectivityChecker,
        ExecutionContextFactory executionContextFactory,
        RagExecutionOrchestrator ragExecutionOrchestrator,
        RuntimeTracePersistenceService runtimeTracePersistenceService,
        ChatGenerationModelSelector chatGenerationModelSelector,
        @Value("${knowledge.v2.chat-overlay.enabled:false}") boolean knowledgeChatOverlayEnabled,
        EvaluationJudgeLlmExecutor evaluationJudgeLlmExecutor,
        LlmErrorComposer llmErrorComposer
    ) {
        EvaluationServiceFactory.Settings settings =
                new EvaluationServiceFactory.Settings(
                        topK,
                        similarityThreshold,
                        classifierServiceUrl,
                        classifierModelId,
                        classifierTimeoutMs,
                        chunkMaxChars,
                        expansionStrategy,
                        expansionOriginalRepeat,
                        expansionMaxExpansionChars,
                        expansionMaxQueryTotalChars,
                        expansionMaxQueryLengthForLlm,
                        expansionRetryQueryLength,
                        knowledgeChatOverlayEnabled);
        return new EvaluationServiceFactory(
                chatClient,
                vectorStore,
                jdbcTemplate,
                settings,
                ollamaConnectivityChecker,
                executionContextFactory,
                ragExecutionOrchestrator,
                runtimeTracePersistenceService,
                chatGenerationModelSelector,
                evaluationJudgeLlmExecutor,
                llmErrorComposer);
    }

    @Bean
    public EvaluationService evaluationService(
        RagFeatureConfiguration featureConfig,
        RagImplementationProperties implementationProperties,
        ChatClient chatClient,
        DocumentService documentService,
        QueryExecutionService queryService,
        EvaluationServiceFactory evaluationServiceFactory,
        EvaluationJudgeLlmExecutor evaluationJudgeLlmExecutor,
        @Value("${evaluation.clean-before-load:true}") boolean cleanBeforeLoad,
        @Autowired(required = false) ObservabilitySupport observability
    ) {
        ReferenceBundleMinuteEvaluationService service =
                new ReferenceBundleMinuteEvaluationService(
                        featureConfig,
                        implementationProperties,
                        chatClient,
                        documentService,
                        queryService,
                        cleanBeforeLoad,
                        evaluationJudgeLlmExecutor);
        service.setEvaluationServiceFactory(evaluationServiceFactory);
        if (observability != null) {
            return new TracedEvaluationService(service, observability);
        }
        return service;
    }
}
