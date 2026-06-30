package com.uniovi.rag.application.service.evaluation.preset;

import com.uniovi.rag.application.service.evaluation.corpus.LabCorpusReasonCodes;
import com.uniovi.rag.application.service.knowledge.KnowledgeIndexSnapshotProfileAccess;
import com.uniovi.rag.application.service.knowledge.KnowledgePipelineOrchestrator;
import com.uniovi.rag.application.service.runtime.config.IndexCompatibilityResult;
import com.uniovi.rag.application.service.runtime.config.IndexSnapshotCapabilities;
import com.uniovi.rag.domain.evaluation.workbook.RagExperimentalPresetCode;
import com.uniovi.rag.domain.knowledge.CorpusScope;
import com.uniovi.rag.domain.knowledge.ProjectIndexProfile;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeIndexSnapshotEntity;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Shared Lab index snapshot reuse contract: profile/materialization compatibility, non-empty vector rows,
 * and corpus signature freshness. Used by prepare-index, auto-reindex, M4 preflight, and execution guards.
 */
@Service
public class LabIndexSnapshotCompatibilityService {

    private final CorpusAvailabilityGate corpusAvailabilityGate;
    private final KnowledgePipelineOrchestrator knowledgePipelineOrchestrator;
    private final KnowledgeIndexSnapshotProfileAccess snapshotProfileAccess;

    public LabIndexSnapshotCompatibilityService(
            CorpusAvailabilityGate corpusAvailabilityGate,
            KnowledgePipelineOrchestrator knowledgePipelineOrchestrator,
            KnowledgeIndexSnapshotProfileAccess snapshotProfileAccess) {
        this.corpusAvailabilityGate = corpusAvailabilityGate;
        this.knowledgePipelineOrchestrator = knowledgePipelineOrchestrator;
        this.snapshotProfileAccess = snapshotProfileAccess;
    }

    public record ReuseEligibility(boolean eligible, String reasonCode, String reasonMessage) {
        static ReuseEligibility ok() {
            return new ReuseEligibility(true, null, null);
        }

        static ReuseEligibility blocked(String code, String message) {
            return new ReuseEligibility(false, code, message);
        }
    }

    /** True when execution reads snapshot-bound {@code vector_store} rows (P1 assembly or P2+ retrieval). */
    public boolean requiresSnapshotBoundVectorRows(
            ExperimentalPresetCanonicalCatalog.IndexRequirements requirements,
            LabPresetRunGroupKey groupKey) {
        if (groupKey == LabPresetRunGroupKey.DIRECT_LLM) {
            return false;
        }
        if (groupKey == LabPresetRunGroupKey.NO_INDEX) {
            return true;
        }
        if (groupKey == LabPresetRunGroupKey.MULTI_TURN_UNSUPPORTED_IN_SINGLE_TURN) {
            return false;
        }
        if (requirements == null || requirements.requiredMaterialization() == null) {
            return false;
        }
        return requirements.requiredMaterialization()
                != ExperimentalPresetCanonicalCatalog.RequiredMaterialization.NONE;
    }

    public boolean requiresSnapshotBoundVectorRows(RagExperimentalPresetCode preset) {
        return ExperimentalPresetCanonicalCatalog.requiresSnapshotForExecution(preset);
    }

    /** Manual corpus prepare-index always materializes snapshot-bound rows. */
    public boolean requiresSnapshotBoundVectorRowsForCorpusPrepare() {
        return true;
    }

    public boolean profileCompatible(
            KnowledgeIndexSnapshotEntity snapshot,
            ExperimentalPresetCanonicalCatalog.IndexRequirements requirements,
            String embeddingModelIdOverride) {
        if (snapshot == null || snapshot.getId() == null) {
            return false;
        }
        Map<String, Object> profile = snapshotProfileAccess.resolveProfileJsonb(snapshot);
        IndexSnapshotCapabilities caps = IndexSnapshotCapabilities.fromIndexProfile(profile);
        if (embeddingModelIdOverride != null
                && !embeddingModelIdOverride.isBlank()
                && caps.embeddingModelId() != null
                && !embeddingModelIdOverride.trim().equalsIgnoreCase(caps.embeddingModelId().trim())) {
            return false;
        }
        return IndexCompatibilityResult.check(requirements, true, caps).compatible();
    }

    public boolean hasVectorRows(UUID userId, UUID corpusId, UUID indexProjectId, UUID snapshotId) {
        if (snapshotId == null) {
            return false;
        }
        if (userId != null && corpusId != null) {
            return corpusAvailabilityGate.snapshotHasVectorRows(userId, corpusId, snapshotId);
        }
        if (indexProjectId != null) {
            return corpusAvailabilityGate.countVectorRows(indexProjectId, List.of(snapshotId)) > 0L;
        }
        return false;
    }

    public boolean snapshotSignatureCurrent(
            UUID indexProjectId,
            KnowledgeIndexSnapshotEntity snapshot,
            ProjectIndexProfile effectiveProfile) {
        if (indexProjectId == null || snapshot == null || effectiveProfile == null) {
            return true;
        }
        String stored = snapshot.getSignatureHash();
        if (stored == null || stored.isBlank()) {
            return false;
        }
        String current =
                knowledgePipelineOrchestrator.computeSnapshotSignatureHex(
                        indexProjectId, CorpusScope.PROJECT_SHARED, null, effectiveProfile);
        return Objects.equals(stored, current);
    }

    public ReuseEligibility evaluateReuse(
            UUID userId,
            UUID corpusId,
            UUID indexProjectId,
            KnowledgeIndexSnapshotEntity snapshot,
            ExperimentalPresetCanonicalCatalog.IndexRequirements requirements,
            String embeddingModelIdOverride,
            ProjectIndexProfile effectiveProfile,
            LabPresetRunGroupKey groupKey,
            boolean requireVectorRows) {
        if (snapshot == null || snapshot.getId() == null) {
            return ReuseEligibility.blocked(
                    LabCorpusReasonCodes.NO_COMPATIBLE_SNAPSHOT, "No compatible index snapshot was found.");
        }
        if (!profileCompatible(snapshot, requirements, embeddingModelIdOverride)) {
            return ReuseEligibility.blocked(
                    LabCorpusReasonCodes.NO_COMPATIBLE_SNAPSHOT,
                    "The active snapshot does not match preset index requirements.");
        }
        if (effectiveProfile != null
                && indexProjectId != null
                && !snapshotSignatureCurrent(indexProjectId, snapshot, effectiveProfile)) {
            return ReuseEligibility.blocked(
                    LabCorpusReasonCodes.SNAPSHOT_STALE,
                    "The snapshot document signature is stale; reindex is required.");
        }
        boolean needsRows =
                requireVectorRows
                        || (groupKey != null
                                ? requiresSnapshotBoundVectorRows(requirements, groupKey)
                                : requiresSnapshotBoundVectorRowsForCorpusPrepare());
        if (needsRows && !hasVectorRows(userId, corpusId, indexProjectId, snapshot.getId())) {
            return ReuseEligibility.blocked(
                    LabCorpusReasonCodes.SNAPSHOT_EMPTY,
                    "The snapshot has no vector rows for the evaluation corpus.");
        }
        return ReuseEligibility.ok();
    }

    public ReuseEligibility evaluateReuseForPreset(
            UUID userId,
            UUID corpusId,
            UUID indexProjectId,
            KnowledgeIndexSnapshotEntity snapshot,
            RagExperimentalPresetCode preset,
            String embeddingModelIdOverride,
            ProjectIndexProfile effectiveProfile) {
        if (preset == null) {
            return ReuseEligibility.blocked(LabCorpusReasonCodes.NO_COMPATIBLE_SNAPSHOT, "Preset is required.");
        }
        LabPresetRunGroupKey groupKey = LabPresetRunPlanService.groupKeyFor(preset);
        ExperimentalPresetCanonicalCatalog.IndexRequirements requirements =
                ExperimentalPresetCanonicalCatalog.effectiveIndexRequirements(preset);
        return evaluateReuse(
                userId,
                corpusId,
                indexProjectId,
                snapshot,
                requirements,
                embeddingModelIdOverride,
                effectiveProfile,
                groupKey,
                requiresSnapshotBoundVectorRows(preset));
    }
}
