package com.uniovi.rag.configuration;

import com.uniovi.rag.model.ExpansionStrategy;
import com.uniovi.rag.service.analyser.MinuteNERQueryAnalyser;
import com.uniovi.rag.service.analyser.NERQueryEnricher;
import com.uniovi.rag.service.analyser.NoOpQueryAnalyser;
import com.uniovi.rag.service.analyser.QueryAnalyser;
import com.uniovi.rag.observability.ObservabilitySupport;
import com.uniovi.rag.observability.TracedDateExistenceGuard;
import com.uniovi.rag.observability.TracedQueryAnalyser;
import com.uniovi.rag.observability.TracedQueryClassifier;
import com.uniovi.rag.observability.TracedQueryExpander;
import com.uniovi.rag.observability.TracedQueryService;
import com.uniovi.rag.observability.TracedReasoningStrategy;
import com.uniovi.rag.observability.TracedResponseRanker;
import com.uniovi.rag.observability.TracedResponseValidator;
import com.uniovi.rag.service.classifier.ClassifierServiceClient;
import com.uniovi.rag.service.classifier.QueryClassifier;
import com.uniovi.rag.service.expand.MinuteDocumentStructureExpander;
import com.uniovi.rag.service.expand.QueryExpander;
import com.uniovi.rag.service.guard.DateExistenceGuard;
import com.uniovi.rag.service.guard.DefaultDateExistenceGuard;
import com.uniovi.rag.service.guard.QueryDateExtractor;
import com.uniovi.rag.service.postretrieval.PostRetrievalProcessor;
import com.uniovi.rag.service.query.LLMResponseValidatorService;
import com.uniovi.rag.service.query.ProcessQueryService;
import com.uniovi.rag.service.query.QueryService;
import com.uniovi.rag.service.query.ResponseValidator;
import com.uniovi.rag.service.query.SimpleProcessQueryService;
import com.uniovi.rag.service.query.SimpleQueryService;
import com.uniovi.rag.service.ranker.FaithfulnessRanker;
import com.uniovi.rag.service.ranker.LLMAsJudgeRanker;
import com.uniovi.rag.service.ranker.ResponseRanker;
import com.uniovi.rag.service.reasoning.COTReasoningStrategy;
import com.uniovi.rag.service.reasoning.PlanAndVerifyReasoningStrategy;
import com.uniovi.rag.service.reasoning.ReasoningStrategy;
import com.uniovi.rag.service.reasoning.SimpleReasoningStrategy;
import com.uniovi.rag.service.retriever.ContextRetriever;
import com.uniovi.rag.tool.MeetingMinutesToolsAdapter;
import com.uniovi.rag.tool.ToolRagService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

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
        String strategy = reasoningProperties.getStrategy() != null ? reasoningProperties.getStrategy().toUpperCase() : "SIMPLE";
        ReasoningStrategy raw;
        switch (strategy) {
            case "COT":
                raw = new COTReasoningStrategy(chatClient);
                break;
            case "PLAN_AND_VERIFY":
                raw = new PlanAndVerifyReasoningStrategy(chatClient);
                break;
            default:
                raw = new SimpleReasoningStrategy();
                break;
        }
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

    @Bean
    public QueryClassifier queryClassifier(
        @Value("${rag.classifier.service.url:http://localhost:8000}") String classifierServiceUrl,
        @Value("${rag.classifier.model-id:default}") String modelId,
        @Value("${rag.classifier.service.timeout-ms:5000}") int timeoutMs,
        @org.springframework.beans.factory.annotation.Autowired(required = false) ObservabilitySupport observability
    ) {
        QueryClassifier raw = new ClassifierServiceClient(classifierServiceUrl, modelId, timeoutMs);
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
            RagFeatureConfiguration featureConfig,
            RagToolsConfiguration toolsConfig,
            QueryExpander expander,
            QueryAnalyser analyser,
            NERQueryEnricher nerQueryEnricher,
            QueryClassifier classifier,
            ContextRetriever retriever,
            ChatClient chatClient,
            DateExistenceGuard dateExistenceGuard,
            MeetingMinutesToolsAdapter meetingMinutesToolsAdapter,
            ReasoningStrategy reasoningStrategy,
            ResponseRanker responseRanker,
            PostRetrievalProcessor postRetrievalProcessor,
            ToolRagService toolRagService,
            ResponseValidator responseValidator,
            QuestionAnswerAdvisor questionAnswerAdvisor,
            RagImplementationProperties implProps,
            @org.springframework.beans.factory.annotation.Autowired(required = false) ObservabilitySupport observability
    ) {
        String impl = implProps.getQueryServiceImpl() != null ? implProps.getQueryServiceImpl().trim().toLowerCase() : "process";
        QueryService raw;
        switch (impl) {
            case "simple":
                raw = new SimpleQueryService(expander, analyser, retriever, chatClient);
                break;
            case "simple-process":
                raw = new SimpleProcessQueryService(featureConfig, toolsConfig, expander, analyser, classifier, retriever, chatClient);
                break;
            default:
                raw = new ProcessQueryService(
                        featureConfig,
                        toolsConfig,
                        expander,
                        analyser,
                        nerQueryEnricher,
                        classifier,
                        retriever,
                        chatClient,
                        dateExistenceGuard,
                        meetingMinutesToolsAdapter,
                        reasoningStrategy,
                        responseRanker,
                        postRetrievalProcessor,
                        toolRagService,
                        responseValidator,
                        questionAnswerAdvisor
                );
                break;
        }
        if (observability != null) {
            return new TracedQueryService(raw, observability);
        }
        return raw;
    }
}
