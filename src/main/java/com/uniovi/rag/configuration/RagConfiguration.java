package com.uniovi.rag.configuration;

import com.uniovi.rag.services.analyser.NERQueryAnalyser;
import com.uniovi.rag.services.analyser.QueryAnalyser;
import com.uniovi.rag.services.classifier.EnhancedQueryClassifier;
import com.uniovi.rag.services.classifier.PythonQueryClassifier;
import com.uniovi.rag.services.classifier.QueryClassifier;
import com.uniovi.rag.services.classifier.QueryType;
import com.uniovi.rag.services.document.DocumentService;
import com.uniovi.rag.services.document.MetadataDocumentService;
import com.uniovi.rag.services.document.SimpleDocumentService;
import com.uniovi.rag.services.evaluation.DatasetMinuteEvaluationService;
import com.uniovi.rag.services.evaluation.EvaluationService;
import com.uniovi.rag.services.expand.DocumentStructureExpander;
import com.uniovi.rag.services.expand.QueryExpander;
import com.uniovi.rag.services.query.ProcessQueryService;
import com.uniovi.rag.services.query.QueryService;
import com.uniovi.rag.services.retriever.BasicContextRetriever;
import com.uniovi.rag.services.retriever.ContextRetriever;
import com.uniovi.rag.services.tools.*;
import com.uniovi.rag.services.tools.metadata.*;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.evaluation.RelevancyEvaluator;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class RagConfiguration {

    @Value("${spring.ai.ollama.top-k}")
    private int topK;

    @Value("${spring.ai.ollama.similarity-threshold}")
    private double similarityThreshold;

    @Bean
    public RagToolsConfiguration toolsConfig(Map<QueryType, Tool> tools) {
        return new RagToolsConfiguration(tools);
    }

    @Bean
    public RagFeatureConfiguration featureConfig() {
        return new RagFeatureConfiguration();
    }

    @Bean
    public PgVectorStore pgVectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {
        return new PgVectorStore(jdbcTemplate, embeddingModel);
    }

    @Bean
    public OllamaEmbeddingModel embeddingModel(
            @Value("${spring.ai.ollama.base-url}") String url,
            @Value("${spring.ai.ollama.embedding.model}") String chatModel
    ) {

        return new OllamaEmbeddingModel(
                new OllamaApi(url),
                OllamaOptions.create().withModel(chatModel)
        );
    }

    @Bean
    public ChatModel chatModel(
            @Value("${spring.ai.ollama.base-url}") String url,
            @Value("${spring.ai.ollama.chat.model}") String chatModel
    ) {
        return new OllamaChatModel(
                new OllamaApi(url),
                OllamaOptions.create().withModel(chatModel)
        );
    }

    @Bean
    public ChatClient.Builder chatClientBuilder(
            @Value("${spring.ai.ollama.base-url}") String url,
            @Value("${spring.ai.ollama.chat.model}") String chatModel
    ) {
        return ChatClient.builder(
                chatModel(url, chatModel)
        );
    }

    @Bean
    public ChatClient chatClient(
            @Value("${spring.ai.ollama.base-url}") String url,
            @Value("${spring.ai.ollama.chat.model}") String chatModel
    ) {
        return chatClientBuilder(url, chatModel).build();
    }

    @Bean
    public RelevancyEvaluator relevancyEvaluator(ChatClient.Builder builder) {
        return new RelevancyEvaluator(builder);
    }

    @Bean
    public DocumentService documentService(RagFeatureConfiguration featureConfig, PgVectorStore vectorStore, ChatClient chatClient) {

        if (featureConfig.isMetadataEnabled()) {
            return new MetadataDocumentService(vectorStore, chatClient);
        }

        return new SimpleDocumentService(vectorStore, chatClient);
    }

    @Bean
    public EvaluationService evaluationService(RagFeatureConfiguration featureConfig, ChatClient chatClient,
                                               DocumentService documentService, QueryService queryService) {
        return new DatasetMinuteEvaluationService(featureConfig, chatClient, documentService, queryService);
    }

    @Bean
    public QueryExpander queryExpander(ChatClient chatClient) {
        return new DocumentStructureExpander(chatClient);
    }

    @Bean
    public QueryClassifier queryClassifier(ChatClient chatClient) {
        return new EnhancedQueryClassifier(new PythonQueryClassifier(), chatClient);
    }

    @Bean
    public QueryAnalyser queryAnalyser(ChatClient chatClient) {
        return new NERQueryAnalyser(chatClient);
    }

    @Bean
    public ContextRetriever contextRetriever(PgVectorStore vectorStore, ChatClient chatClient, RagFeatureConfiguration featureConfig) {
        //if (featureConfig.isCacheDocumentsEnabled()) {
        //    return new CachedContextRetriever(vectorStore, chatClient, featureConfig, topK, similarityThreshold);
        //}
        return new BasicContextRetriever(vectorStore, chatClient, topK, similarityThreshold);
    }

    @Bean
    public Map<QueryType, Tool> tools(
            RagFeatureConfiguration featureConfig,
            ContextRetriever contextRetriever,
            ChatClient chatClient
    ) {

        Map<QueryType, Tool> tools = new HashMap<>();
        if (featureConfig.isMetadataEnabled()) {
            tools.putAll(Map.of(
                    QueryType.COUNT_DOCUMENTS, new MetadataCountDocumentsTool(chatClient, contextRetriever),
                    QueryType.FIND_PARAGRAPH, new MetadataFindParagraphTool(chatClient, contextRetriever),
                    QueryType.COUNT_AND_EXPLAIN, new MetadataCountAndExplainTool(chatClient, contextRetriever),
                    QueryType.EXTRACT_ENTITIES, new MetadataExtractEntitiesTool(chatClient, contextRetriever),
                    QueryType.SUMMARIZE_TOPIC, new MetadataSummarizeTopicTool(chatClient, contextRetriever),
                    QueryType.BOOLEAN_QUERY, new MetadataBooleanQueryTool(chatClient, contextRetriever)
            ));

            tools.putAll(Map.of(
                    QueryType.COMPARE, new MetadataCompareTool(chatClient, contextRetriever),
                    QueryType.GET_DURATION, new MetadataGetDurationTool(chatClient, contextRetriever),
                    QueryType.GET_FIELD, new MetadataGetFieldTool(chatClient, contextRetriever),
                    QueryType.FILTER_AND_LIST, new MetadataFilterAndListTool(chatClient, contextRetriever),
                    QueryType.DECISION_EXTRACTION, new MetadataDecisionExtractionTool(chatClient, contextRetriever),
                    QueryType.SUMMARIZE_MEETING, new MetadataSummarizeMeetingTool(chatClient, contextRetriever)
            ));
        } else {
            tools.putAll(Map.of(
                    QueryType.COUNT_DOCUMENTS, new CountDocumentsTool(chatClient, contextRetriever),
                    QueryType.FIND_PARAGRAPH, new FindParagraphTool(chatClient, contextRetriever),
                    QueryType.COUNT_AND_EXPLAIN, new CountAndExplainTool(chatClient, contextRetriever),
                    QueryType.EXTRACT_ENTITIES, new ExtractEntitiesTool(chatClient, contextRetriever),
                    QueryType.SUMMARIZE_TOPIC, new SummarizeTopicTool(chatClient, contextRetriever),
                    QueryType.BOOLEAN_QUERY, new BooleanQueryTool(chatClient, contextRetriever)
            ));
            tools.putAll(Map.of(
                    QueryType.COMPARE, new CompareTool(chatClient, contextRetriever),
                    QueryType.GET_DURATION, new GetDurationTool(chatClient, contextRetriever),
                    QueryType.GET_FIELD, new GetFieldTool(chatClient, contextRetriever),
                    QueryType.FILTER_AND_LIST, new FilterAndListTool(chatClient, contextRetriever),
                    QueryType.DECISION_EXTRACTION, new DecisionExtractionTool(chatClient, contextRetriever),
                    QueryType.SUMMARIZE_MEETING, new SummarizeMeetingTool(chatClient, contextRetriever)
            ));
        }

        return tools;
    }

    /*@Bean
    public AgenticToolsManager agenticToolsManager(
            ChatClient chatClient,
            ContextRetriever contextRetriever
    ) {
        AgenticToolsManager manager = new AgenticToolsManager();
        
        // Registrar las AgenticTools
        manager.registerTool(new AgenticCountDocumentsTool(chatClient, contextRetriever));
        // Aquí puedes agregar más AgenticTools según las necesites
        
        return manager;
    }*/

    @Bean
    public QueryService queryService(
            RagFeatureConfiguration featureConfig,
            RagToolsConfiguration toolsConfig,
            QueryExpander expander,
            QueryAnalyser analyser,
            QueryClassifier classifier,
            ContextRetriever contextRetriever,
            ChatClient chatClient
    ) {
        return new ProcessQueryService(
            featureConfig,
            toolsConfig,
            expander,
            analyser,
            classifier,
            contextRetriever,
            chatClient
        );
    }


}