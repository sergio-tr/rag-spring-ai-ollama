package com.uniovi.rag.application.service.llm.catalog;

import com.uniovi.rag.application.service.config.llm.ResolvedLlmConfigResolver;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.domain.llm.ResolvedLlmConfig;
import com.uniovi.rag.domain.llm.catalog.LlmModelCapability;
import com.uniovi.rag.interfaces.rest.dto.lab.LabEvaluationModelDto;
import com.uniovi.rag.interfaces.rest.dto.lab.LabEvaluationModelsResponseDto;
import com.uniovi.rag.interfaces.rest.dto.llm.catalog.LlmCatalogModelDto;
import com.uniovi.rag.interfaces.rest.dto.llm.catalog.LlmCatalogResponseDto;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Lab evaluation model picker backed by the properties catalog (Phase 5). */
@Service
public class LabEvaluationModelsService {

    private final ResolvedLlmConfigResolver configResolver;
    private final EvaluationModelCatalogService evaluationModelCatalogService;

    public LabEvaluationModelsService(
            ResolvedLlmConfigResolver configResolver, EvaluationModelCatalogService evaluationModelCatalogService) {
        this.configResolver = configResolver;
        this.evaluationModelCatalogService = evaluationModelCatalogService;
    }

    public LabEvaluationModelsResponseDto listForUser(
            UUID userId, LlmModelCapability capability, boolean includeRuntimeStatus) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        ResolvedLlmConfig config = configResolver.resolve(userId, null, null);
        LlmProvider effectiveProvider =
                capability == LlmModelCapability.EMBEDDING ? config.embeddingProvider() : config.chatProvider();

        LlmCatalogResponseDto catalog =
                evaluationModelCatalogService.listForUser(userId, capability, includeRuntimeStatus);

        List<LabEvaluationModelDto> models = new ArrayList<>();
        for (LlmCatalogModelDto entry : catalog.models()) {
            if (entry.provider() != effectiveProvider || entry.capability() != capability) {
                continue;
            }
            models.add(toEvaluationDto(entry, capability, effectiveProvider));
        }
        models.sort(Comparator.comparing(LabEvaluationModelDto::modelName));

        boolean hasCompatible =
                capability == LlmModelCapability.EMBEDDING
                        ? models.stream().anyMatch(m -> Boolean.TRUE.equals(m.compatibleWithCurrentVectorStore()))
                        : evaluationModelCatalogService.hasCompatibleEmbeddingModels(userId);

        return new LabEvaluationModelsResponseDto(
                effectiveProvider, capability, List.copyOf(models), hasCompatible);
    }

    private static LabEvaluationModelDto toEvaluationDto(
            LlmCatalogModelDto entry, LlmModelCapability capability, LlmProvider effectiveProvider) {
        boolean evalSelectable = computeEvalSelectable(entry, capability);
        String blockedReason = evalSelectable ? null : blockedReason(entry, capability, effectiveProvider);
        return new LabEvaluationModelDto(
                entry.modelName(),
                evalSelectable,
                blockedReason,
                evalSelectable ? null : LlmModelRuntimeReasonSupport.evaluationBlockedReasonCode(entry),
                entry.runtimeStatus(),
                entry.embeddingDimensions(),
                entry.compatibleWithCurrentVectorStore(),
                entry.usableAsDefault());
    }

    static boolean computeEvalSelectable(LlmCatalogModelDto entry, LlmModelCapability capability) {
        if (!entry.available()) {
            return false;
        }
        if (capability == LlmModelCapability.EMBEDDING
                && !EvaluationModelCatalogService.isVectorStoreCompatible(entry)) {
            return false;
        }
        return EvaluationModelCatalogService.isRuntimeEvalSelectable(entry);
    }

    private static String blockedReason(
            LlmCatalogModelDto entry, LlmModelCapability capability, LlmProvider effectiveProvider) {
        if (capability == LlmModelCapability.EMBEDDING
                && !EvaluationModelCatalogService.isVectorStoreCompatible(entry)) {
            return "Incompatible with vector store";
        }
        if (!EvaluationModelCatalogService.isRuntimeEvalSelectable(entry)) {
            if (entry.runtimeDetail() != null && !entry.runtimeDetail().isBlank()) {
                return sanitizeRuntimeDetail(entry.runtimeDetail().trim(), effectiveProvider);
            }
            return switch (entry.runtimeStatus()) {
                case UNAVAILABLE ->
                        effectiveProvider == LlmProvider.OPENAI_COMPATIBLE
                                ? "Model not available on configured API"
                                : "Model not available at runtime";
                case PROBE_FAILED ->
                        effectiveProvider == LlmProvider.OPENAI_COMPATIBLE
                                ? "Runtime status not probed for remote provider"
                                : "Runtime probe failed";
                default -> "Model not selectable for evaluation";
            };
        }
        if (!entry.available()) {
            return effectiveProvider == LlmProvider.OPENAI_COMPATIBLE
                    ? "Model not configured in OpenAI-compatible catalog"
                    : "Model not configured in catalog";
        }
        return "Model not selectable for evaluation";
    }

    private static String sanitizeRuntimeDetail(String detail, LlmProvider effectiveProvider) {
        if (effectiveProvider == LlmProvider.OPENAI_COMPATIBLE && detail.toLowerCase(Locale.ROOT).contains("ollama")) {
            return "Model not available on configured API";
        }
        return detail;
    }
}
