package com.uniovi.rag.service.evaluation;

import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.configuration.RagToolsConfiguration;
import com.uniovi.rag.model.Minute;
import com.uniovi.rag.model.QueryType;
import com.uniovi.rag.service.analyser.MinuteNERQueryAnalyser;
import com.uniovi.rag.service.analyser.NERQueryEnricher;
import com.uniovi.rag.service.analyser.NoOpQueryAnalyser;
import com.uniovi.rag.service.analyser.QueryAnalyser;
import com.uniovi.rag.service.classifier.ClassifierServiceClient;
import com.uniovi.rag.service.classifier.QueryClassifier;
import com.uniovi.rag.service.document.DocumentService;
import com.uniovi.rag.service.document.MetadataMinuteDocumentService;
import com.uniovi.rag.service.document.SimpleDocumentService;
import com.uniovi.rag.service.expand.MinuteDocumentStructureExpander;
import com.uniovi.rag.service.expand.QueryExpander;
import com.uniovi.rag.service.extraction.DocumentContentExtractor;
import com.uniovi.rag.service.guard.DateExistenceGuard;
import com.uniovi.rag.service.guard.DefaultDateExistenceGuard;
import com.uniovi.rag.service.guard.QueryDateExtractor;
import com.uniovi.rag.service.postretrieval.DefaultPostRetrievalProcessor;
import com.uniovi.rag.api.OllamaConnectivityChecker;
import com.uniovi.rag.service.query.ProcessQueryService;
import com.uniovi.rag.service.query.QueryService;
import com.uniovi.rag.service.query.ResponseValidator;
import com.uniovi.rag.service.query.SimpleProcessQueryService;
import com.uniovi.rag.service.query.SimpleQueryService;
import com.uniovi.rag.service.ranker.LLMAsJudgeRanker;
import com.uniovi.rag.service.reasoning.SimpleReasoningStrategy;
import com.uniovi.rag.service.retriever.BasicContextRetriever;
import com.uniovi.rag.service.retriever.ContextRetriever;
import com.uniovi.rag.service.retriever.FilteredContextRetriever;
import com.uniovi.rag.service.retriever.MinuteDocumentContextRetriever;
import com.uniovi.rag.model.ExpansionStrategy;
import com.uniovi.rag.tool.MeetingMinutesToolsAdapter;
import com.uniovi.rag.tool.Tool;
import com.uniovi.rag.tool.*;
import com.uniovi.rag.tool.metadata.*;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
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
    private final EmbeddingModel embeddingModel;
    private final int topK;
    private final double similarityThreshold;
    private final String classifierServiceUrl;
    private final String classifierModelId;
    private final int classifierTimeoutMs;
    private final int chunkMaxChars;
    private final ResponseValidator responseValidator;
    private final DocumentContentExtractor documentContentExtractor;
    private final String expansionStrategy;
    private final int expansionOriginalRepeat;
    private final int expansionMaxExpansionChars;
    private final int expansionMaxQueryTotalChars;
    private final int expansionMaxQueryLengthForLlm;
    private final int expansionRetryQueryLength;
    private final OllamaConnectivityChecker ollamaConnectivityChecker;

    public EvaluationServiceFactory(
        ChatClient chatClient,
        PgVectorStore vectorStore,
        JdbcTemplate jdbcTemplate,
        EmbeddingModel embeddingModel,
        int topK,
        double similarityThreshold,
        String classifierServiceUrl,
        String classifierModelId,
        int classifierTimeoutMs,
        int chunkMaxChars,
        ResponseValidator responseValidator,
        DocumentContentExtractor documentContentExtractor,
        String expansionStrategy,
        int expansionOriginalRepeat,
        int expansionMaxExpansionChars,
        int expansionMaxQueryTotalChars,
        int expansionMaxQueryLengthForLlm,
        int expansionRetryQueryLength,
        OllamaConnectivityChecker ollamaConnectivityChecker
    ) {
        this.chatClient = chatClient;
        this.vectorStore = vectorStore;
        this.jdbcTemplate = jdbcTemplate;
        this.embeddingModel = embeddingModel;
        this.topK = topK;
        this.similarityThreshold = similarityThreshold;
        this.classifierServiceUrl = classifierServiceUrl != null ? classifierServiceUrl : "http://localhost:8000";
        this.classifierModelId = (classifierModelId != null && !classifierModelId.isBlank()) ? classifierModelId : "default";
        this.classifierTimeoutMs = classifierTimeoutMs > 0 ? classifierTimeoutMs : 5000;
        this.chunkMaxChars = chunkMaxChars > 0 ? chunkMaxChars : 400;
        this.responseValidator = responseValidator;
        this.documentContentExtractor = documentContentExtractor;
        this.expansionStrategy = expansionStrategy != null ? expansionStrategy : "COT";
        this.expansionOriginalRepeat = expansionOriginalRepeat > 0 ? Math.min(5, expansionOriginalRepeat) : 1;
        this.expansionMaxExpansionChars = expansionMaxExpansionChars > 0 ? expansionMaxExpansionChars : 350;
        this.expansionMaxQueryTotalChars = expansionMaxQueryTotalChars > 0 ? expansionMaxQueryTotalChars : 512;
        this.expansionMaxQueryLengthForLlm = expansionMaxQueryLengthForLlm > 0 ? expansionMaxQueryLengthForLlm : 500;
        this.expansionRetryQueryLength = expansionRetryQueryLength > 0 ? expansionRetryQueryLength : 200;
        this.ollamaConnectivityChecker = ollamaConnectivityChecker;
    }

    /**
     * Creates a QueryService with a custom configuration.
     * Uses featureConfig.getQueryServiceImpl(), getRetrieverImpl(), getAnalyserImpl() when set (e.g. from POST /evaluate/custom body).
     */
    public QueryService createQueryService(RagFeatureConfiguration featureConfig) {
        ExpansionStrategy strategy;
        try {
            strategy = ExpansionStrategy.valueOf((expansionStrategy != null ? expansionStrategy : "COT").toUpperCase());
        } catch (Exception e) {
            strategy = ExpansionStrategy.COT;
        }
        QueryExpander expander = new MinuteDocumentStructureExpander(
                chatClient,
                strategy,
                expansionOriginalRepeat,
                expansionMaxExpansionChars,
                expansionMaxQueryTotalChars,
                expansionMaxQueryLengthForLlm,
                expansionRetryQueryLength
        );
        String analyserImpl = featureConfig.getAnalyserImpl() != null ? featureConfig.getAnalyserImpl().trim().toLowerCase() : "minute-ner";
        QueryAnalyser analyser = "no-op".equals(analyserImpl) ? new NoOpQueryAnalyser() : new MinuteNERQueryAnalyser(chatClient);
        QueryClassifier classifier = new ClassifierServiceClient(classifierServiceUrl, classifierModelId, classifierTimeoutMs);
        String retrieverImpl = featureConfig.getRetrieverImpl() != null ? featureConfig.getRetrieverImpl().trim().toLowerCase() : "basic";
        ContextRetriever retriever;
        switch (retrieverImpl) {
            case "filtered":
                retriever = new FilteredContextRetriever(vectorStore, chatClient, topK, similarityThreshold);
                break;
            case "minute-document":
                retriever = new MinuteDocumentContextRetriever(vectorStore, chatClient, topK, similarityThreshold);
                break;
            default:
                retriever = new BasicContextRetriever(vectorStore, chatClient, topK, similarityThreshold);
                break;
        }
        RagToolsConfiguration toolsConfig = new RagToolsConfiguration(createTools(featureConfig, retriever, documentContentExtractor));
        QueryDateExtractor queryDateExtractor = new QueryDateExtractor();
        DateExistenceGuard dateExistenceGuard = new DefaultDateExistenceGuard(retriever, queryDateExtractor);
        NERQueryEnricher nerQueryEnricher = new NERQueryEnricher(80, 512);
        MeetingMinutesToolsAdapter meetingMinutesToolsAdapter = new MeetingMinutesToolsAdapter(toolsConfig, analyser);
        SimpleReasoningStrategy reasoningStrategy = new SimpleReasoningStrategy();
        LLMAsJudgeRanker responseRanker = new LLMAsJudgeRanker(chatClient);
        DefaultPostRetrievalProcessor postRetrievalProcessor = new DefaultPostRetrievalProcessor(10);
        ToolRagService toolRagService = new ToolRagService(embeddingModel, 5);

        String queryServiceImpl = featureConfig.getQueryServiceImpl() != null ? featureConfig.getQueryServiceImpl().trim().toLowerCase() : "process";
        switch (queryServiceImpl) {
            case "simple":
                return new SimpleQueryService(expander, analyser, retriever, chatClient, ollamaConnectivityChecker);
            case "simple-process":
                return new SimpleProcessQueryService(featureConfig, toolsConfig, expander, analyser, classifier, retriever, chatClient, ollamaConnectivityChecker);
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
                        null,  // questionAnswerAdvisor: evaluation uses manual retrieval path
                        ollamaConnectivityChecker
                );
        }
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
    private Map<QueryType, Tool> createTools(RagFeatureConfiguration featureConfig, ContextRetriever retriever, DocumentContentExtractor extractor) {
        Map<QueryType, Tool> tools = new HashMap<>();

        if (featureConfig.isMetadataEnabled()) {
            tools.putAll(Map.of(
                    QueryType.COUNT_DOCUMENTS, new MetadataCountDocumentsTool(chatClient, retriever, extractor),
                    QueryType.FIND_PARAGRAPH, new MetadataFindParagraphTool(chatClient, retriever, extractor),
                    QueryType.COUNT_AND_EXPLAIN, new MetadataCountAndExplainTool(chatClient, retriever, extractor),
                    QueryType.EXTRACT_ENTITIES, new MetadataExtractEntitiesTool(chatClient, retriever, extractor),
                    QueryType.SUMMARIZE_TOPIC, new MetadataSummarizeTopicTool(chatClient, retriever, extractor),
                    QueryType.BOOLEAN_QUERY, new MetadataBooleanQueryTool(chatClient, retriever, extractor)
            ));
            tools.putAll(Map.of(
                    QueryType.COMPARE, new MetadataCompareTool(chatClient, retriever, extractor),
                    QueryType.GET_DURATION, new MetadataGetDurationTool(chatClient, retriever, extractor),
                    QueryType.GET_FIELD, new MetadataGetFieldTool(chatClient, retriever, extractor),
                    QueryType.FILTER_AND_LIST, new MetadataFilterAndListTool(chatClient, retriever, extractor),
                    QueryType.DECISION_EXTRACTION, new MetadataDecisionExtractionTool(chatClient, retriever, extractor),
                    QueryType.SUMMARIZE_MEETING, new MetadataSummarizeMeetingTool(chatClient, retriever, extractor)
            ));
        } else {
            tools.putAll(Map.of(
                    QueryType.COUNT_DOCUMENTS, new CountDocumentsTool(chatClient, retriever, extractor),
                    QueryType.FIND_PARAGRAPH, new FindParagraphTool(chatClient, retriever, extractor),
                    QueryType.COUNT_AND_EXPLAIN, new CountAndExplainTool(chatClient, retriever, extractor),
                    QueryType.EXTRACT_ENTITIES, new ExtractEntitiesTool(chatClient, retriever, extractor),
                    QueryType.SUMMARIZE_TOPIC, new SummarizeTopicTool(chatClient, retriever, extractor),
                    QueryType.BOOLEAN_QUERY, new BooleanQueryTool(chatClient, retriever, extractor)
            ));
            tools.putAll(Map.of(
                    QueryType.COMPARE, new CompareTool(chatClient, retriever, extractor),
                    QueryType.GET_DURATION, new GetDurationTool(chatClient, retriever, extractor),
                    QueryType.GET_FIELD, new GetFieldTool(chatClient, retriever, extractor),
                    QueryType.FILTER_AND_LIST, new FilterAndListTool(chatClient, retriever, extractor),
                    QueryType.DECISION_EXTRACTION, new DecisionExtractionTool(chatClient, retriever, extractor),
                    QueryType.SUMMARIZE_MEETING, new SummarizeMeetingTool(chatClient, retriever, extractor)
            ));
        }

        return tools;
    }
}

