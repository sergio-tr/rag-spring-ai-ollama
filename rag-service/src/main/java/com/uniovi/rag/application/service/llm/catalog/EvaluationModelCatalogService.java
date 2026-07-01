package com.uniovi.rag.application.service.llm.catalog;

import com.uniovi.rag.application.service.config.llm.ResolvedLlmConfigResolver;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.domain.llm.catalog.LlmModelReasonCodes;
import com.uniovi.rag.domain.llm.ResolvedLlmConfig;
import com.uniovi.rag.domain.llm.catalog.LlmCatalogRuntimeStatus;
import com.uniovi.rag.domain.llm.catalog.LlmModelCapability;
import com.uniovi.rag.interfaces.rest.dto.llm.catalog.LlmCatalogModelDto;
import com.uniovi.rag.interfaces.rest.dto.llm.catalog.LlmCatalogResponseDto;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Catalog-backed validation for Lab evaluation model selection (Phase 5). */
@Service
public class EvaluationModelCatalogService {

    public static final String EMBEDDING_NOT_IN_CATALOG = LlmModelReasonCodes.LLM_MODEL_NOT_CONFIGURED;
    public static final String CHAT_NOT_IN_CATALOG = LlmModelReasonCodes.LLM_MODEL_NOT_CONFIGURED;
    public static final String NO_COMPATIBLE_EMBEDDING_CONFIGURED =
            LlmModelReasonCodes.EMBEDDING_MODEL_INCOMPATIBLE_WITH_VECTOR_STORE;

    private final ResolvedLlmConfigResolver configResolver;
    private final LlmCatalogApiService llmCatalogApiService;

    public EvaluationModelCatalogService(
            ResolvedLlmConfigResolver configResolver, LlmCatalogApiService llmCatalogApiService) {
        this.configResolver = configResolver;
        this.llmCatalogApiService = llmCatalogApiService;
    }

    public LlmCatalogResponseDto listForUser(UUID userId, LlmModelCapability capability, boolean includeRuntimeStatus) {
        ResolvedLlmConfig config = requireConfig(userId);
        LlmProvider provider = providerForCapability(config, capability);
        return llmCatalogApiService.listCatalog(provider, capability, null, includeRuntimeStatus);
    }

    public boolean hasCompatibleEmbeddingModels(UUID userId) {
        return listForUser(userId, LlmModelCapability.EMBEDDING, false).models().stream()
                .anyMatch(EvaluationModelCatalogService::isVectorStoreCompatible);
    }

    public void assertChatModelInCatalog(UUID userId, String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return;
        }
        ResolvedLlmConfig config = requireConfig(userId);
        Optional<LlmCatalogModelDto> match =
                findModel(listForUser(userId, LlmModelCapability.CHAT, false), config.chatProvider(), modelName.trim());
        if (match.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, CHAT_NOT_IN_CATALOG);
        }
    }

    public void assertEmbeddingCompatibleWithVectorStore(UUID userId, String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return;
        }
        ResolvedLlmConfig config = requireConfig(userId);
        LlmCatalogModelDto match =
                findModel(
                                listForUser(userId, LlmModelCapability.EMBEDDING, false),
                                config.embeddingProvider(),
                                modelName.trim())
                        .orElseThrow(
                                () -> new ResponseStatusException(HttpStatus.BAD_REQUEST, EMBEDDING_NOT_IN_CATALOG));
        if (!isVectorStoreCompatible(match)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    LlmModelReasonCodes.EMBEDDING_MODEL_INCOMPATIBLE_WITH_VECTOR_STORE);
        }
    }

    public void assertHasCompatibleEmbeddingWhenRequired(UUID userId) {
        if (!hasCompatibleEmbeddingModels(userId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, NO_COMPATIBLE_EMBEDDING_CONFIGURED);
        }
    }

    static boolean isVectorStoreCompatible(LlmCatalogModelDto model) {
        return Boolean.TRUE.equals(model.compatibleWithCurrentVectorStore());
    }

    static boolean isRuntimeEvalSelectable(LlmCatalogModelDto model) {
        return model.runtimeStatus() == null
                || model.runtimeStatus() == LlmCatalogRuntimeStatus.UNKNOWN
                || model.runtimeStatus() == LlmCatalogRuntimeStatus.CONFIGURED
                || model.runtimeStatus() == LlmCatalogRuntimeStatus.NOT_PROBED
                || model.runtimeStatus() == LlmCatalogRuntimeStatus.AVAILABLE;
    }

    private static Optional<LlmCatalogModelDto> findModel(
            LlmCatalogResponseDto catalog, LlmProvider provider, String modelName) {
        if (catalog == null || catalog.models() == null) {
            return Optional.empty();
        }
        return catalog.models().stream()
                .filter(m -> m.provider() == provider && modelName.equals(m.modelName()))
                .findFirst();
    }

    private ResolvedLlmConfig requireConfig(UUID userId) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return configResolver.resolve(userId, null, null);
    }

    private static LlmProvider providerForCapability(ResolvedLlmConfig config, LlmModelCapability capability) {
        return capability == LlmModelCapability.EMBEDDING ? config.embeddingProvider() : config.chatProvider();
    }
}
