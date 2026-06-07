package com.uniovi.rag.application.service.model;

import com.uniovi.rag.domain.product.ModelRegistryAvailabilityStatus;
import com.uniovi.rag.domain.AllowedModelType;
import com.uniovi.rag.domain.product.ProductDemoModel;
import com.uniovi.rag.infrastructure.llm.ollama.OllamaApiClient;
import com.uniovi.rag.interfaces.rest.dto.modelregistry.ModelRegistryItemDto;
import com.uniovi.rag.interfaces.rest.dto.modelregistry.ModelRegistryResponseDto;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Read-only curated model registry + Ollama presence checks. Pull is delegated to {@link com.uniovi.rag.application.service.async.AsyncTaskService}
 * after validating the model id is in {@link ProductDemoModel}.
 */
@Service
public class ModelRegistryService {

    private static final long EMBEDDING_PROBE_TIMEOUT_MS = 10_000;

    private final OllamaApiClient ollamaApiClient;

    public ModelRegistryService(OllamaApiClient ollamaApiClient) {
        this.ollamaApiClient = ollamaApiClient;
    }

    @Transactional(readOnly = true)
    public ModelRegistryResponseDto snapshot() {
        OllamaTagsResult tags = loadTags();
        List<ModelRegistryItemDto> llm =
                ProductDemoModel.llmModels().stream().map(m -> buildSnapshotRow(m, tags)).toList();
        List<ModelRegistryItemDto> emb =
                ProductDemoModel.embeddingModels().stream().map(m -> buildSnapshotRow(m, tags)).toList();
        return new ModelRegistryResponseDto(tags.reachable(), tags.errorMessage(), llm, emb);
    }

    @Transactional(readOnly = true)
    public ModelRegistryItemDto check(String rawModelId, Boolean probeEmbeddingFlag) {
        ProductDemoModel model =
                ProductDemoModel.resolve(rawModelId)
                        .orElseThrow(
                                () -> new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST, "MODEL_NOT_IN_PRODUCT_REGISTRY"));
        boolean probe =
                model.modelType() != AllowedModelType.EMBEDDING
                        || probeEmbeddingFlag == null
                        || Boolean.TRUE.equals(probeEmbeddingFlag);
        OllamaTagsResult tags = loadTags();
        if (!tags.reachable()) {
            return new ModelRegistryItemDto(
                    model.modelId(),
                    model.modelType(),
                    ModelRegistryAvailabilityStatus.ERROR,
                    tags.errorMessage(),
                    null);
        }
        boolean installed = OllamaInstalledModelMatcher.matchesInstalledName(model.modelId(), tags.installed());
        if (!installed) {
            return new ModelRegistryItemDto(
                    model.modelId(),
                    model.modelType(),
                    ModelRegistryAvailabilityStatus.MISSING,
                    "Model not installed locally in Ollama",
                    null);
        }
        if (model.modelType() == AllowedModelType.EMBEDDING && probe) {
            List<String> matches = OllamaInstalledModelMatcher.findMatchingInstalledNames(model.modelId(), tags.installed());
            String probeName = OllamaInstalledModelMatcher.pickBestInstalledName(model.modelId(), matches);
            try {
                var probeResult = ollamaApiClient.probeEmbeddingDetailed(probeName, "ping", EMBEDDING_PROBE_TIMEOUT_MS);
                if (!probeResult.ok()) {
                    return new ModelRegistryItemDto(
                            model.modelId(),
                            model.modelType(),
                            ModelRegistryAvailabilityStatus.ERROR,
                            probeResult.userMessage() != null
                                    ? probeResult.userMessage()
                                    : "Embedding probe failed (model present but did not return embeddings)",
                            false);
                }
                return new ModelRegistryItemDto(
                        model.modelId(), model.modelType(), ModelRegistryAvailabilityStatus.AVAILABLE, null, true);
            } catch (Exception e) {
                return new ModelRegistryItemDto(
                        model.modelId(),
                        model.modelType(),
                        ModelRegistryAvailabilityStatus.ERROR,
                        e.getMessage() != null ? e.getMessage() : "Embedding probe error",
                        false);
            }
        }
        return new ModelRegistryItemDto(model.modelId(), model.modelType(), ModelRegistryAvailabilityStatus.AVAILABLE, null, null);
    }

    /** Validates curated id; caller enqueues async Ollama pull. */
    public void assertPullAllowed(String rawModelId) {
        ProductDemoModel.resolve(rawModelId)
                .orElseThrow(
                        () -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "MODEL_NOT_IN_PRODUCT_REGISTRY"));
    }

    private ModelRegistryItemDto buildSnapshotRow(ProductDemoModel model, OllamaTagsResult tags) {
        if (!tags.reachable()) {
            return new ModelRegistryItemDto(
                    model.modelId(),
                    model.modelType(),
                    ModelRegistryAvailabilityStatus.ERROR,
                    tags.errorMessage(),
                    null);
        }
        boolean installed = OllamaInstalledModelMatcher.matchesInstalledName(model.modelId(), tags.installed());
        if (installed) {
            return new ModelRegistryItemDto(
                    model.modelId(), model.modelType(), ModelRegistryAvailabilityStatus.AVAILABLE, null, null);
        }
        return new ModelRegistryItemDto(
                model.modelId(),
                model.modelType(),
                ModelRegistryAvailabilityStatus.MISSING,
                "Model not installed locally in Ollama",
                null);
    }

    @SuppressWarnings("java:S2142") // interrupt restored
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
