package com.uniovi.rag.application.service.evaluation.preset;

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
import com.uniovi.rag.infrastructure.persistence.KnowledgeIndexSnapshotRepository;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

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
    public static final String MODEL_UNAVAILABLE = "MODEL_UNAVAILABLE";

    private final KnowledgeSnapshotService knowledgeSnapshotService;
    private final KnowledgePipelineOrchestrator knowledgePipelineOrchestrator;
    private final ProjectIndexProfileService projectIndexProfileService;
    private final LabIndexProfileOverrideFactory labIndexProfileOverrideFactory;
    private final KnowledgeIndexSnapshotRepository knowledgeIndexSnapshotRepository;

    public LabEvaluationSnapshotService(
            KnowledgeSnapshotService knowledgeSnapshotService,
            KnowledgePipelineOrchestrator knowledgePipelineOrchestrator,
            ProjectIndexProfileService projectIndexProfileService,
            LabIndexProfileOverrideFactory labIndexProfileOverrideFactory,
            KnowledgeIndexSnapshotRepository knowledgeIndexSnapshotRepository) {
        this.knowledgeSnapshotService = knowledgeSnapshotService;
        this.knowledgePipelineOrchestrator = knowledgePipelineOrchestrator;
        this.projectIndexProfileService = projectIndexProfileService;
        this.labIndexProfileOverrideFactory = labIndexProfileOverrideFactory;
        this.knowledgeIndexSnapshotRepository = knowledgeIndexSnapshotRepository;
    }

    public UUID resolveCorpusId(EvaluationRunEntity run) {
        if (run == null || run.getEvaluationCorpus() == null) {
            return null;
        }
        return run.getEvaluationCorpus().getId();
    }

    public UUID resolveIndexProjectId(EvaluationRunEntity run) {
        if (run == null) {
            return null;
        }
        if (run.getEvaluationCorpus() != null && run.getEvaluationCorpus().getIndexProject() != null) {
            return run.getEvaluationCorpus().getIndexProject().getId();
        }
        if (run.getProject() != null) {
            return run.getProject().getId();
        }
        return null;
    }

    public ResolvedSnapshot resolveCompatibleSnapshot(
            EvaluationRunEntity run, ExperimentalPresetCanonicalCatalog.IndexRequirements requirements) {
        return resolveCompatibleSnapshot(run, requirements, null);
    }

    public ResolvedSnapshot resolveCompatibleSnapshot(
            EvaluationRunEntity run,
            ExperimentalPresetCanonicalCatalog.IndexRequirements requirements,
            String embeddingModelIdOverride) {
        KnowledgeIndexSnapshotEntity explicit = run != null ? run.getIndexSnapshot() : null;
        if (isCompatibleSnapshot(explicit, requirements, embeddingModelIdOverride)) {
            return resolved(explicit, false);
        }

        UUID corpusId = resolveCorpusId(run);
        if (corpusId != null) {
            Optional<KnowledgeIndexSnapshotEntity> compatible =
                    knowledgeSnapshotService.findCompatibleCorpusSnapshot(
                            corpusId, s -> isCompatibleSnapshot(s, requirements, embeddingModelIdOverride));
            if (compatible.isPresent()) {
                return resolved(compatible.get(), false);
            }
            Optional<KnowledgeIndexSnapshotEntity> active =
                    knowledgeSnapshotService.findActiveCorpusSnapshot(corpusId);
            if (active.isPresent() && isCompatibleSnapshot(active.get(), requirements, embeddingModelIdOverride)) {
                return resolved(active.get(), false);
            }
        }

        UUID projectId = resolveIndexProjectId(run);
        if (projectId != null && corpusId == null) {
            Optional<KnowledgeIndexSnapshotEntity> activeOpt =
                    knowledgeSnapshotService.findActiveProjectSnapshot(projectId);
            if (activeOpt.isPresent()
                    && isCompatibleSnapshot(activeOpt.get(), requirements, embeddingModelIdOverride)) {
                return resolved(activeOpt.get(), false);
            }
            Optional<KnowledgeIndexSnapshotEntity> compatible =
                    knowledgeSnapshotService.findCompatibleProjectSnapshot(
                            projectId, s -> isCompatibleSnapshot(s, requirements, embeddingModelIdOverride));
            if (compatible.isPresent()) {
                return resolved(compatible.get(), false);
            }
            if (activeOpt.isPresent()) {
                return incompatible(activeOpt.get());
            }
        }

        if (explicit != null && explicit.getId() != null) {
            return resolved(explicit, false);
        }
        return ResolvedSnapshot.missing();
    }

    public PrepareResult prepareSnapshotIfNeeded(
            EvaluationRunEntity run,
            LabPresetRunGroupKey groupKey,
            ExperimentalPresetCanonicalCatalog.IndexRequirements requirements,
            AutoReindexPolicy policy,
            String embeddingModelIdOverride) {
        Objects.requireNonNull(policy, "policy");
        if (!policy.enabled()) {
            ResolvedSnapshot resolved = resolveCompatibleSnapshot(run, requirements, embeddingModelIdOverride);
            if (resolved.hasUsableSnapshot()) {
                return PrepareResult.reused(resolved);
            }
            return PrepareResult.incompatible(REINDEX_REQUIRED, "Auto-reindex is disabled for this run.");
        }

        ResolvedSnapshot existing = resolveCompatibleSnapshot(run, requirements, embeddingModelIdOverride);
        if (existing.hasUsableSnapshot()) {
            return PrepareResult.reused(existing);
        }

        if (!policy.allowActiveSnapshotMutation()) {
            return PrepareResult.incompatible(
                    NO_COMPATIBLE_SNAPSHOT, "No compatible snapshot exists and active snapshot mutation is disabled.");
        }

        UUID projectId = resolveIndexProjectId(run);
        UUID corpusId = resolveCorpusId(run);
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
            return PrepareResult.built(
                    new ResolvedSnapshot(
                            true,
                            newSnapId,
                            effective.profileHash(),
                            caps,
                            true,
                            ownerType,
                            ownerId));
        }

        IndexSnapshotCapabilities caps = IndexSnapshotCapabilities.fromIndexProfile(profileJson);
        IndexCompatibilityResult afterIdx = IndexCompatibilityResult.check(requirements, true, caps);
        if (!afterIdx.compatible()) {
            throw new IllegalStateException(
                    afterIdx.reasonCode() != null ? afterIdx.reasonCode() : NO_COMPATIBLE_SNAPSHOT);
        }
        return PrepareResult.built(resolved(built, true));
    }

    public boolean isCompatibleSnapshot(
            KnowledgeIndexSnapshotEntity snapshot,
            ExperimentalPresetCanonicalCatalog.IndexRequirements requirements,
            String embeddingModelIdOverride) {
        if (snapshot == null || snapshot.getId() == null) {
            return false;
        }
        Map<String, Object> profile = snapshot.getIndexProfileJsonb() != null ? snapshot.getIndexProfileJsonb() : Map.of();
        IndexSnapshotCapabilities caps = IndexSnapshotCapabilities.fromIndexProfile(profile);
        if (embeddingModelIdOverride != null
                && !embeddingModelIdOverride.isBlank()
                && caps.embeddingModelId() != null
                && !embeddingModelIdOverride.trim().equalsIgnoreCase(caps.embeddingModelId().trim())) {
            return false;
        }
        return IndexCompatibilityResult.check(requirements, true, caps).compatible();
    }

    private static ResolvedSnapshot resolved(KnowledgeIndexSnapshotEntity snap, boolean preparedDuringRun) {
        if (snap == null || snap.getId() == null) {
            return ResolvedSnapshot.missing();
        }
        Map<String, Object> profile = snap.getIndexProfileJsonb() != null ? snap.getIndexProfileJsonb() : Map.of();
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
    private static ResolvedSnapshot incompatible(KnowledgeIndexSnapshotEntity snap) {
        if (snap == null || snap.getId() == null) {
            return ResolvedSnapshot.missing();
        }
        Map<String, Object> profile = snap.getIndexProfileJsonb() != null ? snap.getIndexProfileJsonb() : Map.of();
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
            String errorCode,
            String errorReason) {
        static PrepareResult reused(ResolvedSnapshot snapshot) {
            return new PrepareResult("REUSE_COMPATIBLE_SNAPSHOT", "REUSED", snapshot, null, null);
        }

        static PrepareResult built(ResolvedSnapshot snapshot) {
            return new PrepareResult("BUILD_AND_ACTIVATE", "BUILT", snapshot, null, null);
        }

        static PrepareResult incompatible(String code, String reason) {
            return new PrepareResult("NONE", "INCOMPATIBLE", ResolvedSnapshot.missing(), code, reason);
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

    /** Ensures run project is wired from evaluation corpus index sandbox when only corpus is set. */
    public void ensureRunIndexProject(EvaluationRunEntity run) {
        if (run == null) {
            return;
        }
        if (run.getProject() != null && run.getProject().getId() != null) {
            return;
        }
        EvaluationCorpusEntity corpus = run.getEvaluationCorpus();
        if (corpus != null && corpus.getIndexProject() != null) {
            run.setProject(corpus.getIndexProject());
        }
    }
}
