package com.uniovi.rag.configuration;

import com.uniovi.rag.model.ExpansionStrategy;
import com.uniovi.rag.services.analyser.MinuteNERQueryAnalyser;
import com.uniovi.rag.services.analyser.NERQueryEnricher;
import com.uniovi.rag.services.analyser.NoOpQueryAnalyser;
import com.uniovi.rag.services.analyser.QueryAnalyser;
import com.uniovi.rag.services.classifier.PythonQueryClassifier;
import com.uniovi.rag.services.classifier.QueryClassifier;
import com.uniovi.rag.services.classifier.QueryType;
import com.uniovi.rag.repository.MinuteDocumentRepository;
import com.uniovi.rag.repository.MinuteDocumentRepositoryImpl;
import com.uniovi.rag.services.document.DocumentService;
import com.uniovi.rag.services.document.MetadataMinuteDocumentService;
import com.uniovi.rag.services.document.SimpleDocumentService;
import com.uniovi.rag.services.evaluation.DatasetMinuteEvaluationService;
import com.uniovi.rag.services.evaluation.EvaluationService;
import com.uniovi.rag.services.evaluation.EvaluationServiceFactory;
import com.uniovi.rag.services.expand.MinuteDocumentStructureExpander;
import com.uniovi.rag.services.expand.QueryExpander;
import com.uniovi.rag.services.guard.DateExistenceGuard;
import com.uniovi.rag.services.guard.DefaultDateExistenceGuard;
import com.uniovi.rag.services.guard.QueryDateExtractor;
import com.uniovi.rag.services.query.ProcessQueryService;
import com.uniovi.rag.services.query.QueryService;
import com.uniovi.rag.services.query.SimpleProcessQueryService;
import com.uniovi.rag.services.query.SimpleQueryService;
import com.uniovi.rag.services.retriever.BasicContextRetriever;
import com.uniovi.rag.services.retriever.ContextRetriever;
import com.uniovi.rag.services.retriever.FilteredContextRetriever;
import com.uniovi.rag.services.retriever.MinuteDocumentContextRetriever;
import com.uniovi.rag.services.postretrieval.DefaultPostRetrievalProcessor;
import com.uniovi.rag.services.postretrieval.PostRetrievalProcessor;
import com.uniovi.rag.services.ranker.FaithfulnessRanker;
import com.uniovi.rag.services.ranker.LLMAsJudgeRanker;
import com.uniovi.rag.services.ranker.ResponseRanker;
import com.uniovi.rag.services.reasoning.COTReasoningStrategy;
import com.uniovi.rag.services.reasoning.PlanAndVerifyReasoningStrategy;
import com.uniovi.rag.services.reasoning.ReasoningStrategy;
import com.uniovi.rag.services.query.LLMResponseValidatorService;
import com.uniovi.rag.services.query.ResponseValidator;
import com.uniovi.rag.services.extraction.DefaultDocumentContentExtractor;
import com.uniovi.rag.services.extraction.DocumentContentExtractor;
import com.uniovi.rag.services.reasoning.SimpleReasoningStrategy;
import com.uniovi.rag.services.tools.*;
import com.uniovi.rag.services.tools.metadata.*;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.evaluation.RelevancyEvaluator;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class RagConfiguration {

    @Bean
    public RagToolsConfiguration toolsConfig(Map<QueryType, Tool> tools) {
        return new RagToolsConfiguration(tools);
    }

    @Bean
    public MeetingMinutesToolsAdapter meetingMinutesToolsAdapter(
            RagToolsConfiguration toolsConfig,
            QueryAnalyser queryAnalyser) {
        return new MeetingMinutesToolsAdapter(toolsConfig, queryAnalyser);
    }

    @Bean
    public ReasoningStrategy reasoningStrategy(RagReasoningProperties reasoningProperties, ChatClient chatClient) {
        String strategy = reasoningProperties.getStrategy() != null ? reasoningProperties.getStrategy().toUpperCase() : "SIMPLE";
        return switch (strategy) {
            case "COT" -> new COTReasoningStrategy(chatClient);
            case "PLAN_AND_VERIFY" -> new PlanAndVerifyReasoningStrategy(chatClient);
            default -> new SimpleReasoningStrategy();
        };
    }

    @Bean
    public ResponseRanker responseRanker(RagRankerProperties rankerProperties, ChatClient chatClient) {
        String strategy = rankerProperties.getStrategy() != null ? rankerProperties.getStrategy().toUpperCase() : "LLM_AS_JUDGE";
        return "FAITHFULNESS".equals(strategy) ? new FaithfulnessRanker(chatClient) : new LLMAsJudgeRanker(chatClient);
    }

    @Bean
    public PostRetrievalProcessor postRetrievalProcessor(@Value("${rag.post-retrieval.top-k:10}") int topK) {
        return new DefaultPostRetrievalProcessor(topK);
    }

    @Bean
    public ResponseValidator responseValidator() {
        return new LLMResponseValidatorService();
    }

    @Bean
    public ToolRagService toolRagService(
            EmbeddingModel embeddingModel,
            @Value("${rag.tool-rag.top-k:5}") int topK) {
        return new ToolRagService(embeddingModel, topK);
    }

    @Bean
    public RagFeatureConfiguration featureConfig() {
        return new RagFeatureConfiguration();
    }

    @Bean
    public PgVectorStore pgVectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {
        return PgVectorStore.builder(jdbcTemplate, embeddingModel).build();
    }

    @Bean
    public int maxChunkSize(@Value("${rag.chunk.max-chars:400}") int chunkMaxChars) {
        if (chunkMaxChars > 0) {
            return chunkMaxChars;
        }
        return 400;
    }

    @Bean
    public boolean cleanBeforeLoad(@Value("${evaluation.clean-before-load:true}") boolean cleanBeforeLoad) {
        return cleanBeforeLoad;
    }

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

    /**
     * Single ChatClient with no tools/advisor to avoid circular dependency.
     * ProcessQueryService applies tools and QuestionAnswerAdvisor at call time via .tools() and .advisors().
     */
    @Bean
    public ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }

    @Bean
    public RelevancyEvaluator relevancyEvaluator(ChatClient.Builder builder) {
        return new RelevancyEvaluator(builder);
    }

    @Bean
    public DocumentService documentService(
        RagFeatureConfiguration featureConfig, 
        PgVectorStore vectorStore, 
        ChatClient chatClient, 
        JdbcTemplate jdbcTemplate,
        MetadataMinuteDocumentService metadataMinuteDocumentService,
        SimpleDocumentService<?> simpleDocumentService
    ) {
        if (featureConfig.isMetadataEnabled()) {
            return metadataMinuteDocumentService;
        }
        return simpleDocumentService;
    }

    @Bean
    public MinuteDocumentRepository minuteDocumentRepository(
            DocumentService documentService,
            MetadataMinuteDocumentService metadataMinuteDocumentService) {
        return new MinuteDocumentRepositoryImpl(documentService, metadataMinuteDocumentService);
    }

    @Bean
    public EvaluationServiceFactory evaluationServiceFactory(
        ChatClient chatClient,
        PgVectorStore vectorStore,
        JdbcTemplate jdbcTemplate,
        EmbeddingModel embeddingModel,
        @Value("${spring.ai.ollama.top-k}") int topK,
        @Value("${spring.ai.ollama.similarity-threshold}") double similarityThreshold,
        @Value("${rag.classifier.python.executable:}") String pythonClassifierExecutable,
        @Value("${rag.classifier.python.script:}") String pythonClassifierScript,
        @Value("${rag.chunk.max-chars:400}") int chunkMaxChars,
        ResponseValidator responseValidator,
        DocumentContentExtractor documentContentExtractor
    ) {
        return new EvaluationServiceFactory(chatClient, vectorStore, jdbcTemplate, embeddingModel, topK, similarityThreshold,
                pythonClassifierExecutable, pythonClassifierScript, chunkMaxChars, responseValidator, documentContentExtractor);
    }

    @Bean
    public EvaluationService evaluationService(RagFeatureConfiguration featureConfig, 
        ChatClient chatClient,
        DocumentService documentService, 
        QueryService queryService,
        EvaluationServiceFactory evaluationServiceFactory,
        @Value("${evaluation.clean-before-load:true}") boolean cleanBeforeLoad
    ) {
        DatasetMinuteEvaluationService service = new DatasetMinuteEvaluationService(featureConfig, chatClient, documentService, queryService, cleanBeforeLoad);
        service.setEvaluationServiceFactory(evaluationServiceFactory);
        return service;
    }

    @Bean
    public QueryExpander queryExpander(
            ChatClient chatClient,
            @Value("${rag.expansion.strategy:COT}") String expansionStrategy,
            @Value("${rag.expansion.original-repeat:1}") int originalRepeat,
            @Value("${rag.expansion.max-expansion-chars:350}") int maxExpansionChars,
            @Value("${rag.expansion.max-query-total-chars:512}") int maxQueryTotalChars
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
            maxQueryTotalChars
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

    /** Content-based cache key for NER to avoid hashCode collisions (see docs/ANALISIS_NER_Y_MEJORAS.md). */
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
    public ContextRetriever retriever(
        PgVectorStore vectorStore,
        ChatClient chatClient,
        RagImplementationProperties implProps,
        @Value("${spring.ai.ollama.top-k}") int topK,
        @Value("${spring.ai.ollama.similarity-threshold}") double similarityThreshold
    ) {
        String impl = implProps.getRetrieverImpl() != null ? implProps.getRetrieverImpl().trim().toLowerCase() : "basic";
        return switch (impl) {
            case "filtered" -> new FilteredContextRetriever(vectorStore, chatClient, topK, similarityThreshold);
            case "minute-document" -> new MinuteDocumentContextRetriever(vectorStore, chatClient, topK, similarityThreshold);
            default -> new BasicContextRetriever(vectorStore, chatClient, topK, similarityThreshold);
        };
    }

    @Bean
    public DocumentContentExtractor documentContentExtractor() {
        return new DefaultDocumentContentExtractor();
    }

    @Bean
    public Map<QueryType, Tool> tools(
            RagFeatureConfiguration featureConfig,
            ContextRetriever retriever,
            ChatClient chatClient,
            DocumentContentExtractor documentContentExtractor
    ) {

        Map<QueryType, Tool> tools = new HashMap<>();
        if (featureConfig.isMetadataEnabled()) {
            tools.putAll(Map.of(
                    QueryType.COUNT_DOCUMENTS, new MetadataCountDocumentsTool(chatClient, retriever, documentContentExtractor),
                    QueryType.FIND_PARAGRAPH, new MetadataFindParagraphTool(chatClient, retriever, documentContentExtractor),
                    QueryType.COUNT_AND_EXPLAIN, new MetadataCountAndExplainTool(chatClient, retriever, documentContentExtractor),
                    QueryType.EXTRACT_ENTITIES, new MetadataExtractEntitiesTool(chatClient, retriever, documentContentExtractor),
                    QueryType.SUMMARIZE_TOPIC, new MetadataSummarizeTopicTool(chatClient, retriever, documentContentExtractor),
                    QueryType.BOOLEAN_QUERY, new MetadataBooleanQueryTool(chatClient, retriever, documentContentExtractor)
            ));

            tools.putAll(Map.of(
                    QueryType.COMPARE, new MetadataCompareTool(chatClient, retriever, documentContentExtractor),
                    QueryType.GET_DURATION, new MetadataGetDurationTool(chatClient, retriever, documentContentExtractor),
                    QueryType.GET_FIELD, new MetadataGetFieldTool(chatClient, retriever, documentContentExtractor),
                    QueryType.FILTER_AND_LIST, new MetadataFilterAndListTool(chatClient, retriever, documentContentExtractor),
                    QueryType.DECISION_EXTRACTION, new MetadataDecisionExtractionTool(chatClient, retriever, documentContentExtractor),
                    QueryType.SUMMARIZE_MEETING, new MetadataSummarizeMeetingTool(chatClient, retriever, documentContentExtractor)
            ));
        } else {
            tools.putAll(Map.of(
                    QueryType.COUNT_DOCUMENTS, new CountDocumentsTool(chatClient, retriever, documentContentExtractor),
                    QueryType.FIND_PARAGRAPH, new FindParagraphTool(chatClient, retriever, documentContentExtractor),
                    QueryType.COUNT_AND_EXPLAIN, new CountAndExplainTool(chatClient, retriever, documentContentExtractor),
                    QueryType.EXTRACT_ENTITIES, new ExtractEntitiesTool(chatClient, retriever, documentContentExtractor),
                    QueryType.SUMMARIZE_TOPIC, new SummarizeTopicTool(chatClient, retriever, documentContentExtractor),
                    QueryType.BOOLEAN_QUERY, new BooleanQueryTool(chatClient, retriever, documentContentExtractor)
            ));
            tools.putAll(Map.of(
                    QueryType.COMPARE, new CompareTool(chatClient, retriever, documentContentExtractor),
                    QueryType.GET_DURATION, new GetDurationTool(chatClient, retriever, documentContentExtractor),
                    QueryType.GET_FIELD, new GetFieldTool(chatClient, retriever, documentContentExtractor),
                    QueryType.FILTER_AND_LIST, new FilterAndListTool(chatClient, retriever, documentContentExtractor),
                    QueryType.DECISION_EXTRACTION, new DecisionExtractionTool(chatClient, retriever, documentContentExtractor),
                    QueryType.SUMMARIZE_MEETING, new SummarizeMeetingTool(chatClient, retriever, documentContentExtractor)
            ));
        }

        return tools;
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
        return switch (impl) {
            case "simple" -> new SimpleQueryService(expander, analyser, retriever, chatClient);
            case "simple-process" -> new SimpleProcessQueryService(featureConfig, toolsConfig, expander, analyser, classifier, retriever, chatClient);
            default -> new ProcessQueryService(
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
        };
    }

}