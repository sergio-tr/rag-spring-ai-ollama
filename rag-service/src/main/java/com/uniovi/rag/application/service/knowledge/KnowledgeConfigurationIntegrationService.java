package com.uniovi.rag.application.service.knowledge;

import com.uniovi.rag.application.config.ConfigResolverService;
import com.uniovi.rag.application.service.ResolvedConfigSnapshotApplicationService;
import com.uniovi.rag.domain.config.indexing.ReindexImpactLevel;
import com.uniovi.rag.domain.config.runtime.ResolvedRuntimeConfig;
import com.uniovi.rag.domain.knowledge.CorpusScope;
import com.uniovi.rag.domain.knowledge.KnowledgeBuildProjection;
import com.uniovi.rag.domain.knowledge.KnowledgeOperationKind;
import com.uniovi.rag.domain.knowledge.KnowledgeReindexDecision;
import com.uniovi.rag.domain.knowledge.KnowledgeReindexKind;
import com.uniovi.rag.domain.knowledge.MaterializationStrategy;
import com.uniovi.rag.domain.knowledge.ProjectIndexProfile;
import com.uniovi.rag.domain.knowledge.IndexSnapshotStatus;
import com.uniovi.rag.application.service.runtime.config.IndexSnapshotCapabilities;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeIndexSnapshotEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.ResolvedConfigSnapshotEntity;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Sole orchestration center for configuration-aware knowledge rebuild/preview.
 */
@Service
public class KnowledgeConfigurationIntegrationService {

    private final ConfigResolverService configResolverService;
    private final KnowledgeBuildProjectionMapper knowledgeBuildProjectionMapper;
    private final ResolvedConfigSnapshotApplicationService resolvedConfigSnapshotApplicationService;
    private final KnowledgePipelineOrchestrator knowledgePipelineOrchestrator;
    private final ReindexService reindexService;
    private final KnowledgeSnapshotService knowledgeSnapshotService;

    public KnowledgeConfigurationIntegrationService(
            ConfigResolverService configResolverService,
            KnowledgeBuildProjectionMapper knowledgeBuildProjectionMapper,
            ResolvedConfigSnapshotApplicationService resolvedConfigSnapshotApplicationService,
            KnowledgePipelineOrchestrator knowledgePipelineOrchestrator,
            ReindexService reindexService,
            KnowledgeSnapshotService knowledgeSnapshotService) {
        this.configResolverService = configResolverService;
        this.knowledgeBuildProjectionMapper = knowledgeBuildProjectionMapper;
        this.resolvedConfigSnapshotApplicationService = resolvedConfigSnapshotApplicationService;
        this.knowledgePipelineOrchestrator = knowledgePipelineOrchestrator;
        this.reindexService = reindexService;
        this.knowledgeSnapshotService = knowledgeSnapshotService;
    }

    @Transactional(readOnly = true)
    public KnowledgeRebuildPreviewResult previewRebuild(KnowledgeConfigurationOperationInput input) {
        validateCorpusInput(input);
        if (input.operationKind() != KnowledgeOperationKind.PREVIEW) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "operationKind must be PREVIEW");
        }
        ResolvedRuntimeConfig resolved = configResolverService.preview(input.toRuntimeConfigResolutionInput());
        KnowledgeBuildProjection projection = knowledgeBuildProjectionMapper.fromResolvedRuntimeConfig(resolved);
        KnowledgeReindexDecision decision =
                computeReindexDecision(
                        projection,
                        input.corpusScope(),
                        input.conversationId(),
                        input.projectId());
        return new KnowledgeRebuildPreviewResult(projection, decision);
    }

    @Transactional
    public KnowledgeRebuildExecuteResult executeRebuild(KnowledgeConfigurationOperationInput input) {
        validateCorpusInput(input);
        if (input.operationKind() != KnowledgeOperationKind.EXECUTE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "operationKind must be EXECUTE");
        }
        if (input.explicitResolvedConfigSnapshotId() != null) {
            return executeWithPinnedSnapshot(input);
        }
        return executeWithResolveAndPersist(input);
    }

    /**
     * Shared by PREVIEW and EXECUTE. See architecture plan §13 (CASE 1–4).
     */
    public KnowledgeReindexDecision computeReindexDecision(
            KnowledgeBuildProjection projection,
            CorpusScope corpusScope,
            UUID conversationId,
            UUID projectId) {
        Optional<KnowledgeReindexDecision> metadataUpgrade =
                metadataSnapshotUpgradeDecision(projection, corpusScope, conversationId, projectId);
        if (metadataUpgrade.isPresent()) {
            return metadataUpgrade.get();
        }
        Optional<KnowledgeReindexDecision> profileUpgrade =
                indexProfileSnapshotUpgradeDecision(projection, corpusScope, conversationId, projectId);
        if (profileUpgrade.isPresent()) {
            return profileUpgrade.get();
        }
        if (projection.reindexImpact() == null
                || projection.reindexImpact().level() == ReindexImpactLevel.NO_REINDEX) {
            return new KnowledgeReindexDecision(KnowledgeReindexKind.NO_OP);
        }
        ReindexImpactLevel level = projection.reindexImpact().level();
        if (level == ReindexImpactLevel.SOFT_REINDEX) {
            if (!knowledgePipelineOrchestrator.hasReadyDocumentsInScope(projectId, corpusScope, conversationId)) {
                return new KnowledgeReindexDecision(KnowledgeReindexKind.NO_OP);
            }
            if (!knowledgePipelineOrchestrator.scopeHasRequiresReindex(projectId, corpusScope, conversationId)) {
                return new KnowledgeReindexDecision(KnowledgeReindexKind.NO_OP);
            }
            return new KnowledgeReindexDecision(KnowledgeReindexKind.SOFT_REBUILD);
        }
        if (level == ReindexImpactLevel.HARD_REINDEX) {
            if (!knowledgePipelineOrchestrator.hasReadyDocumentsInScope(projectId, corpusScope, conversationId)) {
                return new KnowledgeReindexDecision(KnowledgeReindexKind.NO_OP);
            }
            return new KnowledgeReindexDecision(KnowledgeReindexKind.HARD_REBUILD);
        }
        return new KnowledgeReindexDecision(KnowledgeReindexKind.NO_OP);
    }

    /**
     * Project index capability ({@code supportsMetadata} on the active snapshot) is authoritative at creation
     * time. Preset runtime flags must not silently upgrade snapshot metadata capability.
     */
    private Optional<KnowledgeReindexDecision> metadataSnapshotUpgradeDecision(
            KnowledgeBuildProjection projection,
            CorpusScope corpusScope,
            UUID conversationId,
            UUID projectId) {
        return Optional.empty();
    }

    /**
     * When the resolved projection targets an index profile (materialization/metadata/chunking) that has no ACTIVE
     * project snapshot yet, force a hard rebuild so alternate materializations can coexist per profile hash.
     */
    private Optional<KnowledgeReindexDecision> indexProfileSnapshotUpgradeDecision(
            KnowledgeBuildProjection projection,
            CorpusScope corpusScope,
            UUID conversationId,
            UUID projectId) {
        if (corpusScope != CorpusScope.PROJECT_SHARED || projection == null) {
            return Optional.empty();
        }
        MaterializationStrategy strategy =
                projection.materializationStrategy() != null
                        ? projection.materializationStrategy()
                        : MaterializationStrategy.CHUNK_LEVEL;
        boolean metadataForHash = projection.metadataExtractionEnabled();
        Optional<KnowledgeIndexSnapshotEntity> activeSnapshot =
                knowledgeSnapshotService.findActiveProjectSnapshot(projectId);
        if (activeSnapshot.isPresent() && metadataForHash) {
            Boolean snapSupportsMetadata =
                    IndexSnapshotCapabilities.fromIndexProfile(
                                    activeSnapshot.get().getIndexProfileJsonb())
                            .supportsMetadata();
            if (!Boolean.TRUE.equals(snapSupportsMetadata)) {
                metadataForHash = false;
            }
        }
        String expectedHash =
                ProjectIndexProfile.computeProfileHash(
                        strategy,
                        metadataForHash,
                        "",
                        projection.embeddingModelId(),
                        projection.chunkMaxChars(),
                        projection.chunkOverlap());
        Optional<KnowledgeIndexSnapshotEntity> compatible =
                knowledgeSnapshotService.findCompatibleProjectSnapshot(
                        projectId,
                        snap ->
                                snap.getStatus() == IndexSnapshotStatus.ACTIVE
                                        && expectedHash.equals(snap.getIndexProfileHash()));
        if (compatible.isPresent()) {
            return Optional.empty();
        }
        if (!knowledgePipelineOrchestrator.hasReadyDocumentsInScope(projectId, corpusScope, conversationId)) {
            return Optional.empty();
        }
        return Optional.of(new KnowledgeReindexDecision(KnowledgeReindexKind.HARD_REBUILD));
    }

    private KnowledgeRebuildExecuteResult executeWithResolveAndPersist(KnowledgeConfigurationOperationInput input) {
        ResolvedRuntimeConfig resolved = configResolverService.resolve(input.toRuntimeConfigResolutionInput());
        KnowledgeBuildProjection working = knowledgeBuildProjectionMapper.fromResolvedRuntimeConfig(resolved);
        Map<String, Object> nested = knowledgeBuildProjectionMapper.toNestedPayloadMap(working);
        Optional<UUID> conv =
                input.corpusScope() == CorpusScope.CHAT_LOCAL
                        ? Optional.ofNullable(input.conversationId())
                        : Optional.empty();
        Optional<String> corr =
                input.correlationId() == null || input.correlationId().isBlank()
                        ? Optional.empty()
                        : Optional.of(input.correlationId());
        ResolvedConfigSnapshotEntity entity =
                resolvedConfigSnapshotApplicationService.persistForKnowledgeExecute(
                        resolved, input.userId(), input.projectId(), conv, corr, nested);
        KnowledgeBuildProjection projection =
                knowledgeBuildProjectionMapper.fromResolvedRuntimeConfigAndSnapshotIds(
                        resolved, entity.getId(), entity.getConfigHash());
        KnowledgeReindexDecision decision =
                computeReindexDecision(
                        projection,
                        input.corpusScope(),
                        input.conversationId(),
                        input.projectId());
        KnowledgeReindexExecutionResult exec =
                reindexService.executeKnowledgeReindexDecision(
                        decision,
                        projection,
                        input.projectId(),
                        input.corpusScope(),
                        input.conversationId(),
                        entity.getId());
        return KnowledgeRebuildExecuteResult.of(entity.getId(), exec.knowledgeSnapshotId(), exec.reindexEventId());
    }

    private KnowledgeRebuildExecuteResult executeWithPinnedSnapshot(KnowledgeConfigurationOperationInput input) {
        UUID snapshotId = input.explicitResolvedConfigSnapshotId();
        ResolvedConfigSnapshotEntity entity =
                resolvedConfigSnapshotApplicationService.getValidatedSnapshotForKnowledgePin(
                        input.projectId(), input.userId(), snapshotId);
        KnowledgeBuildProjection projection = knowledgeBuildProjectionMapper.fromPersistedSnapshot(entity);
        KnowledgeReindexDecision decision =
                computeReindexDecision(
                        projection,
                        input.corpusScope(),
                        input.conversationId(),
                        input.projectId());
        KnowledgeReindexExecutionResult exec =
                reindexService.executeKnowledgeReindexDecision(
                        decision,
                        projection,
                        input.projectId(),
                        input.corpusScope(),
                        input.conversationId(),
                        entity.getId());
        return KnowledgeRebuildExecuteResult.of(entity.getId(), exec.knowledgeSnapshotId(), exec.reindexEventId());
    }

    private static void validateCorpusInput(KnowledgeConfigurationOperationInput input) {
        if (input.corpusScope() == CorpusScope.CHAT_LOCAL) {
            if (input.conversationId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "conversationId required for CHAT_LOCAL");
            }
        } else if (input.conversationId() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "conversationId must be absent for PROJECT_SHARED");
        }
    }
}
