package com.uniovi.rag.architecture;

import java.util.Arrays;
import java.util.Set;

/**
 * Explicit, class-scoped architecture debt allowlists for ArchUnit guardrails (Agent A6).
 * Each entry must map to a board debt id in {@code .cursor/context/architecture-refactor-board.md} section ARCH-DEBT-*.
 * Remove a class when the violation is fixed; do not add package-wide wildcards.
 */
public final class ArchitectureGuardrailAllowlists {

    private ArchitectureGuardrailAllowlists() {}


    /** Temporary REST DTO/exception coupling (see board ARCH-DEBT-*) */
    public static final Set<String> APPLICATION_REST_ADAPTER_DEBT = Set.copyOf(Arrays.asList(
            "com.uniovi.rag.application.exception.RagServiceException", // ARCH-DEBT-MISC
            "com.uniovi.rag.application.service.ChatMessageApplicationService", // ARCH-DEBT-CHAT
            "com.uniovi.rag.application.service.ChatMessageWorkService", // ARCH-DEBT-CHAT
            "com.uniovi.rag.application.service.ConfigProfileApplicationService", // ARCH-DEBT-RUNTIME-CONFIG
            "com.uniovi.rag.application.service.ConversationApplicationService", // ARCH-DEBT-CHAT
            "com.uniovi.rag.application.service.MoveConversationApplicationService", // ARCH-DEBT-CHAT
            "com.uniovi.rag.application.service.ProjectDocumentApplicationService", // ARCH-DEBT-KNOWLEDGE
            "com.uniovi.rag.application.service.ResolvedConfigSnapshotApplicationService", // ARCH-DEBT-RUNTIME-CONFIG
            "com.uniovi.rag.application.service.admin.AllowlistAdminService", // ARCH-DEBT-ADMIN-MODEL
            "com.uniovi.rag.application.service.admin.model.AdminModelsService", // ARCH-DEBT-ADMIN-MODEL
            "com.uniovi.rag.application.service.async.AsyncLabTaskRunner", // ARCH-DEBT-LAB
            "com.uniovi.rag.application.service.async.AsyncTaskMutationService", // ARCH-DEBT-ASYNC
            "com.uniovi.rag.application.service.async.AsyncTaskService", // ARCH-DEBT-ASYNC
            "com.uniovi.rag.application.service.auth.AuthService", // ARCH-DEBT-AUTH
            "com.uniovi.rag.application.service.auth.OauthLoginService", // ARCH-DEBT-AUTH
            "com.uniovi.rag.application.service.chat.ChatRuntimeCompatibilitySupport", // ARCH-DEBT-CHAT
            "com.uniovi.rag.application.service.chat.ChatRuntimeStateService", // ARCH-DEBT-CHAT
            "com.uniovi.rag.application.service.chat.async.ChatMessageJobHandler", // ARCH-DEBT-CHAT
            "com.uniovi.rag.application.service.classifier.ClassifierModelRegistryService", // ARCH-DEBT-CLASSIFIER
            "com.uniovi.rag.application.service.evaluation.BenchmarkRunOrchestrator", // ARCH-DEBT-LAB
            "com.uniovi.rag.application.service.evaluation.EvaluationServiceFactory", // ARCH-DEBT-LAB
            "com.uniovi.rag.application.service.evaluation.ExperimentalDatasetLabService", // ARCH-DEBT-LAB
            "com.uniovi.rag.application.service.evaluation.LabCampaignService", // ARCH-DEBT-LAB
            "com.uniovi.rag.application.service.evaluation.LabEvaluationRunService", // ARCH-DEBT-LAB
            "com.uniovi.rag.application.service.evaluation.LabExperimentalPresetCatalogService", // ARCH-DEBT-LAB
            "com.uniovi.rag.application.service.evaluation.LabJobConcurrencyException", // ARCH-DEBT-LAB
            "com.uniovi.rag.application.service.evaluation.LabJobEventService", // ARCH-DEBT-LAB
            "com.uniovi.rag.application.service.evaluation.LabJobLifecycleService", // ARCH-DEBT-LAB
            "com.uniovi.rag.application.service.evaluation.corpus.EvaluationCorpusApplicationService", // ARCH-DEBT-LAB
            "com.uniovi.rag.application.service.evaluation.lab.LabClasspathCorpusBootstrapService", // ARCH-DEBT-LAB
            "com.uniovi.rag.application.service.knowledge.KnowledgeIngestionService", // ARCH-DEBT-KNOWLEDGE
            "com.uniovi.rag.application.service.knowledge.ProjectIndexProfileApplicationService", // ARCH-DEBT-KNOWLEDGE
            "com.uniovi.rag.application.service.knowledge.ProjectKnowledgeApplicationService", // ARCH-DEBT-KNOWLEDGE
            "com.uniovi.rag.application.service.me.MeDocumentQueryService", // ARCH-DEBT-KNOWLEDGE
            "com.uniovi.rag.application.service.me.MeSummaryApplicationService", // ARCH-DEBT-ME
            "com.uniovi.rag.application.service.me.UserMePersonalizationService", // ARCH-DEBT-ME
            "com.uniovi.rag.application.service.me.UserMePreferenceService", // ARCH-DEBT-ME
            "com.uniovi.rag.application.service.model.ModelRegistryService", // ARCH-DEBT-ADMIN-MODEL
            "com.uniovi.rag.application.service.model.ModelsCatalogService", // ARCH-DEBT-ADMIN-MODEL
            "com.uniovi.rag.application.service.preset.PresetService", // ARCH-DEBT-ADMIN-MODEL
            "com.uniovi.rag.application.service.project.ProjectAccessService", // ARCH-DEBT-ADMIN-MODEL
            "com.uniovi.rag.application.service.project.ProjectService", // ARCH-DEBT-ADMIN-MODEL
            "com.uniovi.rag.application.service.runtime.execution.RuntimeQueryExecutionService", // ARCH-DEBT-QUERY-RUNTIME
            "com.uniovi.rag.application.service.runtime.config.RuntimeConfigValidationService", // ARCH-DEBT-RUNTIME-CONFIG
            "com.uniovi.rag.application.service.runtime.tracecomparison.RuntimeTraceReplayComparator", // ARCH-DEBT-TRACE
            "com.uniovi.rag.application.service.runtime.tracecomparison.RuntimeTraceReplayComparisonService", // ARCH-DEBT-TRACE
            "com.uniovi.rag.application.service.runtime.tracecomparisonbatch.RuntimeTraceReplayComparisonBatchService", // ARCH-DEBT-TRACE
            "com.uniovi.rag.application.service.runtime.tracecomparisonbatchexport.RuntimeTraceReplayComparisonBatchExportService", // ARCH-DEBT-TRACE
            "com.uniovi.rag.application.service.runtime.tracecomparisonexport.RuntimeTraceReplayComparisonExportService", // ARCH-DEBT-TRACE
            "com.uniovi.rag.application.service.runtime.traceexport.RuntimeTraceExportService", // ARCH-DEBT-TRACE
            "com.uniovi.rag.application.service.runtime.tracequery.RuntimeTraceQueryService", // ARCH-DEBT-TRACE
            "com.uniovi.rag.application.service.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteService", // ARCH-DEBT-TRACE
            "com.uniovi.rag.application.service.runtime.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionService", // ARCH-DEBT-TRACE
            "com.uniovi.rag.application.service.runtime.traceregressionsuitedefinitionexecutionexport.RuntimeTraceRegressionSuiteDefinitionExecutionExportService", // ARCH-DEBT-TRACE
            "com.uniovi.rag.application.service.runtime.traceregressionsuitedefinitionexport.RuntimeTraceRegressionSuiteDefinitionExportService", // ARCH-DEBT-TRACE
            "com.uniovi.rag.application.service.runtime.traceregressionsuitedefinitionimport.RuntimeTraceRegressionSuiteDefinitionImportService", // ARCH-DEBT-TRACE
            "com.uniovi.rag.application.service.runtime.traceregressionsuitedefinitionimportpreview.RuntimeTraceRegressionSuiteDefinitionImportPreviewService", // ARCH-DEBT-TRACE
            "com.uniovi.rag.application.service.runtime.traceregressionsuiteexport.ConversationScopedSuiteManifestScope", // ARCH-DEBT-TRACE
            "com.uniovi.rag.application.service.runtime.traceregressionsuiteexport.RuntimeTraceRegressionSuiteExportService", // ARCH-DEBT-TRACE
            "com.uniovi.rag.application.service.runtime.traceregressionsuiterunexport.RuntimeTraceRegressionSuiteRunExportService", // ARCH-DEBT-TRACE
            "com.uniovi.rag.application.service.runtime.traceregressionsuiterunimport.RuntimeTraceRegressionSuiteRunImportService", // ARCH-DEBT-TRACE
            "com.uniovi.rag.application.service.runtime.traceregressionsuiterunimportpreview.RuntimeTraceRegressionSuiteRunImportPreviewService", // ARCH-DEBT-TRACE
            "com.uniovi.rag.application.service.runtime.tracereplay.RuntimeTraceReplayEligibilityResolver", // ARCH-DEBT-TRACE
            "com.uniovi.rag.application.service.runtime.tracereplay.RuntimeTraceReplayInputLoader", // ARCH-DEBT-TRACE
            "com.uniovi.rag.application.service.runtime.tracereplay.RuntimeTraceReplayService", // ARCH-DEBT-TRACE
            "com.uniovi.rag.application.service.runtime.tracereplay.RuntimeTraceReplayStrategy", // ARCH-DEBT-TRACE
            "com.uniovi.rag.application.service.runtime.tracereplaybatch.RuntimeTraceReplayBatchService", // ARCH-DEBT-TRACE
            "com.uniovi.rag.application.service.runtime.tracereplaybatchexport.RuntimeTraceReplayBatchExportService", // ARCH-DEBT-TRACE
            "com.uniovi.rag.application.service.runtime.tracereplayexport.RuntimeTraceReplayExportService" // ARCH-DEBT-TRACE
    ));

    /** Temporary JPA entity/repository coupling (see board ARCH-DEBT-*) */
    public static final Set<String> APPLICATION_PERSISTENCE_ADAPTER_DEBT = Set.copyOf(Arrays.asList(
            "com.uniovi.rag.application.config.ConfigResolverService", // ARCH-DEBT-RUNTIME-CONFIG
            "com.uniovi.rag.application.port.out.UserAccountPort", // ARCH-DEBT-PORT
            "com.uniovi.rag.application.service.AuditApplicationService", // ARCH-DEBT-MISC
            "com.uniovi.rag.application.service.ChatMessageApplicationService", // ARCH-DEBT-CHAT
            "com.uniovi.rag.application.service.ChatMessageWorkService", // ARCH-DEBT-CHAT
            "com.uniovi.rag.application.service.ConfigProfileApplicationService", // ARCH-DEBT-RUNTIME-CONFIG
            "com.uniovi.rag.application.service.ConversationApplicationService", // ARCH-DEBT-CHAT
            "com.uniovi.rag.application.service.KnowledgeIndexSnapshotService", // ARCH-DEBT-KNOWLEDGE
            "com.uniovi.rag.application.service.KnowledgeVectorMetadataBackfillService", // ARCH-DEBT-KNOWLEDGE
            "com.uniovi.rag.application.service.MoveConversationApplicationService", // ARCH-DEBT-CHAT
            "com.uniovi.rag.application.service.ProjectDocumentApplicationService", // ARCH-DEBT-KNOWLEDGE
            "com.uniovi.rag.application.service.PromoteDocumentApplicationService", // ARCH-DEBT-KNOWLEDGE
            "com.uniovi.rag.application.service.ResolvedConfigSnapshotApplicationService", // ARCH-DEBT-RUNTIME-CONFIG
            "com.uniovi.rag.application.service.account.AccountDeletionApplicationService", // ARCH-DEBT-ASYNC
            "com.uniovi.rag.application.service.account.AccountExportApplicationService", // ARCH-DEBT-ASYNC
            "com.uniovi.rag.application.service.account.AccountExportArtifactRegistrar", // ARCH-DEBT-ASYNC
            "com.uniovi.rag.application.service.account.AccountExportCleanupScheduler", // ARCH-DEBT-ASYNC
            "com.uniovi.rag.application.service.account.AccountExportCompletion", // ARCH-DEBT-ASYNC
            "com.uniovi.rag.application.service.account.AccountExportSnapshotLoader", // ARCH-DEBT-ASYNC
            "com.uniovi.rag.application.service.account.async.AccountDeletionJobHandler", // ARCH-DEBT-ASYNC
            "com.uniovi.rag.application.service.account.async.AccountExportJobHandler", // ARCH-DEBT-ASYNC
            "com.uniovi.rag.application.service.admin.AdminSystemDefaultsService", // ARCH-DEBT-ADMIN-MODEL
            "com.uniovi.rag.application.service.admin.AllowlistAdminService", // ARCH-DEBT-ADMIN-MODEL
            "com.uniovi.rag.application.service.admin.model.AdminModelsService", // ARCH-DEBT-ADMIN-MODEL
            "com.uniovi.rag.application.service.admin.model.AllowedModelReferenceGuard", // ARCH-DEBT-ADMIN-MODEL
            "com.uniovi.rag.application.service.async.AsyncLabTaskRunner", // ARCH-DEBT-LAB
            "com.uniovi.rag.application.service.async.AsyncTaskCancellationService", // ARCH-DEBT-ASYNC
            "com.uniovi.rag.application.service.async.AsyncTaskMutationService", // ARCH-DEBT-ASYNC
            "com.uniovi.rag.application.service.async.AsyncTaskService", // ARCH-DEBT-ASYNC
            "com.uniovi.rag.application.service.auth.AuthService", // ARCH-DEBT-AUTH
            "com.uniovi.rag.application.service.auth.MailOutboxDeliveryService", // ARCH-DEBT-AUTH
            "com.uniovi.rag.application.service.auth.OauthLoginService", // ARCH-DEBT-AUTH
            "com.uniovi.rag.application.service.chat.ChatRuntimeStateService", // ARCH-DEBT-CHAT
            "com.uniovi.rag.application.service.chat.async.ChatMessageJobHandler", // ARCH-DEBT-CHAT
            "com.uniovi.rag.application.service.classifier.ClassifierModelRegistryService", // ARCH-DEBT-CLASSIFIER
            "com.uniovi.rag.application.service.config.ChatPresetDefaults", // ARCH-DEBT-CHAT
            "com.uniovi.rag.application.service.config.ChatScopedRagConfigResolver", // ARCH-DEBT-CHAT
            "com.uniovi.rag.application.service.config.UserProjectConfigurationService", // ARCH-DEBT-RUNTIME-CONFIG
            "com.uniovi.rag.application.service.evaluation.BenchmarkDatasetCompatibility", // ARCH-DEBT-LAB
            "com.uniovi.rag.application.service.evaluation.BenchmarkRunOrchestrator", // ARCH-DEBT-LAB
            "com.uniovi.rag.application.service.evaluation.EvaluationCanonicalPersistenceService", // ARCH-DEBT-LAB
            "com.uniovi.rag.application.service.evaluation.ExperimentalDatasetLabService", // ARCH-DEBT-LAB
            "com.uniovi.rag.application.service.evaluation.ExperimentalDatasetResolver", // ARCH-DEBT-LAB
            "com.uniovi.rag.application.service.evaluation.LabCampaignService", // ARCH-DEBT-LAB
            "com.uniovi.rag.application.service.evaluation.LabEvaluationRunService", // ARCH-DEBT-LAB
            "com.uniovi.rag.application.service.evaluation.LabJobEventService", // ARCH-DEBT-LAB
            "com.uniovi.rag.application.service.evaluation.LabJobLifecycleService", // ARCH-DEBT-LAB
            "com.uniovi.rag.application.service.evaluation.LabMetricsComparisonService", // ARCH-DEBT-LAB
            "com.uniovi.rag.application.service.evaluation.corpus.EvaluationCorpusApplicationService", // ARCH-DEBT-LAB
            "com.uniovi.rag.application.service.evaluation.async.ClassifierEvalJobHandler", // ARCH-DEBT-LAB
            "com.uniovi.rag.application.service.evaluation.async.ClassifierTrainJobHandler", // ARCH-DEBT-LAB
            "com.uniovi.rag.application.service.evaluation.async.EvalEmbeddingRetrievalJobHandler", // ARCH-DEBT-LAB
            "com.uniovi.rag.application.service.evaluation.async.EvalLlmJobHandler", // ARCH-DEBT-LAB
            "com.uniovi.rag.application.service.evaluation.async.EvalRagJobHandler", // ARCH-DEBT-LAB
            "com.uniovi.rag.application.service.evaluation.async.LabJobHandler", // ARCH-DEBT-LAB
            "com.uniovi.rag.application.service.evaluation.async.LabJobPayloadKeys", // ARCH-DEBT-LAB
            "com.uniovi.rag.application.service.evaluation.async.OllamaPullJobHandler", // ARCH-DEBT-LAB
            "com.uniovi.rag.application.service.evaluation.baseline.BaselineRunSnapshotWriter", // ARCH-DEBT-LAB
            "com.uniovi.rag.application.service.evaluation.baseline.ExperimentalSnapshotFactory", // ARCH-DEBT-LAB
            "com.uniovi.rag.application.service.evaluation.baseline.ModelBaselineEvaluationOrchestrator", // ARCH-DEBT-LAB
            "com.uniovi.rag.application.service.evaluation.lab.LabClasspathCorpusBootstrapService", // ARCH-DEBT-LAB
            "com.uniovi.rag.application.service.evaluation.metrics.BenchmarkMvpMetricsCalculator", // ARCH-DEBT-LAB
            "com.uniovi.rag.application.service.evaluation.metrics.BenchmarkMvpRollupCalculator", // ARCH-DEBT-LAB
            "com.uniovi.rag.application.service.evaluation.preset.CorpusAvailabilityGate", // ARCH-DEBT-LAB
            "com.uniovi.rag.application.service.evaluation.preset.LabEvaluationSnapshotService", // ARCH-DEBT-LAB
            "com.uniovi.rag.application.service.evaluation.preset.LabPresetRunPlanService", // ARCH-DEBT-LAB
            "com.uniovi.rag.application.service.evaluation.preset.TypedRagPresetBenchmarkOrchestrator", // ARCH-DEBT-LAB
            "com.uniovi.rag.application.service.knowledge.DocumentIngestionWatchdog", // ARCH-DEBT-KNOWLEDGE
            "com.uniovi.rag.application.service.knowledge.KnowledgeBuildProjectionMapper", // ARCH-DEBT-KNOWLEDGE
            "com.uniovi.rag.application.service.knowledge.KnowledgeConfigurationIntegrationService", // ARCH-DEBT-KNOWLEDGE
            "com.uniovi.rag.application.service.knowledge.KnowledgeDocumentIndexingRequest", // ARCH-DEBT-KNOWLEDGE
            "com.uniovi.rag.application.service.knowledge.KnowledgeIndexingService", // ARCH-DEBT-KNOWLEDGE
            "com.uniovi.rag.application.service.knowledge.KnowledgeIngestionService", // ARCH-DEBT-KNOWLEDGE
            "com.uniovi.rag.application.service.knowledge.KnowledgePipelineOrchestrator", // ARCH-DEBT-KNOWLEDGE
            "com.uniovi.rag.application.service.knowledge.KnowledgeSnapshotService", // ARCH-DEBT-KNOWLEDGE
            "com.uniovi.rag.application.service.knowledge.ProjectIndexProfileApplicationService", // ARCH-DEBT-KNOWLEDGE
            "com.uniovi.rag.application.service.knowledge.ProjectIndexProfileService", // ARCH-DEBT-KNOWLEDGE
            "com.uniovi.rag.application.service.knowledge.ProjectKnowledgeApplicationService", // ARCH-DEBT-KNOWLEDGE
            "com.uniovi.rag.application.service.knowledge.ReindexEventStatusUpdater", // ARCH-DEBT-KNOWLEDGE
            "com.uniovi.rag.application.service.knowledge.ReindexService", // ARCH-DEBT-KNOWLEDGE
            "com.uniovi.rag.application.service.knowledge.WorkspaceDocumentMapper", // ARCH-DEBT-KNOWLEDGE
            "com.uniovi.rag.application.service.knowledge.document.ProjectDocumentIngestionService", // ARCH-DEBT-KNOWLEDGE
            "com.uniovi.rag.application.service.me.MeDocumentQueryService", // ARCH-DEBT-KNOWLEDGE
            "com.uniovi.rag.application.service.me.MeSummaryApplicationService", // ARCH-DEBT-ME
            "com.uniovi.rag.application.service.me.UserMePersonalizationService", // ARCH-DEBT-ME
            "com.uniovi.rag.application.service.me.UserMePreferenceService", // ARCH-DEBT-ME
            "com.uniovi.rag.application.service.model.ModelsCatalogService", // ARCH-DEBT-ADMIN-MODEL
            "com.uniovi.rag.application.service.preset.PresetService", // ARCH-DEBT-ADMIN-MODEL
            "com.uniovi.rag.application.service.project.ProjectAccessService", // ARCH-DEBT-ADMIN-MODEL
            "com.uniovi.rag.application.service.project.ProjectService", // ARCH-DEBT-ADMIN-MODEL
            "com.uniovi.rag.application.service.runtime.execution.RuntimeQueryExecutionService", // ARCH-DEBT-QUERY-RUNTIME
            "com.uniovi.rag.application.service.runtime.KnowledgeRuntimeSnapshotSelector", // ARCH-DEBT-KNOWLEDGE
            "com.uniovi.rag.application.service.runtime.config.RuntimeConfigValidationService", // ARCH-DEBT-RUNTIME-CONFIG
            "com.uniovi.rag.application.service.runtime.memory.ConversationHistoryLoader", // ARCH-DEBT-CHAT
            "com.uniovi.rag.application.service.runtime.tracepersistence.RuntimeTracePersistenceService", // ARCH-DEBT-TRACE
            "com.uniovi.rag.application.service.runtime.tracequery.RuntimeTraceQueryService", // ARCH-DEBT-TRACE
            "com.uniovi.rag.application.service.runtime.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionService", // ARCH-DEBT-TRACE
            "com.uniovi.rag.application.service.runtime.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunPersistenceService", // ARCH-DEBT-TRACE
            "com.uniovi.rag.application.service.runtime.tracereplay.RuntimeReplayResolvedConfigMaterializer", // ARCH-DEBT-TRACE
            "com.uniovi.rag.application.service.runtime.tracereplay.RuntimeTraceReplayEligibilityResolver", // ARCH-DEBT-TRACE
            "com.uniovi.rag.application.service.runtime.tracereplay.RuntimeTraceReplayInputLoader", // ARCH-DEBT-TRACE
            "com.uniovi.rag.application.service.runtime.tracereplay.RuntimeTraceReplayStrategy" // ARCH-DEBT-TRACE
    ));

    public static boolean isApplicationRestAdapterDebt(String className) {
        return APPLICATION_REST_ADAPTER_DEBT.contains(className);
    }

    public static boolean isApplicationPersistenceAdapterDebt(String className) {
        return APPLICATION_PERSISTENCE_ADAPTER_DEBT.contains(className);
    }
}

