package com.uniovi.rag.application.service.model;

import com.uniovi.rag.application.port.llm.catalog.LlmModelCatalogPort;
import com.uniovi.rag.domain.AllowedModelType;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.domain.llm.catalog.LlmCatalogEntry;
import com.uniovi.rag.domain.llm.catalog.LlmCatalogQuery;
import com.uniovi.rag.domain.llm.catalog.LlmModelCapability;
import com.uniovi.rag.domain.product.ModelRegistryAvailabilityStatus;
import com.uniovi.rag.infrastructure.llm.ollama.OllamaApiClient;
import com.uniovi.rag.interfaces.rest.dto.modelregistry.ModelRegistryItemDto;
import com.uniovi.rag.interfaces.rest.dto.modelregistry.ModelRegistryResponseDto;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Read-only configured model registry with optional Ollama presence checks.
 * Recommended models are derived from the properties-backed LLM catalog - not hardcoded demo enums.
 */
@Service
public class ModelRegistryService {

    private static final long EMBEDDING_PROBE_TIMEOUT_MS = 10_000;

    private final OllamaApiClient ollamaApiClient;
    private final LlmModelCatalogPort modelCatalog;

    public ModelRegistryService(OllamaApiClient ollamaApiClient, LlmModelCatalogPort modelCatalog) {
        this.ollamaApiClient = ollamaApiClient;
        this.modelCatalog = modelCatalog;
    }

    @Transactional(readOnly = true)
    public ModelRegistryResponseDto snapshot() {
        OllamaTagsResult tags = loadTags();
        List<ModelRegistryItemDto> llm = new ArrayList<>();
        List<ModelRegistryItemDto> embedding = new ArrayList<>();
        for (LlmCatalogEntry entry : modelCatalog.listConfigured(new LlmCatalogQuery(null, null, null, null))) {
            if (!entry.available()) {
                continue;
            }
            ModelRegistryItemDto row = buildSnapshotRow(entry, tags);
            if (entry.capability() == LlmModelCapability.CHAT) {
                llm.add(row);
            } else if (entry.capability() == LlmModelCapability.EMBEDDING) {
                embedding.add(row);
            }
        }
        llm.sort(Comparator.comparing(ModelRegistryItemDto::modelId));
        embedding.sort(Comparator.comparing(ModelRegistryItemDto::modelId));
        return new ModelRegistryResponseDto(tags.reachable(), tags.errorMessage(), List.copyOf(llm), List.copyOf(embedding));
    }

    @Transactional(readOnly = true)
    public ModelRegistryItemDto check(String rawModelId, Boolean probeEmbeddingFlag) {
        LlmCatalogEntry entry = resolveCatalogEntry(rawModelId)
                .orElseThrow(
                        () -> new ResponseStatusException(
                                HttpStatus.BAD_REQUEST, "MODEL_NOT_IN_CONFIGURED_CATALOG"));
        AllowedModelType modelType =
                entry.capability() == LlmModelCapability.EMBEDDING
                        ? AllowedModelType.EMBEDDING
                        : AllowedModelType.LLM;
        boolean probe =
                modelType != AllowedModelType.EMBEDDING
                        || probeEmbeddingFlag == null
                        || Boolean.TRUE.equals(probeEmbeddingFlag);
        if (entry.provider() != LlmProvider.OLLAMA_NATIVE) {
            return new ModelRegistryItemDto(
                    entry.modelName(), modelType, ModelRegistryAvailabilityStatus.AVAILABLE, null, null);
        }
        OllamaTagsResult tags = loadTags();
        if (!tags.reachable()) {
            return new ModelRegistryItemDto(
                    entry.modelName(),
                    modelType,
                    ModelRegistryAvailabilityStatus.ERROR,
                    tags.errorMessage(),
                    null);
        }
        boolean installed = OllamaInstalledModelMatcher.matchesInstalledName(entry.modelName(), tags.installed());
        if (!installed) {
            return new ModelRegistryItemDto(
                    entry.modelName(),
                    modelType,
                    ModelRegistryAvailabilityStatus.MISSING,
                    "Model not installed locally in Ollama",
                    null);
        }
        if (modelType == AllowedModelType.EMBEDDING && probe) {
            List<String> matches = OllamaInstalledModelMatcher.findMatchingInstalledNames(entry.modelName(), tags.installed());
            String probeName = OllamaInstalledModelMatcher.pickBestInstalledName(entry.modelName(), matches);
            try {
                var probeResult = ollamaApiClient.probeEmbeddingDetailed(probeName, "ping", EMBEDDING_PROBE_TIMEOUT_MS);
                if (!probeResult.ok()) {
                    return new ModelRegistryItemDto(
                            entry.modelName(),
                            modelType,
                            ModelRegistryAvailabilityStatus.ERROR,
                            probeResult.userMessage() != null
                                    ? probeResult.userMessage()
                                    : "Embedding probe failed (model present but did not return embeddings)",
                            false);
                }
                return new ModelRegistryItemDto(
                        entry.modelName(), modelType, ModelRegistryAvailabilityStatus.AVAILABLE, null, true);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return new ModelRegistryItemDto(
                        entry.modelName(),
                        modelType,
                        ModelRegistryAvailabilityStatus.ERROR,
                        "Interrupted during embedding probe",
                        false);
            } catch (Exception e) {
                return new ModelRegistryItemDto(
                        entry.modelName(),
                        modelType,
                        ModelRegistryAvailabilityStatus.ERROR,
                        e.getMessage() != null ? e.getMessage() : "Embedding probe error",
                        false);
            }
        }
        return new ModelRegistryItemDto(entry.modelName(), modelType, ModelRegistryAvailabilityStatus.AVAILABLE, null, null);
    }

    /** Validates catalog id; caller enqueues async Ollama pull when provider is local. */
    public void assertPullAllowed(String rawModelId) {
        LlmCatalogEntry entry =
                resolveCatalogEntry(rawModelId)
                        .orElseThrow(
                                () -> new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST, "MODEL_NOT_IN_CONFIGURED_CATALOG"));
        if (entry.provider() != LlmProvider.OLLAMA_NATIVE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "PULL_ONLY_SUPPORTED_FOR_LOCAL_MODEL_SERVER");
        }
    }

    private Optional<LlmCatalogEntry> resolveCatalogEntry(String rawModelId) {
        if (rawModelId == null || rawModelId.isBlank()) {
            return Optional.empty();
        }
        String modelId = rawModelId.trim();
        for (LlmProvider provider : LlmProvider.values()) {
            Optional<LlmCatalogEntry> chat = modelCatalog.find(provider, modelId, LlmModelCapability.CHAT);
            if (chat.isPresent() && chat.get().available()) {
                return chat;
            }
            Optional<LlmCatalogEntry> emb = modelCatalog.find(provider, modelId, LlmModelCapability.EMBEDDING);
            if (emb.isPresent() && emb.get().available()) {
                return emb;
            }
        }
        return Optional.empty();
    }

    private ModelRegistryItemDto buildSnapshotRow(LlmCatalogEntry entry, OllamaTagsResult tags) {
        AllowedModelType modelType =
                entry.capability() == LlmModelCapability.EMBEDDING
                        ? AllowedModelType.EMBEDDING
                        : AllowedModelType.LLM;
        if (entry.provider() != LlmProvider.OLLAMA_NATIVE) {
            return new ModelRegistryItemDto(
                    entry.modelName(), modelType, ModelRegistryAvailabilityStatus.AVAILABLE, null, null);
        }
        if (!tags.reachable()) {
            return new ModelRegistryItemDto(
                    entry.modelName(),
                    modelType,
                    ModelRegistryAvailabilityStatus.ERROR,
                    tags.errorMessage(),
                    null);
        }
        boolean installed = OllamaInstalledModelMatcher.matchesInstalledName(entry.modelName(), tags.installed());
        if (installed) {
            return new ModelRegistryItemDto(
                    entry.modelName(), modelType, ModelRegistryAvailabilityStatus.AVAILABLE, null, null);
        }
        return new ModelRegistryItemDto(
                entry.modelName(),
                modelType,
                ModelRegistryAvailabilityStatus.MISSING,
                "Model not installed locally in Ollama",
                null);
    }

    @SuppressWarnings("java:S2142")
    private OllamaTagsResult loadTags() {
        try {
            Set<String> names = ollamaApiClient.listModelNames();
            return new OllamaTagsResult(true, null, names != null ? names : Collections.emptySet());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new OllamaTagsResult(false, "Interrupted while listing Ollama models", Collections.emptySet());
        } catch (Exception e) {
            String msg = Optional.ofNullable(e.getMessage()).orElse("Failed to list Ollama models");
            return new OllamaTagsResult(false, msg, Collections.emptySet());
        }
    }

    private record OllamaTagsResult(boolean reachable, String errorMessage, Set<String> installed) {}
}
