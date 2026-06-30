package com.uniovi.rag.application.service.evaluation.preset;

import com.uniovi.rag.application.service.evaluation.corpus.EvaluationCorpusIndexPrepareResult;
import com.uniovi.rag.application.service.evaluation.corpus.EvaluationCorpusIndexService;
import com.uniovi.rag.application.service.evaluation.corpus.LabCorpusReasonCodes;
import com.uniovi.rag.application.service.evaluation.LabJobProgressTracker;
import com.uniovi.rag.application.service.knowledge.KnowledgeIndexSnapshotProfileAccess;
import com.uniovi.rag.application.service.knowledge.KnowledgePipelineOrchestrator;
import com.uniovi.rag.application.service.knowledge.KnowledgeSnapshotService;
import com.uniovi.rag.application.service.knowledge.LabIndexProfileOverrideFactory;
import com.uniovi.rag.application.service.knowledge.ProjectIndexProfileService;
import com.uniovi.rag.application.service.runtime.config.IndexCompatibilityResult;
import com.uniovi.rag.application.service.runtime.config.IndexSnapshotCapabilities;
import com.uniovi.rag.domain.knowledge.CorpusScope;
import com.uniovi.rag.domain.knowledge.KnowledgeSnapshotOwnerType;
import com.uniovi.rag.domain.knowledge.ProjectIndexProfile;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationCorpusEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationRunEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeIndexSnapshotEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.ProjectEntity;
import com.uniovi.rag.infrastructure.persistence.EvaluationRunRepository;
import com.uniovi.rag.infrastructure.persistence.KnowledgeIndexSnapshotRepository;
import com.uniovi.rag.infrastructure.persistence.ProjectRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves and prepares evaluation-corpus-scoped index snapshots for Lab benchmarks.
 *
 * <p>Lab runs bind snapshots by {@code evaluation_corpus.id} + index profile (materialization, metadata, embedding),
 * not by the user's active chat project snapshot.
 */
@Service
public class LabEvaluationSnapshotService {

    public static final String NO_COMPATIBLE_SNAPSHOT = "NO_COMPATIBLE_SNAPSHOT";
    public static final String REINDEX_REQUIRED = "REINDEX_REQUIRED";
    public static final String REINDEX_IN_PROGRESS = "REINDEX_IN_PROGRESS";
    public static final String REINDEX_FAILED = "REINDEX_FAILED";
    public static final String REBUILD_REQUIRED = "REBUILD_REQUIRED";
    public static final String MODEL_UNAVAILABLE = "MODEL_UNAVAILABLE";

    private final KnowledgeSnapshotService knowledgeSnapshotService;
    private final KnowledgePipelineOrchestrator knowledgePipelineOrchestrator;
    private final ProjectIndexProfileService projectIndexProfileService;
    private final LabIndexProfileOverrideFactory labIndexProfileOverrideFactory;
    private final EvaluationCorpusIndexService evaluationCorpusIndexService;
    private final CorpusAvailabilityGate corpusAvailabilityGate;
    private final LabIndexSnapshotCompatibilityService indexSnapshotCompatibilityService;
    private final KnowledgeIndexSnapshotRepository knowledgeIndexSnapshotRepository;
    private final KnowledgeIndexSnapshotProfileAccess snapshotProfileAccess;
    private final EvaluationRunRepository evaluationRunRepository;
    private final ProjectRepository projectRepository;
    private final ObjectProvider<LabJobProgressTracker> labJobProgressTracker;

    public LabEvaluationSnapshotService(
            KnowledgeSnapshotService knowledgeSnapshotService,
            KnowledgePipelineOrchestrator knowledgePipelineOrchestrator,
            ProjectIndexProfileService projectIndexProfileService,
            LabIndexProfileOverrideFactory labIndexProfileOverrideFactory,
            EvaluationCorpusIndexService evaluationCorpusIndexService,
            CorpusAvailabilityGate corpusAvailabilityGate,
            LabIndexSnapshotCompatibilityService indexSnapshotCompatibilityService,
            KnowledgeIndexSnapshotRepository knowledgeIndexSnapshotRepository,
            KnowledgeIndexSnapshotProfileAccess snapshotProfileAccess,
            EvaluationRunRepository evaluationRunRepository,
            ProjectRepository projectRepository,
            ObjectProvider<LabJobProgressTracker> labJobProgressTracker) {
        this.knowledgeSnapshotService = knowledgeSnapshotService;
        this.knowledgePipelineOrchestrator = knowledgePipelineOrchestrator;
        this.projectIndexProfileService = projectIndexProfileService;
        this.labIndexProfileOverrideFactory = labIndexProfileOverrideFactory;
        this.evaluationCorpusIndexService = evaluationCorpusIndexService;
        this.corpusAvailabilityGate = corpusAvailabilityGate;
        this.indexSnapshotCompatibilityService = indexSnapshotCompatibilityService;
        this.knowledgeIndexSnapshotRepository = knowledgeIndexSnapshotRepository;
        this.snapshotProfileAccess = snapshotProfileAccess;
        this.evaluationRunRepository = evaluationRunRepository;
        this.projectRepository = projectRepository;
        this.labJobProgressTracker = labJobProgressTracker;
    }

    public UUID resolveCorpusId(EvaluationRunEntity run) {
        if (run == null) {
            return null;
        }
        if (run.getId() != null) {
            return evaluationRunRepository.findCorpusIdByRunId(run.getId()).orElse(null);
        }
        if (run.getEvaluationCorpus() == null) {
            return null;
        }
        return run.getEvaluationCorpus().getId();
    }

    public UUID resolveIndexProjectId(EvaluationRunEntity run) {
        if (run == null) {
            return null;
        }
        if (run.getId() != null) {
            return evaluationRunRepository.findEffectiveProjectIdByRunId(run.getId()).orElse(null);
        }
        if (run.getProject() != null) {
            return run.getProject().getId();
        }
        if (run.getEvaluationCorpus() != null && run.getEvaluationCorpus().getIndexProject() != null) {
            return run.getEvaluationCorpus().getIndexProject().getId();
        }
        return null;
    }

    public ResolvedSnapshot resolveCompatibleSnapshot(
            EvaluationRunEntity run, ExperimentalPresetCanonicalCatalog.IndexRequirements requirements) {
        return resolveCompatibleSnapshot(run, requirements, null, null);
    }

    public ResolvedSnapshot resolveCompatibleSnapshot(
            EvaluationRunEntity run,
            ExperimentalPresetCanonicalCatalog.IndexRequirements requirements,
            String embeddingModelIdOverride) {
        return resolveCompatibleSnapshot(run, requirements, embeddingModelIdOverride, null);
    }

    public ResolvedSnapshot resolveCompatibleSnapshot(
            EvaluationRunEntity run,
            ExperimentalPresetCanonicalCatalog.IndexRequirements requirements,
            String embeddingModelIdOverride,
            LabPresetRunGroupKey groupKey) {
        KnowledgeIndexSnapshotEntity explicit = run != null ? run.getIndexSnapshot() : null;
        if (isUsableSnapshot(run, explicit, requirements, embeddingModelIdOverride, groupKey)) {
            return resolved(explicit, false);
        }

        UUID corpusId = resolveCorpusId(run);
        if (corpusId != null) {
            Optional<KnowledgeIndexSnapshotEntity> compatible =
                    knowledgeSnapshotService.findCompatibleCorpusSnapshot(
                            corpusId,
                            s -> isUsableSnapshot(run, s, requirements, embeddingModelIdOverride, groupKey));
            if (compatible.isPresent()) {
                return resolved(compatible.get(), false);
            }
            Optional<KnowledgeIndexSnapshotEntity> active =
                    knowledgeSnapshotService.findActiveCorpusSnapshot(corpusId);
            if (active.isPresent()
                    && isUsableSnapshot(run, active.get(), requirements, embeddingModelIdOverride, groupKey)) {
                return resolved(active.get(), false);
            }
        }

        UUID projectId = resolveIndexProjectId(run);
        if (projectId != null && corpusId == null) {
            Optional<KnowledgeIndexSnapshotEntity> activeOpt =
                    knowledgeSnapshotService.findActiveProjectSnapshot(projectId);
            if (activeOpt.isPresent()
                    && isUsableSnapshot(run, activeOpt.get(), requirements, embeddingModelIdOverride, groupKey)) {
                return resolved(activeOpt.get(), false);
            }
            Optional<KnowledgeIndexSnapshotEntity> compatible =
                    knowledgeSnapshotService.findCompatibleProjectSnapshot(
                            projectId,
                            s -> isUsableSnapshot(run, s, requirements, embeddingModelIdOverride, groupKey));
            if (compatible.isPresent()) {
                return resolved(compatible.get(), false);
            }
            if (activeOpt.isPresent()) {
                return incompatible(activeOpt.get());
            }
        }

        if (explicit != null && explicit.getId() != null) {
            if (isUsableSnapshot(run, explicit, requirements, embeddingModelIdOverride, groupKey)) {
                return resolved(explicit, false);
            }
            return incompatible(explicit);
        }
        return ResolvedSnapshot.missing();
    }

    /**
     * Evaluates a snapshot already selected for the current run group (after prepare/reuse). Vector rows are required.
     */
    public LabIndexSnapshotCompatibilityService.ReuseEligibility evaluatePreparedGroupSnapshot(
            EvaluationRunEntity run,
            UUID snapshotId,
            LabPresetRunGroupKey groupKey,
            ExperimentalPresetCanonicalCatalog.IndexRequirements requirements,
            String embeddingModelIdOverride) {
        if (snapshotId == null || groupKey == null) {
            return LabIndexSnapshotCompatibilityService.ReuseEligibility.blocked(
                    NO_COMPATIBLE_SNAPSHOT, "No compatible index snapshot was found.");
        }
        KnowledgeIndexSnapshotEntity snap = knowledgeIndexSnapshotRepository.findById(snapshotId).orElse(null);
        if (snap == null || snap.getId() == null) {
            return LabIndexSnapshotCompatibilityService.ReuseEligibility.blocked(
                    NO_COMPATIBLE_SNAPSHOT, "No compatible index snapshot was found.");
        }
        return indexSnapshotCompatibilityService.evaluateReuse(
                resolveUserId(run),
                resolveCorpusId(run),
                resolveIndexProjectId(run),
                snap,
                requirements,
                embeddingModelIdOverride,
                resolveEffectiveProfile(run, requirements, groupKey, embeddingModelIdOverride),
                groupKey,
                true);
    }

    public IndexSnapshotCapabilities capabilitiesForSnapshot(UUID snapshotId) {
        if (snapshotId == null) {
            return IndexSnapshotCapabilities.fromIndexProfile(Map.of());
        }
        return IndexSnapshotCapabilities.fromIndexProfile(snapshotProfileAccess.loadProfileJsonb(snapshotId));
    }

    public String indexProfileHashForSnapshot(UUID snapshotId) {
        if (snapshotId == null) {
            return null;
        }
        return knowledgeIndexSnapshotRepository
                .findById(snapshotId)
                .map(KnowledgeIndexSnapshotEntity::getIndexProfileHash)
                .orElse(null);
    }

    /** Whether {@code snapshotId} satisfies the shared reuse contract for the run group. */
    public boolean hasRequiredVectorRows(
            EvaluationRunEntity run,
            UUID snapshotId,
            ExperimentalPresetCanonicalCatalog.IndexRequirements requirements,
            LabPresetRunGroupKey groupKey) {
        return snapshotEligibleForReuse(run, snapshotId, requirements, groupKey);
    }

    public PrepareResult prepareSnapshotIfNeeded(
            EvaluationRunEntity run,
            LabPresetRunGroupKey groupKey,
            ExperimentalPresetCanonicalCatalog.IndexRequirements requirements,
            AutoReindexPolicy policy,
            String embeddingModelIdOverride) {
        Objects.requireNonNull(policy, "policy");
        if (!policy.enabled()) {
            ResolvedSnapshot resolved = resolveCompatibleSnapshot(run, requirements, embeddingModelIdOverride, groupKey);
            if (resolved.hasUsableSnapshot()
                    && snapshotEligibleForReuse(run, resolved.snapshotId(), requirements, groupKey)) {
                return PrepareResult.reused(resolved);
            }
            return PrepareResult.rebuildRequired(
                    REINDEX_REQUIRED, "Auto-reindex is disabled for this run.");
        }

        ResolvedSnapshot existing = resolveCompatibleSnapshot(run, requirements, embeddingModelIdOverride, groupKey);
        if (existing.hasUsableSnapshot()
                && snapshotEligibleForReuse(run, existing.snapshotId(), requirements, groupKey)) {
            return PrepareResult.reused(existing);
        }

        if (!policy.allowActiveSnapshotMutation()) {
            return PrepareResult.rebuildRequired(
                    NO_COMPATIBLE_SNAPSHOT,
                    "No compatible snapshot exists and active snapshot mutation is disabled.");
        }

        UUID projectId = resolveIndexProjectId(run);
        UUID corpusId = resolveCorpusId(run);
        UUID userId = resolveUserId(run);

        if (corpusId != null && userId == null) {
            throw new IllegalStateException(
                    "Evaluation run owner is missing for corpus-scoped index preparation.");
        }

        if (corpusId != null && userId != null) {
            emitSnapshotPreparation(run, groupKey, corpusId, true);
            boolean allowBuild = policy.enabled() && policy.allowActiveSnapshotMutation();
            EvaluationCorpusIndexPrepareResult prepared =
                    evaluationCorpusIndexService.prepareForPresetRequirements(
                            userId, corpusId, groupKey, requirements, embeddingModelIdOverride, allowBuild);
            if (prepared.succeeded()) {
                KnowledgeIndexSnapshotEntity built =
                        knowledgeIndexSnapshotRepository
                                .findById(prepared.knowledgeIndexSnapshotId())
                                .orElse(null);
                ResolvedSnapshot resolved = resolvedFromPrepare(prepared, built, corpusId);
                if (resolved.hasUsableSnapshot()
                        && resolved.snapshotId() != null
                        && !snapshotEligibleForReuse(run, resolved.snapshotId(), requirements, groupKey)) {
                    return PrepareResult.incompatible(
                            LabCorpusReasonCodes.SNAPSHOT_EMPTY,
                            "The snapshot has no vector rows for the evaluation corpus.");
                }
                emitSnapshotPreparationCompleted(run, groupKey, corpusId, prepared.knowledgeIndexSnapshotId());
                return toPrepareResult(prepared, resolved);
            }
            if (!policy.enabled()) {
                return PrepareResult.incompatible(
                        prepared.reasonCode() != null ? prepared.reasonCode() : REINDEX_REQUIRED,
                        prepared.reasonMessage() != null
                                ? prepared.reasonMessage()
                                : "Auto-reindex is disabled for this run.");
            }
            return PrepareResult.incompatible(
                    prepared.reasonCode() != null ? prepared.reasonCode() : REINDEX_FAILED,
                    prepared.reasonMessage() != null ? prepared.reasonMessage() : "Index preparation failed.");
        }

        if (projectId == null) {
            throw new IllegalStateException("AUTO_REINDEX_REQUIRES_CORPUS_INDEX_CONTEXT");
        }

        ProjectIndexProfile current = projectIndexProfileService.ensureDefault(projectId);
        if (embeddingModelIdOverride != null && !embeddingModelIdOverride.isBlank()) {
            current = labIndexProfileOverrideFactory.withEmbeddingModelId(current, embeddingModelIdOverride);
        }
        ProjectIndexProfile effective = labIndexProfileOverrideFactory.buildEffectiveProfile(current, requirements, groupKey);

        UUID resolvedConfigSnapshotId =
                run.getResolvedConfigSnapshot() != null ? run.getResolvedConfigSnapshot().getId() : null;
        String resolvedConfigHash =
                run.getResolvedConfigSnapshot() != null ? run.getResolvedConfigSnapshot().getConfigHash() : null;

        KnowledgeSnapshotOwnerType ownerType =
                corpusId != null ? KnowledgeSnapshotOwnerType.EVALUATION_CORPUS : KnowledgeSnapshotOwnerType.PROJECT;
        UUID ownerId = corpusId != null ? corpusId : projectId;

        emitSnapshotPreparation(run, groupKey, corpusId, true);

        UUID newSnapId =
                knowledgePipelineOrchestrator.rebuildScopeWithProfileOverride(
                        projectId,
                        CorpusScope.PROJECT_SHARED,
                        null,
                        ownerType,
                        ownerId,
                        resolvedConfigSnapshotId,
                        resolvedConfigHash,
                        effective);

        if (newSnapId == null) {
            throw new IllegalStateException(REINDEX_FAILED + ": no READY documents in evaluation corpus index project");
        }

        KnowledgeIndexSnapshotEntity built =
                knowledgeIndexSnapshotRepository.findById(newSnapId).orElse(null);
        Map<String, Object> profileJson =
                built != null && built.getIndexProfileJsonb() != null
                        ? built.getIndexProfileJsonb()
                        : effective.toSnapshotJsonb();
        if (built == null || built.getId() == null) {
            IndexSnapshotCapabilities caps = IndexSnapshotCapabilities.fromIndexProfile(profileJson);
            emitSnapshotPreparationCompleted(run, groupKey, corpusId, newSnapId);
            return PrepareResult.built(
                    new ResolvedSnapshot(
                            true,
                            newSnapId,
                            effective.profileHash(),
                            caps,
                            true,
                            ownerType,
                            ownerId),
                    resolvedConfigSnapshotId,
                    effective.profileHash());
        }

        IndexSnapshotCapabilities caps = IndexSnapshotCapabilities.fromIndexProfile(profileJson);
        IndexCompatibilityResult afterIdx = IndexCompatibilityResult.check(requirements, true, caps);
        if (!afterIdx.compatible()) {
            throw new IllegalStateException(
                    afterIdx.reasonCode() != null ? afterIdx.reasonCode() : NO_COMPATIBLE_SNAPSHOT);
        }
        UUID preparedSnapshotId = built != null && built.getId() != null ? built.getId() : newSnapId;
        if (!snapshotEligibleForReuse(run, preparedSnapshotId, requirements, groupKey)) {
            return PrepareResult.incompatible(
                    LabCorpusReasonCodes.SNAPSHOT_EMPTY,
                    "The snapshot has no vector rows for the evaluation corpus.");
        }
        emitSnapshotPreparationCompleted(run, groupKey, corpusId, preparedSnapshotId);
        ResolvedSnapshot resolvedSnap = resolved(built, true);
        UUID cfgId = built != null ? built.getResolvedConfigSnapshotId() : resolvedConfigSnapshotId;
        return PrepareResult.built(resolvedSnap, cfgId, resolvedSnap.indexProfileHash());
    }

    private static PrepareResult toPrepareResult(
            EvaluationCorpusIndexPrepareResult prepared, ResolvedSnapshot resolved) {
        if (prepared.status() == EvaluationCorpusIndexPrepareResult.IndexBuildStatus.REUSED) {
            return PrepareResult.reused(resolved, prepared.resolvedConfigSnapshotId(), prepared.indexProfileHash());
        }
        return PrepareResult.built(resolved, prepared.resolvedConfigSnapshotId(), prepared.indexProfileHash());
    }

    private void emitSnapshotPreparation(
            EvaluationRunEntity run, LabPresetRunGroupKey groupKey, UUID corpusId, boolean started) {
        if (run == null || run.getAsyncTask() == null || run.getAsyncTask().getId() == null) {
            return;
        }
        labJobProgressTracker.ifAvailable(
                tracker -> {
                    UUID taskId = run.getAsyncTask().getId();
                    UUID runId = run.getId();
                    if (started) {
                        tracker.emitSnapshotPreparationStarted(taskId, runId, groupKey, corpusId);
                    }
                });
    }

    private void emitSnapshotPreparationCompleted(
            EvaluationRunEntity run,
            LabPresetRunGroupKey groupKey,
            UUID corpusId,
            UUID snapshotId) {
        if (run == null || run.getAsyncTask() == null || run.getAsyncTask().getId() == null) {
            return;
        }
        labJobProgressTracker.ifAvailable(
                tracker ->
                        tracker.emitSnapshotPreparationCompleted(
                                run.getAsyncTask().getId(), run.getId(), groupKey, snapshotId, corpusId));
    }

    private boolean isUsableSnapshot(
            EvaluationRunEntity run,
            KnowledgeIndexSnapshotEntity snapshot,
            ExperimentalPresetCanonicalCatalog.IndexRequirements requirements,
            String embeddingModelIdOverride,
            LabPresetRunGroupKey groupKey) {
        if (snapshot == null || snapshot.getId() == null) {
            return false;
        }
        return indexSnapshotCompatibilityService
                .evaluateReuse(
                        resolveUserId(run),
                        resolveCorpusId(run),
                        resolveIndexProjectId(run),
                        snapshot,
                        requirements,
                        embeddingModelIdOverride,
                        resolveEffectiveProfile(run, requirements, groupKey, embeddingModelIdOverride),
                        groupKey,
                        false)
                .eligible();
    }

    private boolean snapshotEligibleForReuse(
            EvaluationRunEntity run,
            UUID snapshotId,
            ExperimentalPresetCanonicalCatalog.IndexRequirements requirements,
            LabPresetRunGroupKey groupKey) {
        if (snapshotId == null) {
            return false;
        }
        if (!indexSnapshotCompatibilityService.requiresSnapshotBoundVectorRows(requirements, groupKey)) {
            return true;
        }
        return indexSnapshotCompatibilityService.hasVectorRows(
                resolveUserId(run), resolveCorpusId(run), resolveIndexProjectId(run), snapshotId);
    }

    private ProjectIndexProfile resolveEffectiveProfile(
            EvaluationRunEntity run,
            ExperimentalPresetCanonicalCatalog.IndexRequirements requirements,
            LabPresetRunGroupKey groupKey,
            String embeddingModelIdOverride) {
        if (run == null || groupKey == null) {
            return null;
        }
        UUID projectId = resolveIndexProjectId(run);
        if (projectId == null) {
            return null;
        }
        ProjectIndexProfile base = projectIndexProfileService.ensureDefault(projectId);
        if (embeddingModelIdOverride != null && !embeddingModelIdOverride.isBlank()) {
            base = labIndexProfileOverrideFactory.withEmbeddingModelId(base, embeddingModelIdOverride);
        }
        return labIndexProfileOverrideFactory.buildEffectiveProfile(base, requirements, groupKey);
    }

    public boolean isCompatibleSnapshot(
            KnowledgeIndexSnapshotEntity snapshot,
            ExperimentalPresetCanonicalCatalog.IndexRequirements requirements,
            String embeddingModelIdOverride) {
        return indexSnapshotCompatibilityService.profileCompatible(snapshot, requirements, embeddingModelIdOverride);
    }

    private ResolvedSnapshot resolvedFromPrepare(
            EvaluationCorpusIndexPrepareResult prepared,
            KnowledgeIndexSnapshotEntity built,
            UUID corpusId) {
        if (built != null && built.getId() != null) {
            return resolved(
                    built, prepared.status() == EvaluationCorpusIndexPrepareResult.IndexBuildStatus.BUILT);
        }
        if (!prepared.succeeded() || prepared.knowledgeIndexSnapshotId() == null) {
            return ResolvedSnapshot.missing();
        }
        return new ResolvedSnapshot(
                true,
                prepared.knowledgeIndexSnapshotId(),
                prepared.indexProfileHash(),
                IndexSnapshotCapabilities.fromIndexProfile(Map.of()),
                prepared.status() == EvaluationCorpusIndexPrepareResult.IndexBuildStatus.BUILT,
                KnowledgeSnapshotOwnerType.EVALUATION_CORPUS,
                corpusId);
    }

    private ResolvedSnapshot resolved(KnowledgeIndexSnapshotEntity snap, boolean preparedDuringRun) {
        if (snap == null || snap.getId() == null) {
            return ResolvedSnapshot.missing();
        }
        Map<String, Object> profile = snapshotProfileAccess.resolveProfileJsonb(snap);
        IndexSnapshotCapabilities caps = IndexSnapshotCapabilities.fromIndexProfile(profile);
        return new ResolvedSnapshot(
                true,
                snap.getId(),
                snap.getIndexProfileHash(),
                caps,
                preparedDuringRun,
                snap.getOwnerType(),
                snap.getOwnerId());
    }

    /** Active snapshot exists but does not satisfy preset index requirements (distinct from no snapshot). */
    private ResolvedSnapshot incompatible(KnowledgeIndexSnapshotEntity snap) {
        if (snap == null || snap.getId() == null) {
            return ResolvedSnapshot.missing();
        }
        Map<String, Object> profile = snapshotProfileAccess.resolveProfileJsonb(snap);
        IndexSnapshotCapabilities caps = IndexSnapshotCapabilities.fromIndexProfile(profile);
        return new ResolvedSnapshot(
                false,
                snap.getId(),
                snap.getIndexProfileHash(),
                caps,
                false,
                snap.getOwnerType(),
                snap.getOwnerId());
    }

    public record ResolvedSnapshot(
            boolean hasUsableSnapshot,
            UUID snapshotId,
            String indexProfileHash,
            IndexSnapshotCapabilities capabilities,
            boolean preparedDuringRun,
            KnowledgeSnapshotOwnerType ownerType,
            UUID ownerId) {
        static ResolvedSnapshot missing() {
            return new ResolvedSnapshot(false, null, null, IndexSnapshotCapabilities.fromIndexProfile(Map.of()), false, null, null);
        }
    }

    public record PrepareResult(
            String action,
            String status,
            ResolvedSnapshot snapshot,
            UUID resolvedConfigSnapshotId,
            String indexProfileHash,
            String errorCode,
            String errorReason) {
        static PrepareResult reused(ResolvedSnapshot snapshot) {
            return reused(snapshot, null, snapshot != null ? snapshot.indexProfileHash() : null);
        }

        static PrepareResult built(ResolvedSnapshot snapshot) {
            return built(snapshot, null, snapshot != null ? snapshot.indexProfileHash() : null);
        }

        static PrepareResult reused(ResolvedSnapshot snapshot, UUID resolvedConfigSnapshotId, String indexProfileHash) {
            return new PrepareResult(
                    "REUSE_COMPATIBLE_SNAPSHOT",
                    "REUSED",
                    snapshot,
                    resolvedConfigSnapshotId,
                    indexProfileHash,
                    null,
                    null);
        }

        static PrepareResult built(ResolvedSnapshot snapshot, UUID resolvedConfigSnapshotId, String indexProfileHash) {
            return new PrepareResult(
                    "BUILD_AND_ACTIVATE",
                    "BUILT",
                    snapshot,
                    resolvedConfigSnapshotId,
                    indexProfileHash,
                    null,
                    null);
        }

        static PrepareResult incompatible(String code, String reason) {
            return new PrepareResult(
                    "NONE", "INCOMPATIBLE", ResolvedSnapshot.missing(), null, null, code, reason);
        }

        static PrepareResult rebuildRequired(String code, String reason) {
            return new PrepareResult(
                    REBUILD_REQUIRED, "PENDING", ResolvedSnapshot.missing(), null, null, code, reason);
        }
    }

    public record AutoReindexPolicy(
            boolean enabled,
            boolean allowActiveSnapshotMutation,
            boolean reuseCompatibleActiveSnapshot,
            boolean failOnReindexFailure) {
        @SuppressWarnings("unchecked")
        public static AutoReindexPolicy fromRun(EvaluationRunEntity run) {
            if (run == null || run.getAggregatesJson() == null) {
                return new AutoReindexPolicy(false, false, true, true);
            }
            Object o = run.getAggregatesJson().get("autoReindexPolicy");
            if (!(o instanceof Map<?, ?> m)) {
                return new AutoReindexPolicy(false, false, true, true);
            }
            boolean enabled = Boolean.TRUE.equals(m.get("enabled"));
            boolean allowMut = Boolean.TRUE.equals(m.get("allowActiveSnapshotMutation"));
            boolean reuse =
                    m.get("reuseCompatibleActiveSnapshot") == null
                            || Boolean.TRUE.equals(m.get("reuseCompatibleActiveSnapshot"));
            boolean fail =
                    m.get("failOnReindexFailure") == null || Boolean.TRUE.equals(m.get("failOnReindexFailure"));
            return new AutoReindexPolicy(enabled, allowMut, reuse, fail);
        }
    }

    /** Async-safe user lookup when the run entity was loaded without {@code JOIN FETCH r.user}. */
    UUID resolveUserId(EvaluationRunEntity run) {
        if (run == null) {
            return null;
        }
        if (run.getUser() != null && run.getUser().getId() != null) {
            return run.getUser().getId();
        }
        if (run.getId() == null) {
            return null;
        }
        return evaluationRunRepository.findUserIdByRunId(run.getId()).orElse(null);
    }

    /** Ensures run project is wired from evaluation corpus index sandbox when only corpus is set. */
    @Transactional
    public void ensureRunIndexProject(EvaluationRunEntity run) {
        if (run == null || run.getId() == null) {
            return;
        }
        wireRunIndexProject(run.getId());
    }

    /**
     * Wires {@code evaluation_run.project_id} from corpus index project using scalar lookups only (async-safe).
     */
    @Transactional
    public void ensureRunIndexProjectByRunId(UUID runId) {
        wireRunIndexProject(runId);
    }

    private void wireRunIndexProject(UUID runId) {
        if (runId == null) {
            return;
        }
        if (evaluationRunRepository.findProjectIdByRunId(runId).isPresent()) {
            return;
        }
        Optional<UUID> projectId = evaluationRunRepository.findEffectiveProjectIdByRunId(runId);
        if (projectId.isEmpty()) {
            return;
        }
        EvaluationRunEntity run = evaluationRunRepository.getReferenceById(runId);
        run.setProject(projectRepository.getReferenceById(projectId.get()));
    }
}
