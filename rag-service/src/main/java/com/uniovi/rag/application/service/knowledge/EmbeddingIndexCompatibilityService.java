package com.uniovi.rag.application.service.knowledge;

import com.uniovi.rag.application.service.llm.ProviderAwareEmbeddingService;
import com.uniovi.rag.application.service.llm.catalog.EmbeddingModelCatalogResolver;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.domain.llm.ResolvedLlmConfig;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalRequest;
import com.uniovi.rag.domain.runtime.RagExecutionContext;
import com.uniovi.rag.domain.runtime.RagExecutionContextHolder;
import com.uniovi.rag.domain.runtime.RagSnapshotContextHolder;
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
     * Deployment default embedding model id (no explicit override), used to distinguish genuinely non-default
     * (e.g. Lab benchmark) embedding indices from the normal product default at retrieval time.
     */
    public String deploymentDefaultEmbeddingModelId() {
        return embeddingService.effectiveEmbeddingModelId(null);
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
        String deploymentDefault = embeddingService.effectiveEmbeddingModelId(null);
        String resolved = embeddingService.effectiveEmbeddingModelId(explicitModel);
        if (IndexProfileJsonSupport.embeddingKeysEquivalent(resolved, deploymentDefault)) {
            resolved = deploymentDefault;
        }
        enriched.put(IndexProfileJsonSupport.EMBEDDING_MODEL_ID_KEY, resolved);
        enriched.put(IndexProfileJsonSupport.EMBEDDING_PROVIDER_KEY, effective.embeddingProvider().name());
        return enriched;
    }

    public void assertIndexingCompatible(Map<String, Object> indexProfileJsonb) {
        assertProfileCompatible(indexProfileJsonb, effectiveEmbeddingConfig());
    }

    /**
     * Project document ingestion: compatibility is evaluated against the resolved project index profile, not deployment
     * or user embedding defaults.
     */
    public void assertIndexingCompatibleForProjectIngestion(
            Map<String, Object> indexProfileJsonb,
            ProjectIndexProfileResolver.ResolvedIngestionIndexProfile ingestionProfile) {
        Objects.requireNonNull(ingestionProfile, "ingestionProfile");
        if (indexProfileJsonb == null || indexProfileJsonb.isEmpty()) {
            throw EmbeddingIndexCompatibilityException.noCompatibleVectorIndex(
                    ingestionProfile.embeddingProvider(), ingestionProfile.resolvedEmbeddingModel());
        }
        LlmProvider indexProvider = IndexProfileJsonSupport.resolveEmbeddingProviderOrLegacyDefault(indexProfileJsonb);
        if (indexProvider != ingestionProfile.embeddingProvider()) {
            throw EmbeddingIndexCompatibilityException.noCompatibleVectorIndex(
                    ingestionProfile.embeddingProvider(), ingestionProfile.resolvedEmbeddingModel());
        }
        String profileModel = IndexProfileJsonSupport.readEmbeddingModelId(indexProfileJsonb).orElse("");
        if (profileModel.isBlank()) {
            throw EmbeddingIndexCompatibilityException.noCompatibleVectorIndex(
                    ingestionProfile.embeddingProvider(), ingestionProfile.resolvedEmbeddingModel());
        }
        if (!IndexProfileJsonSupport.embeddingKeysEquivalent(
                profileModel, ingestionProfile.resolvedEmbeddingModel())) {
            throw EmbeddingIndexCompatibilityException.projectIndexProfileMismatch(
                    ingestionProfile.activeProfileEmbeddingModel(),
                    ingestionProfile.resolvedEmbeddingModel(),
                    embeddingService.effectiveEmbeddingModelId(null));
        }
        if (!IndexProfileJsonSupport.embeddingKeysEquivalent(
                profileModel, ingestionProfile.activeProfileEmbeddingModel())) {
            throw EmbeddingIndexCompatibilityException.embeddingModelAliasMismatch(
                    ingestionProfile.activeProfileEmbeddingModel(), profileModel);
        }
    }

    public Map<String, Object> enrichIndexProfileForIngestion(
            Map<String, Object> baseProfile,
            ProjectIndexProfileResolver.ResolvedIngestionIndexProfile ingestionProfile) {
        Objects.requireNonNull(ingestionProfile, "ingestionProfile");
        Map<String, Object> enriched = new LinkedHashMap<>(baseProfile != null ? baseProfile : Map.of());
        enriched.put(
                IndexProfileJsonSupport.EMBEDDING_MODEL_ID_KEY, ingestionProfile.resolvedEmbeddingModel());
        enriched.put(
                IndexProfileJsonSupport.EMBEDDING_PROVIDER_KEY, ingestionProfile.embeddingProvider().name());
        return enriched;
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
        if (!RagSnapshotContextHolder.activeSnapshotIds().isEmpty()) {
            boolean anyCompatible = false;
            for (String snapshotIdText : RagSnapshotContextHolder.activeSnapshotIds()) {
                UUID snapshotId;
                try {
                    snapshotId = UUID.fromString(snapshotIdText);
                } catch (IllegalArgumentException ex) {
                    continue;
                }
                KnowledgeIndexSnapshotEntity snap = snapshotRepository.findById(snapshotId).orElse(null);
                if (snap != null && isEvaluationSnapshotCompatible(snap.getIndexProfileJsonb(), effective)) {
                    anyCompatible = true;
                }
            }
            if (!anyCompatible) {
                throw incompatibleIndex(effective);
            }
            return;
        }
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
        Optional<String> boundSnapshotModel =
                req.denseRetrievalEmbeddingModelId().filter(model -> model != null && !model.isBlank());
        boolean anyCompatible = false;
        for (UUID snapshotId : snapshotIds) {
            KnowledgeIndexSnapshotEntity snap =
                    snapshotRepository
                            .findById(snapshotId)
                            .orElseThrow(
                                    () ->
                                            incompatibleIndex(effective));
            if (boundSnapshotModel.isPresent()) {
                if (isProfileCompatibleWithSnapshotModel(
                        snap.getIndexProfileJsonb(), effective, boundSnapshotModel.get())) {
                    anyCompatible = true;
                }
            } else if (isProfileCompatible(snap.getIndexProfileJsonb(), effective)) {
                anyCompatible = true;
            }
        }
        if (!anyCompatible) {
            throw incompatibleIndex(effective);
        }
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
        return IndexProfileJsonSupport.embeddingKeysEquivalent(resolvedProfileModel, resolvedEffectiveModel);
    }

    /**
     * Lab evaluation snapshots may bind a non-default embedding model (e.g. bge-m3) while deployment defaults differ.
     * Provider must match; profile model must be present and align with the bound snapshot model.
     */
    boolean isProfileCompatibleWithSnapshotModel(
            Map<String, Object> indexProfileJsonb, ResolvedLlmConfig effective, String expectedSnapshotModel) {
        if (!isEvaluationSnapshotCompatible(indexProfileJsonb, effective)) {
            return false;
        }
        String profileModel = IndexProfileJsonSupport.readEmbeddingModelId(indexProfileJsonb).orElse("");
        String resolvedProfileModel = embeddingService.effectiveEmbeddingModelId(profileModel);
        String resolvedExpectedModel = embeddingService.effectiveEmbeddingModelId(expectedSnapshotModel);
        return IndexProfileJsonSupport.embeddingKeysEquivalent(resolvedProfileModel, resolvedExpectedModel);
    }

    private boolean isEvaluationSnapshotCompatible(Map<String, Object> indexProfileJsonb, ResolvedLlmConfig effective) {
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
        return profileModel != null && !profileModel.isBlank();
    }

    public static ResponseStatusException incompatibleIndex(ResolvedLlmConfig effective) {
        LlmProvider provider = effective != null ? effective.embeddingProvider() : null;
        String model = effective != null ? effective.embeddingModel() : "";
        return new ResponseStatusException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                String.format(NO_COMPATIBLE_INDEX_MESSAGE, provider, model));
    }
}
