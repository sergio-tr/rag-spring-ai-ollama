package com.uniovi.rag.service.evaluation;

import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.configuration.RagImplementationProperties;
import com.uniovi.rag.configuration.RagToolsConfiguration;
import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.service.analyser.MinuteNERQueryAnalyser;
import com.uniovi.rag.service.analyser.NoOpQueryAnalyser;
import com.uniovi.rag.service.analyser.QueryAnalyser;
import com.uniovi.rag.infrastructure.classifier.ClassifierServiceClient;
import com.uniovi.rag.infrastructure.classifier.QueryClassifier;
import com.uniovi.rag.service.document.DocumentService;
import com.uniovi.rag.service.document.MetadataMinuteDocumentService;
import com.uniovi.rag.service.document.SimpleDocumentService;
import com.uniovi.rag.service.expand.MinuteDocumentStructureExpander;
import com.uniovi.rag.service.expand.QueryExpander;
import com.uniovi.rag.service.extraction.DocumentContentExtractor;
import com.uniovi.rag.service.guard.QueryDateExtractor;
import com.uniovi.rag.service.postretrieval.PostRetrievalProcessor;
import com.uniovi.rag.service.ranker.ResponseRanker;
import com.uniovi.rag.service.reasoning.ReasoningStrategy;
import com.uniovi.rag.interfaces.rest.support.OllamaConnectivityChecker;
import com.uniovi.rag.application.port.ModelCatalogPort;
import com.uniovi.rag.configuration.RagRuntimeProperties;
import com.uniovi.rag.application.service.runtime.ExecutionContextFactory;
import com.uniovi.rag.application.service.runtime.RagExecutionOrchestrator;
import com.uniovi.rag.service.config.ChatScopedRagConfigResolver;
import com.uniovi.rag.service.query.ProcessQueryService;
import com.uniovi.rag.service.query.QueryService;
import com.uniovi.rag.service.query.ResponseValidator;
import com.uniovi.rag.service.query.SimpleProcessQueryService;
import com.uniovi.rag.service.query.SimpleQueryService;
import com.uniovi.rag.service.retriever.BasicContextRetriever;
import com.uniovi.rag.service.retriever.ContextRetriever;
import com.uniovi.rag.service.retriever.FilteredContextRetriever;
import com.uniovi.rag.service.retriever.MinuteDocumentContextRetriever;
import com.uniovi.rag.domain.model.ExpansionStrategy;
import com.uniovi.rag.tool.Tool;
import com.uniovi.rag.tool.*;
import com.uniovi.rag.tool.metadata.*;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
    private final MetadataLlmResponseCacheService metadataLlmResponseCacheService;
    private final ModelCatalogPort modelCatalogPort;
    private final ChatScopedRagConfigResolver chatScopedRagConfigResolver;
    private final RagRuntimeProperties ragRuntimeProperties;
    private final ReasoningStrategy reasoningStrategy;
    private final ResponseRanker responseRanker;
    private final PostRetrievalProcessor postRetrievalProcessor;
    private final QueryDateExtractor queryDateExtractor;
    private final boolean knowledgeChatOverlayEnabled;
    private final ExecutionContextFactory executionContextFactory;
    private final RagExecutionOrchestrator ragExecutionOrchestrator;

    public EvaluationServiceFactory(
        ChatClient chatClient,
        PgVectorStore vectorStore,
        JdbcTemplate jdbcTemplate,
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
        OllamaConnectivityChecker ollamaConnectivityChecker,
        MetadataLlmResponseCacheService metadataLlmResponseCacheService,
        ModelCatalogPort modelCatalogPort,
        ChatScopedRagConfigResolver chatScopedRagConfigResolver,
        ExecutionContextFactory executionContextFactory,
        RagExecutionOrchestrator ragExecutionOrchestrator,
        ReasoningStrategy reasoningStrategy,
        ResponseRanker responseRanker,
        PostRetrievalProcessor postRetrievalProcessor,
        QueryDateExtractor queryDateExtractor,
        @Value("${knowledge.v2.chat-overlay.enabled:false}") boolean knowledgeChatOverlayEnabled,
        @Autowired(required = false) RagRuntimeProperties ragRuntimeProperties
    ) {
        this.chatClient = chatClient;
        this.vectorStore = vectorStore;
        this.jdbcTemplate = jdbcTemplate;
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
        this.metadataLlmResponseCacheService = metadataLlmResponseCacheService;
        this.modelCatalogPort = modelCatalogPort;
        this.chatScopedRagConfigResolver = chatScopedRagConfigResolver;
        this.reasoningStrategy = reasoningStrategy;
        this.responseRanker = responseRanker;
        this.postRetrievalProcessor = postRetrievalProcessor;
        this.queryDateExtractor = queryDateExtractor;
        this.knowledgeChatOverlayEnabled = knowledgeChatOverlayEnabled;
        this.ragRuntimeProperties = ragRuntimeProperties;
        this.executionContextFactory = executionContextFactory;
        this.ragExecutionOrchestrator = ragExecutionOrchestrator;
    }

    /**
     * Creates a QueryService with a custom configuration.
     * Implementation selection comes from {@link RagImplementationProperties} (YAML or POST /evaluate/custom overrides).
     */
    public QueryService createQueryService(RagFeatureConfiguration featureConfig, RagImplementationProperties implProps) {
        RagImplementationProperties impl = implProps != null ? implProps : new RagImplementationProperties();
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
        String analyserImpl = impl.getAnalyserImpl() != null ? impl.getAnalyserImpl().trim().toLowerCase() : "minute-ner";
        QueryAnalyser analyser = "no-op".equals(analyserImpl) ? new NoOpQueryAnalyser() : new MinuteNERQueryAnalyser(chatClient);
        QueryClassifier classifier = new ClassifierServiceClient(classifierServiceUrl, classifierModelId, classifierTimeoutMs);
        String retrieverImpl = impl.getRetrieverImpl() != null ? impl.getRetrieverImpl().trim().toLowerCase() : "basic";
        ContextRetriever retriever;
        switch (retrieverImpl) {
            case "filtered":
                retriever = new FilteredContextRetriever(
                        vectorStore, chatClient, topK, similarityThreshold, knowledgeChatOverlayEnabled);
                break;
            case "minute-document":
                retriever = new MinuteDocumentContextRetriever(
                        vectorStore, chatClient, topK, similarityThreshold, knowledgeChatOverlayEnabled);
                break;
            default:
                retriever = new BasicContextRetriever(
                        vectorStore, chatClient, topK, similarityThreshold, knowledgeChatOverlayEnabled);
                break;
        }
        String queryServiceImpl = impl.getQueryServiceImpl() != null ? impl.getQueryServiceImpl().trim().toLowerCase() : "process";
        if ("simple".equals(queryServiceImpl)) {
            return new SimpleQueryService(expander, analyser, retriever, chatClient, ollamaConnectivityChecker);
        }
        if ("simple-process".equals(queryServiceImpl)) {
            return new SimpleProcessQueryService(
                    executionContextFactory, ragExecutionOrchestrator, ollamaConnectivityChecker);
        }
        return new ProcessQueryService(
                executionContextFactory, ragExecutionOrchestrator, chatClient, ollamaConnectivityChecker);
    }

    /**
     * Creates a DocumentService with a custom configuration.
     */
    public DocumentService createDocumentService(RagFeatureConfiguration featureConfig) {
        if (featureConfig.isMetadataEnabled()) {
            return new MetadataMinuteDocumentService(vectorStore, chatClient, jdbcTemplate, chunkMaxChars);
        }
        return new SimpleDocumentService(vectorStore, chatClient, jdbcTemplate, chunkMaxChars);
    }

    /**
     * Creates an EvaluationService with a custom configuration.
     */
    public EvaluationService createEvaluationService(
            RagFeatureConfiguration featureConfig,
            RagImplementationProperties implProps,
            boolean cleanBeforeLoad) {
        DocumentService documentService = createDocumentService(featureConfig);
        QueryService queryService = createQueryService(featureConfig, implProps);
        return new DatasetMinuteEvaluationService(
                featureConfig, implProps, chatClient, documentService, queryService, cleanBeforeLoad);
    }

    /**
     * Creates the tools map based on the feature configuration.
     */
    private Map<QueryType, Tool> createTools(RagFeatureConfiguration featureConfig, ContextRetriever retriever, DocumentContentExtractor extractor) {
        Map<QueryType, Tool> tools = new HashMap<>();

        if (featureConfig.isMetadataEnabled()) {
            tools.putAll(Map.of(
                    QueryType.COUNT_DOCUMENTS, new MetadataCountDocumentsTool(chatClient, retriever, extractor, metadataLlmResponseCacheService),
                    QueryType.FIND_PARAGRAPH, new MetadataFindParagraphTool(chatClient, retriever, extractor, metadataLlmResponseCacheService),
                    QueryType.COUNT_AND_EXPLAIN, new MetadataCountAndExplainTool(chatClient, retriever, extractor, metadataLlmResponseCacheService),
                    QueryType.EXTRACT_ENTITIES, new MetadataExtractEntitiesTool(chatClient, retriever, extractor, metadataLlmResponseCacheService),
                    QueryType.SUMMARIZE_TOPIC, new MetadataSummarizeTopicTool(chatClient, retriever, extractor, metadataLlmResponseCacheService),
                    QueryType.BOOLEAN_QUERY, new MetadataBooleanQueryTool(chatClient, retriever, extractor, metadataLlmResponseCacheService)
            ));
            tools.putAll(Map.of(
                    QueryType.COMPARE, new MetadataCompareTool(chatClient, retriever, extractor, metadataLlmResponseCacheService),
                    QueryType.GET_DURATION, new MetadataGetDurationTool(chatClient, retriever, extractor, metadataLlmResponseCacheService),
                    QueryType.GET_FIELD, new MetadataGetFieldTool(chatClient, retriever, extractor, metadataLlmResponseCacheService),
                    QueryType.FILTER_AND_LIST, new MetadataFilterAndListTool(chatClient, retriever, extractor, metadataLlmResponseCacheService),
                    QueryType.DECISION_EXTRACTION, new MetadataDecisionExtractionTool(chatClient, retriever, extractor, metadataLlmResponseCacheService),
                    QueryType.SUMMARIZE_MEETING, new MetadataSummarizeMeetingTool(chatClient, retriever, extractor, metadataLlmResponseCacheService)
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

