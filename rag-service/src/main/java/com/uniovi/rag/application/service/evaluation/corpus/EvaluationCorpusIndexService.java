package com.uniovi.rag.application.service.evaluation.corpus;

import com.uniovi.rag.application.service.ResolvedConfigSnapshotApplicationService;
import com.uniovi.rag.application.service.ResolvedConfigSnapshotLinkage;
import com.uniovi.rag.application.service.evaluation.RagBenchmarkHumanReasons;
import com.uniovi.rag.application.service.evaluation.preset.ExperimentalPresetCanonicalCatalog;
import com.uniovi.rag.application.service.evaluation.preset.LabIndexSnapshotCompatibilityService;
import com.uniovi.rag.application.service.evaluation.preset.LabPresetRunGroupKey;
import com.uniovi.rag.application.service.knowledge.KnowledgePipelineOrchestrator;
import com.uniovi.rag.application.service.knowledge.KnowledgeSnapshotService;
import com.uniovi.rag.application.service.knowledge.LabIndexProfileOverrideFactory;
import com.uniovi.rag.application.service.knowledge.ProjectIndexProfileService;
import com.uniovi.rag.domain.knowledge.CorpusScope;
import com.uniovi.rag.domain.knowledge.KnowledgeSnapshotOwnerType;
import com.uniovi.rag.domain.knowledge.ProjectIndexProfile;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeDocumentEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeIndexSnapshotEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Builds and reuses evaluation-corpus-scoped index snapshots for Lab benchmarks. */
@Service
public class EvaluationCorpusIndexService {

    private final EvaluationCorpusApplicationService evaluationCorpusApplicationService;
    private final KnowledgePipelineOrchestrator knowledgePipelineOrchestrator;
    private final KnowledgeSnapshotService knowledgeSnapshotService;
    private final ProjectIndexProfileService projectIndexProfileService;
    private final LabIndexProfileOverrideFactory labIndexProfileOverrideFactory;
    private final ResolvedConfigSnapshotApplicationService resolvedConfigSnapshotApplicationService;
    private final LabIndexSnapshotCompatibilityService indexSnapshotCompatibilityService;
    private final EvaluationCorpusStorageIntegrityService storageIntegrityService;

    public EvaluationCorpusIndexService(
            EvaluationCorpusApplicationService evaluationCorpusApplicationService,
            KnowledgePipelineOrchestrator knowledgePipelineOrchestrator,
            KnowledgeSnapshotService knowledgeSnapshotService,
            ProjectIndexProfileService projectIndexProfileService,
            LabIndexProfileOverrideFactory labIndexProfileOverrideFactory,
            ResolvedConfigSnapshotApplicationService resolvedConfigSnapshotApplicationService,
            LabIndexSnapshotCompatibilityService indexSnapshotCompatibilityService,
            EvaluationCorpusStorageIntegrityService storageIntegrityService) {
        this.evaluationCorpusApplicationService = evaluationCorpusApplicationService;
        this.knowledgePipelineOrchestrator = knowledgePipelineOrchestrator;
        this.knowledgeSnapshotService = knowledgeSnapshotService;
        this.projectIndexProfileService = projectIndexProfileService;
        this.labIndexProfileOverrideFactory = labIndexProfileOverrideFactory;
        this.resolvedConfigSnapshotApplicationService = resolvedConfigSnapshotApplicationService;
        this.indexSnapshotCompatibilityService = indexSnapshotCompatibilityService;
        this.storageIntegrityService = storageIntegrityService;
    }

    /** Manual prepare-index API: builds default-profile corpus snapshot. */
    @Transactional
    public EvaluationCorpusIndexPrepareResult prepareIndex(UUID userId, UUID corpusId) {
        EvaluationCorpusIndexPrepareResult result = prepareDefaultIndex(userId, corpusId);
        if (!result.succeeded()) {
            throw toResponseException(result);
        }
        return result;
    }

    /** Default index profile for the corpus (no preset-specific override). */
    @Transactional
    public EvaluationCorpusIndexPrepareResult prepareDefaultIndex(UUID userId, UUID corpusId) {
        EvaluationCorpusApplicationService.EvaluationCorpusContext context = requireReadyCorpus(userId, corpusId);
        ProjectIndexProfile profile = projectIndexProfileService.ensureDefault(context.indexProjectId());
        return prepareOrReuse(
                userId,
                corpusId,
                context,
                ExperimentalPresetCanonicalCatalog.IndexRequirements.none(),
                null,
                profile,
                null,
                true);
    }

    /** Prepare or reuse a snapshot matching preset index requirements for a run group. */
    @Transactional
    public EvaluationCorpusIndexPrepareResult prepareForPresetRequirements(
            UUID userId,
            UUID corpusId,
            LabPresetRunGroupKey groupKey,
            ExperimentalPresetCanonicalCatalog.IndexRequirements requirements,
            String embeddingModelIdOverride,
            boolean allowBuild) {
        EvaluationCorpusApplicationService.EvaluationCorpusContext context = requireReadyCorpus(userId, corpusId);
        ProjectIndexProfile base = projectIndexProfileService.ensureDefault(context.indexProjectId());
        if (embeddingModelIdOverride != null && !embeddingModelIdOverride.isBlank()) {
            base = labIndexProfileOverrideFactory.withEmbeddingModelId(base, embeddingModelIdOverride);
        }
        ProjectIndexProfile effective =
                labIndexProfileOverrideFactory.buildEffectiveProfile(base, requirements, groupKey);
        return prepareOrReuse(
                userId,
                corpusId,
                context,
                requirements,
                embeddingModelIdOverride,
                effective,
                groupKey,
                allowBuild);
    }

    public Optional<KnowledgeIndexSnapshotEntity> findCompatibleSnapshot(
            UUID corpusId,
            ExperimentalPresetCanonicalCatalog.IndexRequirements requirements,
            String embeddingModelIdOverride) {
        return findCompatibleSnapshot(null, corpusId, null, requirements, embeddingModelIdOverride, null, null);
    }

    public Optional<KnowledgeIndexSnapshotEntity> findCompatibleSnapshot(
            UUID userId,
            UUID corpusId,
            UUID indexProjectId,
            ExperimentalPresetCanonicalCatalog.IndexRequirements requirements,
            String embeddingModelIdOverride,
            ProjectIndexProfile effectiveProfile,
            LabPresetRunGroupKey groupKey) {
        if (corpusId == null) {
            return Optional.empty();
        }
        return knowledgeSnapshotService.findCompatibleCorpusSnapshot(
                corpusId,
                snapshot ->
                        indexSnapshotCompatibilityService
                                .evaluateReuse(
                                        userId,
                                        corpusId,
                                        indexProjectId,
                                        snapshot,
                                        requirements,
                                        embeddingModelIdOverride,
                                        effectiveProfile,
                                        groupKey,
                                        false)
                                .eligible());
    }

    private EvaluationCorpusIndexPrepareResult prepareOrReuse(
            UUID userId,
            UUID corpusId,
            EvaluationCorpusApplicationService.EvaluationCorpusContext context,
            ExperimentalPresetCanonicalCatalog.IndexRequirements requirements,
            String embeddingModelIdOverride,
            ProjectIndexProfile effectiveProfile,
            LabPresetRunGroupKey groupKey,
            boolean allowBuild) {
        evaluationCorpusApplicationService.syncIndexProjectDocuments(userId, corpusId);

        List<KnowledgeDocumentEntity> corpusDocuments = context.documents();
        if (storageIntegrityService.hasReadyDocumentWithMissingBinary(corpusDocuments)) {
            return EvaluationCorpusIndexPrepareResult.failed(
                    LabCorpusReasonCodes.DOCUMENT_BINARY_MISSING,
                    RagBenchmarkHumanReasons.humanize(LabCorpusReasonCodes.DOCUMENT_BINARY_MISSING));
        }

        Optional<KnowledgeIndexSnapshotEntity> compatible =
                findCompatibleSnapshot(
                        userId,
                        corpusId,
                        context.indexProjectId(),
                        requirements,
                        embeddingModelIdOverride,
                        effectiveProfile,
                        groupKey);
        if (compatible.isPresent()) {
            KnowledgeIndexSnapshotEntity snap = compatible.get();
            return EvaluationCorpusIndexPrepareResult.reused(
                    snap.getId(),
                    snap.getResolvedConfigSnapshotId(),
                    snap.getResolvedConfigHash(),
                    snap.getIndexProfileHash());
        }

        if (!allowBuild) {
            String reasonCode = LabCorpusReasonCodes.NO_COMPATIBLE_SNAPSHOT;
            String reasonMessage = "No compatible index exists for the evaluation corpus.";
            Optional<LabIndexSnapshotCompatibilityService.ReuseEligibility> blocked =
                    diagnoseBlockedReuse(
                            userId,
                            corpusId,
                            context.indexProjectId(),
                            requirements,
                            embeddingModelIdOverride,
                            effectiveProfile,
                            groupKey);
            if (blocked.isPresent()) {
                reasonCode = blocked.get().reasonCode();
                reasonMessage = blocked.get().reasonMessage();
            }
            return EvaluationCorpusIndexPrepareResult.failed(reasonCode, reasonMessage);
        }

        UUID indexProjectId = context.indexProjectId();
        if (indexProjectId == null) {
            return EvaluationCorpusIndexPrepareResult.failed(
                    LabCorpusReasonCodes.NO_READY_DOCUMENTS, "Evaluation corpus has no index scope.");
        }

        ResolvedConfigSnapshotLinkage resolved =
                resolvedConfigSnapshotApplicationService.persistIngestionDefaultSnapshotLinkage(
                        userId, indexProjectId, Optional.empty());

        UUID snapshotId;
        try {
            snapshotId =
                    knowledgePipelineOrchestrator.rebuildScopeWithProfileOverride(
                            indexProjectId,
                            CorpusScope.PROJECT_SHARED,
                            null,
                            KnowledgeSnapshotOwnerType.EVALUATION_CORPUS,
                            corpusId,
                            resolved.id(),
                            resolved.configHash(),
                            effectiveProfile);
        } catch (IllegalArgumentException e) {
            if (isResolvedConfigLinkageFailure(e)) {
                return EvaluationCorpusIndexPrepareResult.failed(
                        LabCorpusReasonCodes.RUNTIME_CONFIG_SNAPSHOT_UNAVAILABLE,
                        "Could not resolve runtime configuration for the evaluation index.");
            }
            return EvaluationCorpusIndexPrepareResult.failed(LabCorpusReasonCodes.REINDEX_REQUIRED, e.getMessage());
        } catch (IllegalStateException e) {
            return EvaluationCorpusIndexPrepareResult.failed(LabCorpusReasonCodes.REINDEX_REQUIRED, e.getMessage());
        }

        if (snapshotId == null) {
            return EvaluationCorpusIndexPrepareResult.failed(
                    LabCorpusReasonCodes.NO_READY_DOCUMENTS, "No ready documents to index.");
        }

        Optional<KnowledgeIndexSnapshotEntity> built = knowledgeSnapshotService.findCorpusSnapshots(corpusId).stream()
                .filter(s -> snapshotId.equals(s.getId()))
                .findFirst();
        String profileHash =
                built.map(KnowledgeIndexSnapshotEntity::getIndexProfileHash).orElse(effectiveProfile.profileHash());
        return EvaluationCorpusIndexPrepareResult.built(snapshotId, resolved.id(), resolved.configHash(), profileHash);
    }

    private Optional<LabIndexSnapshotCompatibilityService.ReuseEligibility> diagnoseBlockedReuse(
            UUID userId,
            UUID corpusId,
            UUID indexProjectId,
            ExperimentalPresetCanonicalCatalog.IndexRequirements requirements,
            String embeddingModelIdOverride,
            ProjectIndexProfile effectiveProfile,
            LabPresetRunGroupKey groupKey) {
        List<KnowledgeIndexSnapshotEntity> candidates = knowledgeSnapshotService.findCorpusSnapshots(corpusId);
        LabIndexSnapshotCompatibilityService.ReuseEligibility last = null;
        for (KnowledgeIndexSnapshotEntity snapshot : candidates) {
            LabIndexSnapshotCompatibilityService.ReuseEligibility eligibility =
                    indexSnapshotCompatibilityService.evaluateReuse(
                            userId,
                            corpusId,
                            indexProjectId,
                            snapshot,
                            requirements,
                            embeddingModelIdOverride,
                            effectiveProfile,
                            groupKey,
                            false);
            if (!eligibility.eligible()) {
                last = eligibility;
            }
        }
        return Optional.ofNullable(last);
    }

    private EvaluationCorpusApplicationService.EvaluationCorpusContext requireReadyCorpus(UUID userId, UUID corpusId) {
        evaluationCorpusApplicationService.syncIndexProjectDocuments(userId, corpusId);
        return evaluationCorpusApplicationService.requireReadyContext(userId, corpusId);
    }

    private static ResponseStatusException toResponseException(EvaluationCorpusIndexPrepareResult result) {
        String code = result.reasonCode() != null ? result.reasonCode() : LabCorpusReasonCodes.REINDEX_REQUIRED;
        HttpStatus status =
                LabCorpusReasonCodes.RUNTIME_CONFIG_SNAPSHOT_UNAVAILABLE.equals(code)
                        ? HttpStatus.UNPROCESSABLE_ENTITY
                        : HttpStatus.BAD_REQUEST;
        return new ResponseStatusException(status, code);
    }

    private static boolean isResolvedConfigLinkageFailure(Throwable e) {
        String msg = e.getMessage();
        return msg != null && msg.contains("resolved_config_snapshot");
    }
}
