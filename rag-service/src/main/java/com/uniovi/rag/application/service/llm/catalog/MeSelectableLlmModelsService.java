package com.uniovi.rag.application.service.llm.catalog;

import com.uniovi.rag.application.service.model.ModelGovernanceService;
import com.uniovi.rag.application.service.config.llm.ResolvedLlmConfigResolver;
import com.uniovi.rag.domain.llm.catalog.LlmModelReasonCodes;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.domain.llm.ResolvedLlmConfig;
import com.uniovi.rag.domain.llm.catalog.LlmCatalogRuntimeStatus;
import com.uniovi.rag.domain.llm.catalog.LlmModelReasonCodes;
import com.uniovi.rag.domain.llm.catalog.LlmModelCapability;
import com.uniovi.rag.interfaces.rest.dto.llm.catalog.LlmCatalogModelDto;
import com.uniovi.rag.interfaces.rest.dto.llm.catalog.LlmCatalogResponseDto;
import com.uniovi.rag.interfaces.rest.dto.me.llm.MeSelectableLlmModelDto;
import com.uniovi.rag.interfaces.rest.dto.me.llm.MeSelectableLlmModelsResponseDto;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** User-scoped model picker backed by the properties catalog for CHAT and EMBEDDING. */
@Service
public class MeSelectableLlmModelsService {

    private final ResolvedLlmConfigResolver configResolver;
    private final LlmCatalogApiService llmCatalogApiService;
    private final ModelGovernanceService modelGovernanceService;

    public MeSelectableLlmModelsService(
            ResolvedLlmConfigResolver configResolver,
            LlmCatalogApiService llmCatalogApiService,
            ModelGovernanceService modelGovernanceService) {
        this.configResolver = configResolver;
        this.llmCatalogApiService = llmCatalogApiService;
        this.modelGovernanceService = modelGovernanceService;
    }

    public MeSelectableLlmModelsResponseDto listForUser(UUID userId, LlmModelCapability capability) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        if (capability != LlmModelCapability.CHAT && capability != LlmModelCapability.EMBEDDING) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Only CHAT and EMBEDDING capabilities are supported");
        }
        ResolvedLlmConfig config = configResolver.resolve(userId, null, null);
        LlmProvider effectiveProvider =
                capability == LlmModelCapability.CHAT ? config.chatProvider() : config.embeddingProvider();
        Boolean selectableFilter = capability == LlmModelCapability.CHAT ? Boolean.TRUE : null;

        LlmCatalogResponseDto catalog =
                llmCatalogApiService.listCatalog(effectiveProvider, capability, selectableFilter, true);

        List<MeSelectableLlmModelDto> models = new ArrayList<>();
        for (LlmCatalogModelDto entry : catalog.models()) {
            if (!entry.available()) {
                continue;
            }
            if (entry.capability() != capability) {
                continue;
            }
            if (entry.provider() != effectiveProvider) {
                continue;
            }
            if (capability == LlmModelCapability.CHAT && !entry.selectableByUser()) {
                continue;
            }
            models.add(toSelectable(entry));
        }
        if (capability == LlmModelCapability.CHAT) {
            ensureConfiguredRuntimeChatModel(models, config, effectiveProvider);
            applyGovernanceBlocklist(models, effectiveProvider, LlmModelCapability.CHAT);
        } else {
            ensureConfiguredRuntimeEmbeddingModel(models, config, effectiveProvider);
            applyGovernanceBlocklist(models, effectiveProvider, LlmModelCapability.EMBEDDING);
        }
        models.sort(Comparator.comparing(MeSelectableLlmModelDto::modelName));
        return new MeSelectableLlmModelsResponseDto(effectiveProvider, capability, List.copyOf(models));
    }

    private void ensureConfiguredRuntimeChatModel(
            List<MeSelectableLlmModelDto> models, ResolvedLlmConfig config, LlmProvider effectiveProvider) {
        String chatModel = config.chatModel();
        if (chatModel == null || chatModel.isBlank()) {
            return;
        }
        ensureConfiguredRuntimeModel(
                models, chatModel.trim(), effectiveProvider, LlmModelCapability.CHAT, LlmModelReasonCodes.LLM_MODEL_NOT_CONFIGURED);
    }

    private void ensureConfiguredRuntimeEmbeddingModel(
            List<MeSelectableLlmModelDto> models, ResolvedLlmConfig config, LlmProvider effectiveProvider) {
        String embeddingModel = config.embeddingModel();
        if (embeddingModel == null || embeddingModel.isBlank()) {
            return;
        }
        ensureConfiguredRuntimeModel(
                models,
                embeddingModel.trim(),
                effectiveProvider,
                LlmModelCapability.EMBEDDING,
                LlmModelReasonCodes.LLM_MODEL_NOT_CONFIGURED);
    }

    private void ensureConfiguredRuntimeModel(
            List<MeSelectableLlmModelDto> models,
            String configured,
            LlmProvider effectiveProvider,
            LlmModelCapability capability,
            String notConfiguredReasonCode) {
        if (models.stream().anyMatch(m -> configured.equals(m.modelName()))) {
            return;
        }
        LlmCatalogResponseDto fullCatalog =
                llmCatalogApiService.listCatalog(effectiveProvider, capability, null, true);
        for (LlmCatalogModelDto entry : fullCatalog.models()) {
            if (configured.equals(entry.modelName()) && entry.provider() == effectiveProvider) {
                models.add(toSelectable(entry));
                return;
            }
        }
        models.add(
                new MeSelectableLlmModelDto(
                        configured,
                        configured,
                        effectiveProvider != LlmProvider.OLLAMA_NATIVE,
                        effectiveProvider == LlmProvider.OLLAMA_NATIVE
                                ? "Model not registered in provider catalog"
                                : null,
                        notConfiguredReasonCode,
                        false,
                        LlmCatalogRuntimeStatus.UNKNOWN));
    }

    private void applyGovernanceBlocklist(
            List<MeSelectableLlmModelDto> models, LlmProvider provider, LlmModelCapability capability) {
        models.removeIf(
                model ->
                        capability == LlmModelCapability.CHAT
                                ? !modelGovernanceService.isChatModelGovernanceAllowed(provider, model.modelName())
                                : !modelGovernanceService.isEmbeddingModelGovernanceAllowed(
                                        provider, model.modelName()));
    }

    private static MeSelectableLlmModelDto toSelectable(LlmCatalogModelDto entry) {
        boolean selectable = isRuntimeSelectable(entry.runtimeStatus());
        String disabledReason = selectable ? null : disabledReason(entry);
        String displayName =
                entry.modelName() != null && !entry.modelName().isBlank() ? entry.modelName().trim() : entry.modelName();
        return new MeSelectableLlmModelDto(
                entry.modelName(),
                displayName,
                selectable,
                disabledReason,
                selectable ? null : LlmModelRuntimeReasonSupport.chatDisabledReasonCode(entry),
                entry.usableAsDefault(),
                entry.runtimeStatus());
    }

    static boolean isRuntimeSelectable(LlmCatalogRuntimeStatus runtimeStatus) {
        return runtimeStatus == LlmCatalogRuntimeStatus.UNKNOWN
                || runtimeStatus == LlmCatalogRuntimeStatus.CONFIGURED
                || runtimeStatus == LlmCatalogRuntimeStatus.NOT_PROBED
                || runtimeStatus == LlmCatalogRuntimeStatus.AVAILABLE;
    }

    private static String disabledReason(LlmCatalogModelDto entry) {
        if (entry.runtimeDetail() != null && !entry.runtimeDetail().isBlank()) {
            return entry.runtimeDetail().trim();
        }
        return switch (entry.runtimeStatus()) {
            case UNAVAILABLE -> "Model not available at runtime";
            case PROBE_FAILED -> "Runtime probe failed";
            default -> "Model not selectable";
        };
    }
}
