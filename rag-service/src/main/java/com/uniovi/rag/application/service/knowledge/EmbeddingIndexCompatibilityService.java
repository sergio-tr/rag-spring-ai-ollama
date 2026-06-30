package com.uniovi.rag.application.service.knowledge;

import com.uniovi.rag.application.service.llm.ProviderAwareEmbeddingService;
import com.uniovi.rag.application.service.llm.catalog.EmbeddingModelCatalogResolver;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.domain.llm.ResolvedLlmConfig;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalRequest;
import com.uniovi.rag.domain.runtime.RagExecutionContext;
import com.uniovi.rag.domain.runtime.RagExecutionContextHolder;
import com.uniovi.rag.infrastructure.persistence.KnowledgeIndexSnapshotRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeIndexSnapshotEntity;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Ensures vector indices were built with the same embedding provider/model as the effective runtime LLM config.
 * No silent fallback and no automatic reindex.
 */
@Service
public class EmbeddingIndexCompatibilityService {

    public static final String NO_COMPATIBLE_INDEX_MESSAGE =
            "No compatible vector index found for provider=%s, embeddingModel=%s. Reindex is required.";

    private final ProviderAwareEmbeddingService embeddingService;
    private final KnowledgeIndexSnapshotRepository snapshotRepository;
    private final KnowledgeSnapshotService knowledgeSnapshotService;
    private final EmbeddingModelCatalogResolver embeddingModelCatalogResolver;

    public EmbeddingIndexCompatibilityService(
            ProviderAwareEmbeddingService embeddingService,
            KnowledgeIndexSnapshotRepository snapshotRepository,
            KnowledgeSnapshotService knowledgeSnapshotService,
            EmbeddingModelCatalogResolver embeddingModelCatalogResolver) {
        this.embeddingService = embeddingService;
        this.snapshotRepository = snapshotRepository;
        this.knowledgeSnapshotService = knowledgeSnapshotService;
        this.embeddingModelCatalogResolver = embeddingModelCatalogResolver;
    }

    public ResolvedLlmConfig effectiveEmbeddingConfig() {
        return embeddingService.resolveEffectiveConfig();
    }

    /**
     * Stamps provider/model from effective runtime embedding resolution (same path as {@link #isProfileCompatible}).
     * For {@link LlmProvider#OPENAI_COMPATIBLE}, legacy Ollama ids in project profiles are replaced with the
     * configured catalog embedding model.
     */
    public Map<String, Object> enrichIndexProfile(Map<String, Object> baseProfile) {
        ResolvedLlmConfig effective = effectiveEmbeddingConfig();
        Map<String, Object> enriched = new LinkedHashMap<>(baseProfile != null ? baseProfile : Map.of());
        String explicitModel = IndexProfileJsonSupport.readEmbeddingModelId(enriched).orElse(null);
        enriched.put(
                IndexProfileJsonSupport.EMBEDDING_MODEL_ID_KEY,
                embeddingService.effectiveEmbeddingModelId(explicitModel));
        enriched.put(IndexProfileJsonSupport.EMBEDDING_PROVIDER_KEY, effective.embeddingProvider().name());
        return enriched;
    }

    public void assertIndexingCompatible(Map<String, Object> indexProfileJsonb) {
        assertProfileCompatible(indexProfileJsonb, effectiveEmbeddingConfig());
    }

    /**
     * Lab evaluation corpus snapshots may target alternate embedding models (Gate 2 matrix). Provider must match;
     * model id is taken from the snapshot profile without requiring deployment default equality.
     */
    public void assertIndexingCompatibleForEvaluationSnapshot(Map<String, Object> indexProfileJsonb) {
        if (indexProfileJsonb == null || indexProfileJsonb.isEmpty()) {
            assertIndexingCompatible(indexProfileJsonb);
            return;
        }
        ResolvedLlmConfig effective = effectiveEmbeddingConfig();
        LlmProvider indexProvider = IndexProfileJsonSupport.resolveEmbeddingProviderOrLegacyDefault(indexProfileJsonb);
        if (indexProvider != effective.embeddingProvider()) {
            throw incompatibleIndex(effective);
        }
        String model = IndexProfileJsonSupport.readEmbeddingModelId(indexProfileJsonb).orElse(null);
        if (model == null || model.isBlank()) {
            assertIndexingCompatible(indexProfileJsonb);
        }
    }

    /**
     * Metadata/deterministic tools: require a compatible active project snapshot before pgvector similarity search.
     * Skipped when execution is unscoped (no project id).
     */
    public void assertToolVectorRetrievalCompatible() {
        ResolvedLlmConfig effective = effectiveEmbeddingConfig();
        Optional<UUID> projectId = projectIdFromExecutionContext();
        if (projectId.isEmpty()) {
            return;
        }
        KnowledgeIndexSnapshotEntity active =
                knowledgeSnapshotService
                        .findActiveProjectSnapshot(projectId.get())
                        .orElseThrow(() -> incompatibleIndex(effective));
        assertProfileCompatible(active.getIndexProfileJsonb(), effective);
    }

    public static boolean isIncompatibleIndexFailure(Throwable throwable) {
        if (!(throwable instanceof ResponseStatusException ex)) {
            return false;
        }
        String reason = ex.getReason();
        return reason != null && reason.contains("No compatible vector index found");
    }

    private static Optional<UUID> projectIdFromExecutionContext() {
        RagExecutionContext ctx = RagExecutionContextHolder.get();
        if (ctx == null || !ctx.restrictsByProject()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(ctx.projectId().trim()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public void assertRetrievalCompatible(RetrievalRequest req) {
        Objects.requireNonNull(req, "req");
        List<UUID> snapshotIds = req.snapshotIds();
        if (snapshotIds == null || snapshotIds.isEmpty()) {
            throw incompatibleIndex(effectiveEmbeddingConfig());
        }
        ResolvedLlmConfig effective = effectiveEmbeddingConfig();
        boolean anyCompatible = false;
        for (UUID snapshotId : snapshotIds) {
            KnowledgeIndexSnapshotEntity snap =
                    snapshotRepository
                            .findById(snapshotId)
                            .orElseThrow(
                                    () ->
                                            incompatibleIndex(effective));
            if (isProfileCompatible(snap.getIndexProfileJsonb(), effective)) {
                anyCompatible = true;
            }
        }
        if (!anyCompatible) {
            throw incompatibleIndex(effective);
        }
        req.denseRetrievalEmbeddingModelId()
                .filter(model -> !model.isBlank())
                .ifPresent(
                        snapshotModel -> {
                            if (!IndexProfileJsonSupport.normalizeEmbeddingKey(snapshotModel)
                                    .equals(
                                            IndexProfileJsonSupport.normalizeEmbeddingKey(
                                                    effective.embeddingModel()))) {
                                throw incompatibleIndex(effective);
                            }
                        });
    }

    public void assertProfileCompatible(Map<String, Object> indexProfileJsonb, ResolvedLlmConfig effective) {
        if (!isProfileCompatible(indexProfileJsonb, effective)) {
            throw incompatibleIndex(effective);
        }
    }

    public boolean isProfileCompatible(Map<String, Object> indexProfileJsonb, ResolvedLlmConfig effective) {
        if (effective == null || effective.embeddingProvider() == null) {
            return false;
        }
        if (indexProfileJsonb == null || indexProfileJsonb.isEmpty()) {
            return false;
        }
        LlmProvider indexProvider = IndexProfileJsonSupport.resolveEmbeddingProviderOrLegacyDefault(indexProfileJsonb);
        if (indexProvider != effective.embeddingProvider()) {
            return false;
        }
        String profileModel = IndexProfileJsonSupport.readEmbeddingModelId(indexProfileJsonb).orElse(null);
        if (profileModel == null || profileModel.isBlank()) {
            return false;
        }
        String resolvedProfileModel = embeddingService.effectiveEmbeddingModelId(profileModel);
        String resolvedEffectiveModel = embeddingService.effectiveEmbeddingModelId(null);
        return IndexProfileJsonSupport.normalizeEmbeddingKey(resolvedProfileModel)
                .equals(IndexProfileJsonSupport.normalizeEmbeddingKey(resolvedEffectiveModel));
    }

    public static ResponseStatusException incompatibleIndex(ResolvedLlmConfig effective) {
        LlmProvider provider = effective != null ? effective.embeddingProvider() : null;
        String model = effective != null ? effective.embeddingModel() : "";
        return new ResponseStatusException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                String.format(NO_COMPATIBLE_INDEX_MESSAGE, provider, model));
    }
}
