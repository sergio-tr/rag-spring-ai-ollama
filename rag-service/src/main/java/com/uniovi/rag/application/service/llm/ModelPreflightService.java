package com.uniovi.rag.application.service.llm;

import com.uniovi.rag.application.exception.llm.LlmFailureKind;
import com.uniovi.rag.application.exception.llm.LlmExceptionTranslator;
import com.uniovi.rag.application.exception.llm.LlmProviderException;
import com.uniovi.rag.application.exception.llm.LlmRemoteFailures;
import com.uniovi.rag.application.service.config.llm.ResolvedLlmConfigResolver;
import com.uniovi.rag.application.service.evaluation.LabBenchmarkDefaultModelResolver;
import com.uniovi.rag.application.service.evaluation.StartBenchmarkRunRequest;
import com.uniovi.rag.application.service.evaluation.baseline.EvaluationModelAvailabilityGate;
import com.uniovi.rag.application.service.evaluation.judge.EvaluationJudgeLlmExecutor;
import com.uniovi.rag.application.service.evaluation.preset.ExperimentalPresetCanonicalCatalog;
import com.uniovi.rag.application.service.knowledge.ProjectIndexProfileResolver;
import com.uniovi.rag.application.service.knowledge.ProjectIndexProfileService;
import com.uniovi.rag.domain.evaluation.BenchmarkKind;
import com.uniovi.rag.domain.evaluation.workbook.RagExperimentalPresetCode;
import com.uniovi.rag.domain.exception.ErrorCode;
import com.uniovi.rag.domain.knowledge.MaterializationStrategy;
import com.uniovi.rag.domain.knowledge.ProjectIndexProfile;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.domain.llm.ResolvedLlmConfig;
import com.uniovi.rag.infrastructure.health.ModelPreflightProperties;
import com.uniovi.rag.infrastructure.vector.EmbeddingSpaceGuard;
import com.uniovi.rag.interfaces.rest.support.OllamaConnectivityChecker;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Fail-fast model/provider availability checks before expensive ingestion, chat, or Lab work.
 */
@Service
public class ModelPreflightService {

    private final EvaluationModelAvailabilityGate availabilityGate;
    private final EmbeddingSpaceGuard embeddingSpaceGuard;
    private final ProjectIndexProfileResolver projectIndexProfileResolver;
    private final ProjectIndexProfileService projectIndexProfileService;
    private final ResolvedLlmConfigResolver configResolver;
    private final OllamaConnectivityChecker ollamaConnectivityChecker;
    private final LlmManualHealthCheckService healthCheckService;
    private final ModelPreflightProperties preflightProperties;
    private final LabBenchmarkDefaultModelResolver defaultModelResolver;
    private final EvaluationJudgeLlmExecutor evaluationJudgeLlmExecutor;

    public ModelPreflightService(
            EvaluationModelAvailabilityGate availabilityGate,
            EmbeddingSpaceGuard embeddingSpaceGuard,
            ProjectIndexProfileResolver projectIndexProfileResolver,
            ProjectIndexProfileService projectIndexProfileService,
            ResolvedLlmConfigResolver configResolver,
            OllamaConnectivityChecker ollamaConnectivityChecker,
            LlmManualHealthCheckService healthCheckService,
            ModelPreflightProperties preflightProperties,
            LabBenchmarkDefaultModelResolver defaultModelResolver,
            EvaluationJudgeLlmExecutor evaluationJudgeLlmExecutor) {
        this.availabilityGate = availabilityGate;
        this.embeddingSpaceGuard = embeddingSpaceGuard;
        this.projectIndexProfileResolver = projectIndexProfileResolver;
        this.projectIndexProfileService = projectIndexProfileService;
        this.configResolver = configResolver;
        this.ollamaConnectivityChecker = ollamaConnectivityChecker;
        this.healthCheckService = healthCheckService;
        this.preflightProperties = preflightProperties;
        this.defaultModelResolver = defaultModelResolver;
        this.evaluationJudgeLlmExecutor = evaluationJudgeLlmExecutor;
    }

    /** Before document upload/indexing when the project uses vector embeddings. */
    public void requireProjectEmbeddingForIndexing(UUID userId, UUID projectId) {
        ProjectIndexProfile profile = projectIndexProfileService.ensureDefault(projectId);
        if (profile.materializationStrategy() == MaterializationStrategy.STRUCTURED_SEARCH) {
            return;
        }
        ProjectIndexProfileResolver.ResolvedIngestionIndexProfile ingestion =
                projectIndexProfileResolver.resolveForIngestion(projectId, profile);
        requireEmbeddingModel(
                userId,
                ingestion.resolvedEmbeddingModel(),
                ingestion.embeddingProvider(),
                ModelPreflightOperation.INDEXING_EMBEDDING);
    }

    /** Before enqueueing a chat assistant job. */
    public void requireChatForMessage(UUID userId, UUID projectId, String chatModelOverride) {
        ResolvedLlmConfig config = resolveChatConfig(userId, projectId, chatModelOverride);
        String chatModel =
                chatModelOverride != null && !chatModelOverride.isBlank()
                        ? chatModelOverride.trim()
                        : config.chatModel();
        requireChatModel(userId, chatModel, config, ModelPreflightOperation.CHAT);

        boolean needsEmbedding = projectUsesVectorEmbedding(projectId);
        if (needsEmbedding) {
            ResolvedLlmConfig embConfig = configResolver.resolve(userId, null, null);
            requireEmbeddingCatalogOnly(userId, embConfig.embeddingModel(), ModelPreflightOperation.CHAT);
        }

        ollamaConnectivityChecker.prepareForQuery(
                chatModel,
                config.requiresOllamaNativeChat(),
                needsEmbedding && config.requiresOllamaNativeEmbedding());
        probeOpenAiChatIfNeeded(userId, chatModelOverride, config);
    }

    public void requireChatModels(UUID userId, List<String> modelIds, ModelPreflightOperation operation) {
        for (String modelId : nonBlank(modelIds)) {
            ResolvedLlmConfig config = resolveChatConfig(userId, null, modelId);
            requireChatModel(userId, modelId, config, operation);
        }
    }

    public void requireEmbeddingModels(UUID userId, List<String> modelIds, ModelPreflightOperation operation) {
        for (String modelId : nonBlank(modelIds)) {
            ResolvedLlmConfig config = configResolver.resolve(userId, null, null);
            requireEmbeddingModel(userId, modelId, config.embeddingProvider(), operation);
        }
    }

    public void requireJudgeModel(UUID userId, ModelPreflightOperation operation) {
        String judgeModel = evaluationJudgeLlmExecutor.resolveJudgeModelIdForPreflight(userId);
        if (judgeModel == null || judgeModel.isBlank()) {
            throw LlmRemoteFailures.invalidModel(
                    LlmProvider.OPENAI_COMPATIBLE,
                    operation.wireName(),
                    "(judge)",
                    null,
                    "evaluation judge model is not configured");
        }
        ResolvedLlmConfig config = resolveChatConfig(userId, null, judgeModel);
        requireChatModel(userId, judgeModel, config, operation);
    }

    /** Lab benchmark model checks before creating long-running jobs. */
    public void requireModelsForBenchmark(
            UUID userId, BenchmarkKind kind, StartBenchmarkRunRequest request, List<String> llmModelIds, List<String> embeddingModelIds) {
        Objects.requireNonNull(kind, "kind");
        switch (kind) {
            case LLM_JUDGE_QA -> {
                List<String> llms = llmModelIds != null && !llmModelIds.isEmpty()
                        ? llmModelIds
                        : singleOrEmpty(defaultModelResolver.resolveLlmModelId(userId, request.llmModelId()));
                requireChatModels(userId, llms, ModelPreflightOperation.BENCHMARK_LLM);
                requireJudgeModel(userId, ModelPreflightOperation.BENCHMARK_JUDGE);
            }
            case EMBEDDING_RETRIEVAL -> {
                List<String> embs = embeddingModelIds != null && !embeddingModelIds.isEmpty()
                        ? embeddingModelIds
                        : singleOrEmpty(
                                defaultModelResolver.resolveEmbeddingModelId(userId, request.embeddingModelId()));
                requireEmbeddingModels(userId, embs, ModelPreflightOperation.BENCHMARK_EMBEDDING);
                if (request != null && request.embeddingDownstreamRagEffective()) {
                    String llm = defaultModelResolver.resolveLlmModelId(userId, request.llmModelId());
                    requireChatModels(userId, singleOrEmpty(llm), ModelPreflightOperation.BENCHMARK_LLM);
                    requireJudgeModel(userId, ModelPreflightOperation.BENCHMARK_JUDGE);
                }
            }
            case RAG_PRESET_END_TO_END -> requireRagPresetModels(userId, request);
            case CLASSIFIER_METRICS -> { /* no external LLM preflight */ }
        }
    }

    private void requireRagPresetModels(UUID userId, StartBenchmarkRunRequest request) {
        if (request == null) {
            return;
        }
        List<String> presetCodes = request.experimentalPresetCodes();
        if (presetCodes == null || presetCodes.isEmpty()) {
            return;
        }
        boolean needsEmbedding = false;
        boolean needsJudge = false;
        for (String raw : presetCodes) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            try {
                RagExperimentalPresetCode code = RagExperimentalPresetCode.valueOf(raw.trim().toUpperCase());
                needsEmbedding = needsEmbedding || ExperimentalPresetCanonicalCatalog.embeddingRequired(code);
                needsJudge = needsJudge || presetUsesJudge(code);
            } catch (IllegalArgumentException ignored) {
                // config preflight already rejects unknown presets
            }
        }
        if (needsEmbedding) {
            String embeddingModelId = request.embeddingModelId();
            if (embeddingModelId == null || embeddingModelId.isBlank()) {
                embeddingModelId = defaultModelResolver.resolveEmbeddingModelId(userId, null);
            }
            if (embeddingModelId != null && !embeddingModelId.isBlank()) {
                requireEmbeddingModels(
                        userId, List.of(embeddingModelId), ModelPreflightOperation.BENCHMARK_RAG);
            }
        }
        String llm = defaultModelResolver.resolveLlmModelId(userId, request.llmModelId());
        requireChatModels(userId, singleOrEmpty(llm), ModelPreflightOperation.BENCHMARK_RAG);
        if (needsJudge) {
            requireJudgeModel(userId, ModelPreflightOperation.BENCHMARK_JUDGE);
        }
    }

    private static boolean presetUsesJudge(RagExperimentalPresetCode code) {
        var terminal = ExperimentalPresetCanonicalCatalog.effectiveTerminalRuntimeJson(code);
        if (terminal != null && terminal.has("judgeEnabled")) {
            return terminal.get("judgeEnabled").asBoolean(false);
        }
        return false;
    }

    private void requireChatModel(
            UUID userId, String modelId, ResolvedLlmConfig config, ModelPreflightOperation operation) {
        if (modelId == null || modelId.isBlank()) {
            throw LlmRemoteFailures.invalidModel(
                    config != null ? config.chatProvider() : LlmProvider.OPENAI_COMPATIBLE,
                    operation.wireName(),
                    "(empty)",
                    config != null ? config.baseUrl() : null,
                    "chat model id is required");
        }
        if (!availabilityGate.isChatModelAvailable(userId, modelId)) {
            throw unavailableChat(config, operation, modelId);
        }
    }

    private void requireEmbeddingModel(
            UUID userId, String modelId, LlmProvider provider, ModelPreflightOperation operation) {
        if (modelId == null || modelId.isBlank()) {
            throw LlmRemoteFailures.invalidModel(
                    provider,
                    operation.wireName(),
                    "(empty)",
                    null,
                    "embedding model id is required");
        }
        if (!availabilityGate.isEmbeddingModelAvailable(userId, modelId)) {
            throw unavailableEmbedding(provider, operation, modelId);
        }
        try {
            embeddingSpaceGuard.assertFitsPhysicalVectorColumn(modelId);
        } catch (ResponseStatusException ex) {
            throw translateEmbeddingProbe(ex, provider, operation, modelId);
        } catch (RuntimeException ex) {
            ResolvedLlmConfig config = configResolver.resolve(userId, null, null);
            throw LlmExceptionTranslator.translate(ex, config, operation.wireName(), modelId);
        }
    }

    private void requireEmbeddingCatalogOnly(UUID userId, String modelId, ModelPreflightOperation operation) {
        if (modelId == null || modelId.isBlank()) {
            return;
        }
        if (!availabilityGate.isEmbeddingModelAvailable(userId, modelId)) {
            ResolvedLlmConfig config = configResolver.resolve(userId, null, null);
            throw unavailableEmbedding(config.embeddingProvider(), operation, modelId);
        }
    }

    private void probeOpenAiChatIfNeeded(UUID userId, String chatModelOverride, ResolvedLlmConfig config) {
        if (config == null || config.chatProvider() != LlmProvider.OPENAI_COMPATIBLE) {
            return;
        }
        int timeoutMs = Math.min(
                preflightProperties.getProbeTimeoutMs(),
                config.timeoutMs() != null && config.timeoutMs() > 0 ? config.timeoutMs() : preflightProperties.getProbeTimeoutMs());
        LlmManualHealthCheckService.LlmHealthCheckResult result =
                healthCheckService.probeChatWithTimeout(userId, chatModelOverride, timeoutMs);
        if (!result.healthy()) {
            String model =
                    chatModelOverride != null && !chatModelOverride.isBlank()
                            ? chatModelOverride.trim()
                            : config.chatModel();
            throw LlmRemoteFailures.connectionFailed(
                    config.chatProvider(),
                    ModelPreflightOperation.CHAT.wireName(),
                    model,
                    config.baseUrl(),
                    new IllegalStateException(result.message()));
        }
    }

    private boolean projectUsesVectorEmbedding(UUID projectId) {
        if (projectId == null) {
            return true;
        }
        ProjectIndexProfile profile = projectIndexProfileService.ensureDefault(projectId);
        return profile.materializationStrategy() != MaterializationStrategy.STRUCTURED_SEARCH;
    }

    private static LlmProviderException unavailableChat(
            ResolvedLlmConfig config, ModelPreflightOperation operation, String modelId) {
        LlmProvider provider = config != null ? config.chatProvider() : LlmProvider.OPENAI_COMPATIBLE;
        return LlmRemoteFailures.invalidModel(
                provider,
                operation.wireName(),
                modelId,
                config != null ? config.baseUrl() : null,
                "chat model is not available in the configured provider");
    }

    private static LlmProviderException unavailableEmbedding(
            LlmProvider provider, ModelPreflightOperation operation, String modelId) {
        return LlmRemoteFailures.invalidModel(
                provider,
                operation.wireName(),
                modelId,
                null,
                "embedding model is not available in the configured provider");
    }

    private static LlmProviderException translateEmbeddingProbe(
            ResponseStatusException ex, LlmProvider provider, ModelPreflightOperation operation, String modelId) {
        String reason = ex.getReason() != null ? ex.getReason() : ex.getMessage();
        if (reason != null && reason.contains("EMBEDDING_DIMENSION_MISMATCH")) {
            return new LlmProviderException(
                    LlmFailureKind.INVALID_MODEL,
                    provider,
                    operation.wireName(),
                    modelId,
                    null,
                    "Embedding model output dimensions are incompatible with the vector index.",
                    reason,
                    ex) {
                @Override
                public ErrorCode errorCode() {
                    return ErrorCode.MODEL_DIMENSION_MISMATCH;
                }
            };
        }
        if (reason != null && reason.contains("EMBEDDING_DIMENSION_UNAVAILABLE")) {
            return LlmRemoteFailures.connectionFailed(provider, operation.wireName(), modelId, null, ex);
        }
        return LlmRemoteFailures.connectionFailed(provider, operation.wireName(), modelId, null, ex);
    }

    private static List<String> nonBlank(List<String> modelIds) {
        if (modelIds == null || modelIds.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String id : modelIds) {
            if (id != null && !id.isBlank()) {
                out.add(id.trim());
            }
        }
        return out;
    }

    private ResolvedLlmConfig resolveChatConfig(UUID userId, UUID projectId, String chatModelOverride) {
        if (chatModelOverride != null && !chatModelOverride.isBlank()) {
            return configResolver.resolveForOrchestratedExecute(
                    userId, projectId, null, null, Optional.of(chatModelOverride.trim()));
        }
        return configResolver.resolve(userId, projectId, null);
    }

    private static List<String> singleOrEmpty(String modelId) {
        if (modelId == null || modelId.isBlank()) {
            return List.of();
        }
        return List.of(modelId.trim());
    }
}
