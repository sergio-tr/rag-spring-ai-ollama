package com.uniovi.rag.service.evaluation;

import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.configuration.RagImplementationProperties;
import com.uniovi.rag.domain.model.ExpansionStrategy;
import com.uniovi.rag.application.service.runtime.ExecutionContextFactory;
import com.uniovi.rag.application.service.runtime.RagExecutionOrchestrator;
import com.uniovi.rag.application.service.runtime.tracepersistence.RuntimeTracePersistenceService;
import com.uniovi.rag.interfaces.rest.support.OllamaConnectivityChecker;
import com.uniovi.rag.service.analyser.MinuteNERQueryAnalyser;
import com.uniovi.rag.service.analyser.NoOpQueryAnalyser;
import com.uniovi.rag.service.analyser.QueryAnalyser;
import com.uniovi.rag.service.document.DocumentService;
import com.uniovi.rag.service.document.MetadataMinuteDocumentService;
import com.uniovi.rag.service.document.SimpleDocumentService;
import com.uniovi.rag.service.expand.MinuteDocumentStructureExpander;
import com.uniovi.rag.service.expand.QueryExpander;
import com.uniovi.rag.service.query.ProcessQueryService;
import com.uniovi.rag.service.query.QueryService;
import com.uniovi.rag.service.query.SimpleProcessQueryService;
import com.uniovi.rag.service.query.SimpleQueryService;
import com.uniovi.rag.service.retriever.BasicContextRetriever;
import com.uniovi.rag.service.retriever.ContextRetriever;
import com.uniovi.rag.service.retriever.FilteredContextRetriever;
import com.uniovi.rag.service.retriever.MinuteDocumentContextRetriever;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Factory class for creating evaluation services with custom configurations.
 * This allows testing different configuration combinations without modifying the main Spring beans.
 */
public class EvaluationServiceFactory {

    public record Settings(
            int topK,
            double similarityThreshold,
            String classifierServiceUrl,
            String classifierModelId,
            int classifierTimeoutMs,
            int chunkMaxChars,
            String expansionStrategy,
            int expansionOriginalRepeat,
            int expansionMaxExpansionChars,
            int expansionMaxQueryTotalChars,
            int expansionMaxQueryLengthForLlm,
            int expansionRetryQueryLength,
            boolean knowledgeChatOverlayEnabled) {}

    private final ChatClient chatClient;
    private final PgVectorStore vectorStore;
    private final JdbcTemplate jdbcTemplate;
    private final OllamaConnectivityChecker ollamaConnectivityChecker;
    private final ExecutionContextFactory executionContextFactory;
    private final RagExecutionOrchestrator ragExecutionOrchestrator;
    private final RuntimeTracePersistenceService runtimeTracePersistenceService;
    private final Settings settings;

    public EvaluationServiceFactory(
            ChatClient chatClient,
            PgVectorStore vectorStore,
            JdbcTemplate jdbcTemplate,
            Settings settings,
            OllamaConnectivityChecker ollamaConnectivityChecker,
            ExecutionContextFactory executionContextFactory,
            RagExecutionOrchestrator ragExecutionOrchestrator,
            RuntimeTracePersistenceService runtimeTracePersistenceService) {
        this.chatClient = chatClient;
        this.vectorStore = vectorStore;
        this.jdbcTemplate = jdbcTemplate;
        this.settings = normalizeSettings(settings);
        this.ollamaConnectivityChecker = ollamaConnectivityChecker;
        this.executionContextFactory = executionContextFactory;
        this.ragExecutionOrchestrator = ragExecutionOrchestrator;
        this.runtimeTracePersistenceService = runtimeTracePersistenceService;
    }

    private static Settings normalizeSettings(Settings in) {
        Settings base =
                in != null
                        ? in
                        : new Settings(
                                80,
                                0.25,
                                "http://localhost:8000",
                                "default",
                                5000,
                                400,
                                "COT",
                                1,
                                350,
                                512,
                                500,
                                200,
                                false);
        return new Settings(
                base.topK(),
                base.similarityThreshold(),
                base.classifierServiceUrl() != null ? base.classifierServiceUrl() : "http://localhost:8000",
                (base.classifierModelId() != null && !base.classifierModelId().isBlank())
                        ? base.classifierModelId()
                        : "default",
                base.classifierTimeoutMs() > 0 ? base.classifierTimeoutMs() : 5000,
                base.chunkMaxChars() > 0 ? base.chunkMaxChars() : 400,
                base.expansionStrategy() != null ? base.expansionStrategy() : "COT",
                base.expansionOriginalRepeat() > 0 ? Math.min(5, base.expansionOriginalRepeat()) : 1,
                base.expansionMaxExpansionChars() > 0 ? base.expansionMaxExpansionChars() : 350,
                base.expansionMaxQueryTotalChars() > 0 ? base.expansionMaxQueryTotalChars() : 512,
                base.expansionMaxQueryLengthForLlm() > 0 ? base.expansionMaxQueryLengthForLlm() : 500,
                base.expansionRetryQueryLength() > 0 ? base.expansionRetryQueryLength() : 200,
                base.knowledgeChatOverlayEnabled());
    }

    /**
     * Creates a QueryService with a custom configuration.
     * Implementation selection comes from {@link RagImplementationProperties} (YAML or POST /evaluate/custom overrides).
     */
    public QueryService createQueryService(RagImplementationProperties implProps) {
        RagImplementationProperties impl = implProps != null ? implProps : new RagImplementationProperties();
        ExpansionStrategy strategy;
        try {
            strategy = ExpansionStrategy.valueOf(settings.expansionStrategy().toUpperCase());
        } catch (Exception e) {
            strategy = ExpansionStrategy.COT;
        }
        QueryExpander expander =
                new MinuteDocumentStructureExpander(
                        chatClient,
                        strategy,
                        settings.expansionOriginalRepeat(),
                        settings.expansionMaxExpansionChars(),
                        settings.expansionMaxQueryTotalChars(),
                        settings.expansionMaxQueryLengthForLlm(),
                        settings.expansionRetryQueryLength());
        String analyserImpl =
                impl.getAnalyserImpl() != null ? impl.getAnalyserImpl().trim().toLowerCase() : "minute-ner";
        QueryAnalyser analyser =
                "no-op".equals(analyserImpl) ? new NoOpQueryAnalyser() : new MinuteNERQueryAnalyser(chatClient);
        String retrieverImpl =
                impl.getRetrieverImpl() != null ? impl.getRetrieverImpl().trim().toLowerCase() : "basic";
        ContextRetriever retriever;
        switch (retrieverImpl) {
            case "filtered":
                retriever =
                        new FilteredContextRetriever(
                                vectorStore,
                                chatClient,
                                settings.topK(),
                                settings.similarityThreshold(),
                                settings.knowledgeChatOverlayEnabled());
                break;
            case "minute-document":
                retriever =
                        new MinuteDocumentContextRetriever(
                                vectorStore,
                                chatClient,
                                settings.topK(),
                                settings.similarityThreshold(),
                                settings.knowledgeChatOverlayEnabled());
                break;
            default:
                retriever =
                        new BasicContextRetriever(
                                vectorStore,
                                chatClient,
                                settings.topK(),
                                settings.similarityThreshold(),
                                settings.knowledgeChatOverlayEnabled());
                break;
        }
        String queryServiceImpl =
                impl.getQueryServiceImpl() != null ? impl.getQueryServiceImpl().trim().toLowerCase() : "process";
        if ("simple".equals(queryServiceImpl)) {
            return new SimpleQueryService(expander, analyser, retriever, chatClient, ollamaConnectivityChecker);
        }
        if ("simple-process".equals(queryServiceImpl)) {
            return new SimpleProcessQueryService(
                    executionContextFactory,
                    ragExecutionOrchestrator,
                    runtimeTracePersistenceService,
                    ollamaConnectivityChecker);
        }
        return new ProcessQueryService(
                executionContextFactory,
                ragExecutionOrchestrator,
                runtimeTracePersistenceService,
                chatClient,
                ollamaConnectivityChecker,
                null);
    }

    /** Creates a DocumentService with a custom configuration. */
    public DocumentService createDocumentService(RagFeatureConfiguration featureConfig) {
        if (featureConfig.isMetadataEnabled()) {
            return new MetadataMinuteDocumentService(vectorStore, chatClient, jdbcTemplate, settings.chunkMaxChars());
        }
        return new SimpleDocumentService(vectorStore, chatClient, jdbcTemplate, settings.chunkMaxChars());
    }

    /** Creates an EvaluationService with a custom configuration. */
    public EvaluationService createEvaluationService(
            RagFeatureConfiguration featureConfig, RagImplementationProperties implProps, boolean cleanBeforeLoad) {
        DocumentService documentService = createDocumentService(featureConfig);
        QueryService queryService = createQueryService(implProps);
        return new DatasetMinuteEvaluationService(
                featureConfig, implProps, chatClient, documentService, queryService, cleanBeforeLoad);
    }
}
