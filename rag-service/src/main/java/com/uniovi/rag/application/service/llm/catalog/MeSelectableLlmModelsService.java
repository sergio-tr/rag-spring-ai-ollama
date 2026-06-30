package com.uniovi.rag.application.service.llm.catalog;

import com.uniovi.rag.application.service.config.llm.ResolvedLlmConfigResolver;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.domain.llm.ResolvedLlmConfig;
import com.uniovi.rag.domain.llm.catalog.LlmCatalogRuntimeStatus;
import com.uniovi.rag.domain.llm.catalog.LlmModelReasonCodes;
import com.uniovi.rag.application.service.llm.catalog.LlmModelRuntimeReasonSupport;
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

/** User-scoped chat model picker backed by the properties catalog (Phase 2). */
@Service
public class MeSelectableLlmModelsService {

    private final ResolvedLlmConfigResolver configResolver;
    private final LlmCatalogApiService llmCatalogApiService;

    public MeSelectableLlmModelsService(
            ResolvedLlmConfigResolver configResolver, LlmCatalogApiService llmCatalogApiService) {
        this.configResolver = configResolver;
        this.llmCatalogApiService = llmCatalogApiService;
    }

    public MeSelectableLlmModelsResponseDto listForUser(UUID userId, LlmModelCapability capability) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        if (capability != LlmModelCapability.CHAT) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Only CHAT capability is supported for user-selectable models");
        }
        ResolvedLlmConfig config = configResolver.resolve(userId, null, null);
        LlmProvider effectiveProvider = config.chatProvider();

        LlmCatalogResponseDto catalog =
                llmCatalogApiService.listCatalog(
                        effectiveProvider, LlmModelCapability.CHAT, true, true);

        List<MeSelectableLlmModelDto> models = new ArrayList<>();
        for (LlmCatalogModelDto entry : catalog.models()) {
            if (!entry.available() || !entry.selectableByUser()) {
                continue;
            }
            if (entry.capability() != LlmModelCapability.CHAT) {
                continue;
            }
            if (entry.provider() != effectiveProvider) {
                continue;
            }
            models.add(toSelectable(entry));
        }
        ensureConfiguredRuntimeChatModel(models, config, effectiveProvider);
        models.sort(Comparator.comparing(MeSelectableLlmModelDto::modelName));
        return new MeSelectableLlmModelsResponseDto(effectiveProvider, capability, List.copyOf(models));
    }

    private void ensureConfiguredRuntimeChatModel(
            List<MeSelectableLlmModelDto> models, ResolvedLlmConfig config, LlmProvider effectiveProvider) {
        String chatModel = config.chatModel();
        if (chatModel == null || chatModel.isBlank()) {
            return;
        }
        String configured = chatModel.trim();
        if (models.stream().anyMatch(m -> configured.equals(m.modelName()))) {
            return;
        }
        LlmCatalogResponseDto fullCatalog =
                llmCatalogApiService.listCatalog(
                        effectiveProvider, LlmModelCapability.CHAT, null, true);
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
                        LlmModelReasonCodes.LLM_MODEL_NOT_CONFIGURED,
                        false,
                        LlmCatalogRuntimeStatus.UNKNOWN));
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
