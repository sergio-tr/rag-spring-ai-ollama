package com.uniovi.rag.application.service.evaluation;

import com.uniovi.rag.application.service.runtime.ChatGenerationModelSelector;
import com.uniovi.rag.application.service.runtime.ExecutionContextFactory;
import com.uniovi.rag.application.service.runtime.RagExecutionOrchestrator;
import com.uniovi.rag.application.service.runtime.execution.QueryExecutionService;
import com.uniovi.rag.application.service.runtime.execution.RuntimeQueryExecutionService;
import com.uniovi.rag.application.service.runtime.tracepersistence.RuntimeTracePersistenceService;
import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.configuration.RagImplementationProperties;
import com.uniovi.rag.application.service.knowledge.document.DocumentService;
import com.uniovi.rag.application.service.knowledge.document.MetadataMinuteDocumentService;
import com.uniovi.rag.application.service.knowledge.document.SimpleDocumentService;
import com.uniovi.rag.interfaces.rest.support.OllamaConnectivityChecker;
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
    private final ChatGenerationModelSelector chatGenerationModelSelector;
    private final Settings settings;

    public EvaluationServiceFactory(
            ChatClient chatClient,
            PgVectorStore vectorStore,
            JdbcTemplate jdbcTemplate,
            Settings settings,
            OllamaConnectivityChecker ollamaConnectivityChecker,
            ExecutionContextFactory executionContextFactory,
            RagExecutionOrchestrator ragExecutionOrchestrator,
            RuntimeTracePersistenceService runtimeTracePersistenceService,
            ChatGenerationModelSelector chatGenerationModelSelector) {
        this.chatClient = chatClient;
        this.vectorStore = vectorStore;
        this.jdbcTemplate = jdbcTemplate;
        this.settings = normalizeSettings(settings);
        this.ollamaConnectivityChecker = ollamaConnectivityChecker;
        this.executionContextFactory = executionContextFactory;
        this.ragExecutionOrchestrator = ragExecutionOrchestrator;
        this.runtimeTracePersistenceService = runtimeTracePersistenceService;
        this.chatGenerationModelSelector = chatGenerationModelSelector;
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
     * Creates orchestrated query execution (always {@link RuntimeQueryExecutionService}).
     */
    public QueryExecutionService createQueryService(RagImplementationProperties implProps) {
        return new RuntimeQueryExecutionService(
                executionContextFactory,
                ragExecutionOrchestrator,
                runtimeTracePersistenceService,
                chatClient,
                ollamaConnectivityChecker,
                null,
                chatGenerationModelSelector);
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
        QueryExecutionService queryService = createQueryService(implProps);
        return new ReferenceBundleMinuteEvaluationService(
                featureConfig, implProps, chatClient, documentService, queryService, cleanBeforeLoad);
    }
}
