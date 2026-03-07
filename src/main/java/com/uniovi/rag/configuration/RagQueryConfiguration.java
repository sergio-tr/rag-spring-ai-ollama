package com.uniovi.rag.configuration;

import com.uniovi.rag.model.ExpansionStrategy;
import com.uniovi.rag.service.analyser.MinuteNERQueryAnalyser;
import com.uniovi.rag.service.analyser.NERQueryEnricher;
import com.uniovi.rag.service.analyser.NoOpQueryAnalyser;
import com.uniovi.rag.service.analyser.QueryAnalyser;
import com.uniovi.rag.service.classifier.PythonQueryClassifier;
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
    public ResponseValidator responseValidator() {
        return new LLMResponseValidatorService();
    }

    @Bean
    public ReasoningStrategy reasoningStrategy(RagReasoningProperties reasoningProperties, ChatClient chatClient) {
        String strategy = reasoningProperties.getStrategy() != null ? reasoningProperties.getStrategy().toUpperCase() : "SIMPLE";
        switch (strategy) {
            case "COT":
                return new COTReasoningStrategy(chatClient);
            case "PLAN_AND_VERIFY":
                return new PlanAndVerifyReasoningStrategy(chatClient);
            default:
                return new SimpleReasoningStrategy();
        }
    }

    @Bean
    public ResponseRanker responseRanker(RagRankerProperties rankerProperties, ChatClient chatClient) {
        String strategy = rankerProperties.getStrategy() != null ? rankerProperties.getStrategy().toUpperCase() : "LLM_AS_JUDGE";
        return "FAITHFULNESS".equals(strategy) ? new FaithfulnessRanker(chatClient) : new LLMAsJudgeRanker(chatClient);
    }

    @Bean
    public QueryExpander queryExpander(
            ChatClient chatClient,
            @Value("${rag.expansion.strategy:COT}") String expansionStrategy,
            @Value("${rag.expansion.original-repeat:1}") int originalRepeat,
            @Value("${rag.expansion.max-expansion-chars:350}") int maxExpansionChars,
            @Value("${rag.expansion.max-query-total-chars:512}") int maxQueryTotalChars,
            @Value("${rag.expansion.max-query-length-for-llm:500}") int maxQueryLengthForLlm,
            @Value("${rag.expansion.retry-query-length:200}") int retryQueryLength
    ) {
        ExpansionStrategy strategy;
        try {
            strategy = ExpansionStrategy.valueOf(expansionStrategy.toUpperCase());
        } catch (Exception e) {
            strategy = ExpansionStrategy.COT;
        }
        return new MinuteDocumentStructureExpander(
            chatClient,
            strategy,
            originalRepeat,
            maxExpansionChars,
            maxQueryTotalChars,
            maxQueryLengthForLlm,
            retryQueryLength
        );
    }

    @Bean
    public QueryClassifier queryClassifier(
        ChatClient chatClient,
        @Value("${rag.classifier.python.executable:}") String pythonClassifierExecutable,
        @Value("${rag.classifier.python.script:}") String pythonClassifierScript
    ) {
        return new PythonQueryClassifier(pythonClassifierExecutable, pythonClassifierScript);
    }

    @Bean
    public QueryAnalyser queryAnalyser(ChatClient chatClient, RagImplementationProperties implProps) {
        String impl = implProps.getAnalyserImpl() != null ? implProps.getAnalyserImpl().trim().toLowerCase() : "minute-ner";
        if ("no-op".equals(impl)) {
            return new NoOpQueryAnalyser();
        }
        return new MinuteNERQueryAnalyser(chatClient);
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
    public DateExistenceGuard dateExistenceGuard(ContextRetriever retriever, QueryDateExtractor queryDateExtractor) {
        return new DefaultDateExistenceGuard(retriever, queryDateExtractor);
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
            RagImplementationProperties implProps
    ) {
        String impl = implProps.getQueryServiceImpl() != null ? implProps.getQueryServiceImpl().trim().toLowerCase() : "process";
        switch (impl) {
            case "simple":
                return new SimpleQueryService(expander, analyser, retriever, chatClient);
            case "simple-process":
                return new SimpleProcessQueryService(featureConfig, toolsConfig, expander, analyser, classifier, retriever, chatClient);
            default:
                return new ProcessQueryService(
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
        }
    }
}
