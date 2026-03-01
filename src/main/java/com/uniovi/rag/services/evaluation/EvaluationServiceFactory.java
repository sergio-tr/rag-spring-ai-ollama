package com.uniovi.rag.services.evaluation;

import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.configuration.RagToolsConfiguration;
import com.uniovi.rag.model.Minute;
import com.uniovi.rag.services.analyser.MinuteNERQueryAnalyser;
import com.uniovi.rag.services.analyser.QueryAnalyser;
import com.uniovi.rag.services.classifier.PythonQueryClassifier;
import com.uniovi.rag.services.classifier.QueryClassifier;
import com.uniovi.rag.services.classifier.QueryType;
import com.uniovi.rag.services.document.DocumentService;
import com.uniovi.rag.services.document.MetadataMinuteDocumentService;
import com.uniovi.rag.services.document.SimpleDocumentService;
import com.uniovi.rag.services.expand.MinuteDocumentStructureExpander;
import com.uniovi.rag.services.expand.QueryExpander;
import com.uniovi.rag.services.guard.DateExistenceGuard;
import com.uniovi.rag.services.guard.QueryDateExtractor;
import com.uniovi.rag.services.query.ProcessQueryService;
import com.uniovi.rag.services.query.QueryService;
import com.uniovi.rag.services.retriever.BasicContextRetriever;
import com.uniovi.rag.services.retriever.ContextRetriever;
import com.uniovi.rag.services.tools.Tool;
import com.uniovi.rag.services.tools.*;
import com.uniovi.rag.services.tools.metadata.*;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Factory class for creating evaluation services with custom configurations.
 * This allows testing different configuration combinations without modifying the main Spring beans.
 */
public class EvaluationServiceFactory {

    private final ChatClient chatClient;
    private final PgVectorStore vectorStore;
    private final JdbcTemplate jdbcTemplate;
    private final int topK;
    private final double similarityThreshold;
    private final String pythonClassifierExecutable;
    private final String pythonClassifierScript;
    private final int chunkMaxChars;

    public EvaluationServiceFactory(
        ChatClient chatClient, 
        PgVectorStore vectorStore, 
        JdbcTemplate jdbcTemplate,
        int topK,
        double similarityThreshold,
        String pythonClassifierExecutable,
        String pythonClassifierScript,
        int chunkMaxChars
    ) {
        this.chatClient = chatClient;
        this.vectorStore = vectorStore;
        this.jdbcTemplate = jdbcTemplate;
        this.topK = topK;
        this.similarityThreshold = similarityThreshold;
        this.pythonClassifierExecutable = pythonClassifierExecutable != null ? pythonClassifierExecutable : "";
        this.pythonClassifierScript = pythonClassifierScript != null ? pythonClassifierScript : "";
        this.chunkMaxChars = chunkMaxChars > 0 ? chunkMaxChars : 400;
    }

    /**
     * Creates a QueryService with a custom configuration.
     */
    public QueryService createQueryService(RagFeatureConfiguration featureConfig) {
        QueryExpander expander = new MinuteDocumentStructureExpander(chatClient);
        QueryAnalyser analyser = new MinuteNERQueryAnalyser(chatClient);
        QueryClassifier classifier = new PythonQueryClassifier(pythonClassifierExecutable, pythonClassifierScript);
        ContextRetriever retriever = new BasicContextRetriever(vectorStore, chatClient, topK, similarityThreshold);
        RagToolsConfiguration toolsConfig = new RagToolsConfiguration(createTools(featureConfig, retriever));
        QueryDateExtractor queryDateExtractor = new QueryDateExtractor();
        DateExistenceGuard dateExistenceGuard = new DateExistenceGuard(retriever, queryDateExtractor);

        return new ProcessQueryService(
                featureConfig,
                toolsConfig,
                expander,
                analyser,
                classifier,
                retriever,
                chatClient,
                dateExistenceGuard
        );
    }

    /**
     * Creates a DocumentService with a custom configuration.
     */
    public DocumentService createDocumentService(RagFeatureConfiguration featureConfig) {
        if (featureConfig.isMetadataEnabled()) {
            return new MetadataMinuteDocumentService(vectorStore, chatClient, jdbcTemplate, chunkMaxChars);
        }
        return new SimpleDocumentService<Minute>(vectorStore, chatClient, jdbcTemplate, chunkMaxChars);
    }

    /**
     * Creates an EvaluationService with a custom configuration.
     */
    public EvaluationService createEvaluationService(RagFeatureConfiguration featureConfig, boolean cleanBeforeLoad) {
        DocumentService documentService = createDocumentService(featureConfig);
        QueryService queryService = createQueryService(featureConfig);
        return new DatasetMinuteEvaluationService(featureConfig, chatClient, documentService, queryService, cleanBeforeLoad);
    }

    /**
     * Creates the tools map based on the feature configuration.
     */
    private Map<QueryType, Tool> createTools(RagFeatureConfiguration featureConfig, ContextRetriever retriever) {
        Map<QueryType, Tool> tools = new HashMap<>();
        
        if (featureConfig.isMetadataEnabled()) {
            tools.putAll(Map.of(
                    QueryType.COUNT_DOCUMENTS, new MetadataCountDocumentsTool(chatClient, retriever),
                    QueryType.FIND_PARAGRAPH, new MetadataFindParagraphTool(chatClient, retriever),
                    QueryType.COUNT_AND_EXPLAIN, new MetadataCountAndExplainTool(chatClient, retriever),
                    QueryType.EXTRACT_ENTITIES, new MetadataExtractEntitiesTool(chatClient, retriever),
                    QueryType.SUMMARIZE_TOPIC, new MetadataSummarizeTopicTool(chatClient, retriever),
                    QueryType.BOOLEAN_QUERY, new MetadataBooleanQueryTool(chatClient, retriever)
            ));
            tools.putAll(Map.of(
                    QueryType.COMPARE, new MetadataCompareTool(chatClient, retriever),
                    QueryType.GET_DURATION, new MetadataGetDurationTool(chatClient, retriever),
                    QueryType.GET_FIELD, new MetadataGetFieldTool(chatClient, retriever),
                    QueryType.FILTER_AND_LIST, new MetadataFilterAndListTool(chatClient, retriever),
                    QueryType.DECISION_EXTRACTION, new MetadataDecisionExtractionTool(chatClient, retriever),
                    QueryType.SUMMARIZE_MEETING, new MetadataSummarizeMeetingTool(chatClient, retriever)
            ));
        } else {
            tools.putAll(Map.of(
                    QueryType.COUNT_DOCUMENTS, new CountDocumentsTool(chatClient, retriever),
                    QueryType.FIND_PARAGRAPH, new FindParagraphTool(chatClient, retriever),
                    QueryType.COUNT_AND_EXPLAIN, new CountAndExplainTool(chatClient, retriever),
                    QueryType.EXTRACT_ENTITIES, new ExtractEntitiesTool(chatClient, retriever),
                    QueryType.SUMMARIZE_TOPIC, new SummarizeTopicTool(chatClient, retriever),
                    QueryType.BOOLEAN_QUERY, new BooleanQueryTool(chatClient, retriever)
            ));
            tools.putAll(Map.of(
                    QueryType.COMPARE, new CompareTool(chatClient, retriever),
                    QueryType.GET_DURATION, new GetDurationTool(chatClient, retriever),
                    QueryType.GET_FIELD, new GetFieldTool(chatClient, retriever),
                    QueryType.FILTER_AND_LIST, new FilterAndListTool(chatClient, retriever),
                    QueryType.DECISION_EXTRACTION, new DecisionExtractionTool(chatClient, retriever),
                    QueryType.SUMMARIZE_MEETING, new SummarizeMeetingTool(chatClient, retriever)
            ));
        }

        return tools;
    }
}

