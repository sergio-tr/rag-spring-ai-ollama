package com.uniovi.rag.application.service.evaluation.preset;

import com.uniovi.rag.application.service.runtime.config.IndexCompatibilityResult;
import com.uniovi.rag.application.service.runtime.config.IndexSnapshotCapabilities;
import com.uniovi.rag.domain.evaluation.workbook.RagExperimentalPresetCode;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationRunEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeIndexSnapshotEntity;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Builds {@link LabPresetRunPlanModels.LabPresetRunPlan} from canonical index requirements and corpus-scoped snapshots.
 */
@Service
public class LabPresetRunPlanService {

    private static final Comparator<LabPresetRunGroupKey> GROUP_ORDER =
            Comparator.comparingInt(Enum::ordinal);

    private final LabEvaluationSnapshotService labEvaluationSnapshotService;

    public LabPresetRunPlanService(LabEvaluationSnapshotService labEvaluationSnapshotService) {
        this.labEvaluationSnapshotService = labEvaluationSnapshotService;
    }

    /**
     * @param requested ordered preset codes from the benchmark catalog selection (may be subset of P0–P14).
     */
    public LabPresetRunPlanModels.LabPresetRunPlan build(EvaluationRunEntity run, List<RagExperimentalPresetCode> requested) {
        List<RagExperimentalPresetCode> ordered = dedupePreserveOrder(requested);
        if (run != null && run.getId() != null) {
            labEvaluationSnapshotService.ensureRunIndexProjectByRunId(run.getId());
        }

        Map<String, String> skipped = new LinkedHashMap<>();
        List<String> executable = new ArrayList<>();
        List<LabPresetRunPlanModels.LabPresetRunPlanItem> items = new ArrayList<>();

        Map<LabPresetRunGroupKey, List<RagExperimentalPresetCode>> byGroup = new LinkedHashMap<>();
        for (RagExperimentalPresetCode code : ordered) {
            byGroup.computeIfAbsent(groupKeyFor(code), k -> new ArrayList<>()).add(code);
        }

        List<LabPresetRunGroupKey> groupKeys = new ArrayList<>(byGroup.keySet());
        groupKeys.sort(GROUP_ORDER);

        List<LabPresetRunPlanModels.LabPresetRunGroup> groups = new ArrayList<>();
        for (LabPresetRunGroupKey gk : groupKeys) {
            List<RagExperimentalPresetCode> codes = byGroup.get(gk);
            if (codes == null || codes.isEmpty()) {
                continue;
            }
            codes.sort(Comparator.comparingInt(RagExperimentalPresetCode::ordinal));
            LabPresetRunPlanModels.LabPresetRunGroup gr = buildGroup(gk, codes, run, skipped, executable, items);
            groups.add(gr);
        }

        SnapshotContext snapCtx =
                resolveSnapshotContext(run, ExperimentalPresetCanonicalCatalog.IndexRequirements.none(), null);

        List<String> requestedStr = ordered.stream().map(RagExperimentalPresetCode::name).toList();
        UUID corpusId = labEvaluationSnapshotService.resolveCorpusId(run);
        return new LabPresetRunPlanModels.LabPresetRunPlan(
                groups,
                items,
                requestedStr,
                List.copyOf(executable),
                Map.copyOf(skipped),
                snapCtx.snapshotId,
                snapCtx.profileHash,
                snapCtx.hasUsableSnapshot,
                corpusId,
                LabPresetRunPlanModels.STRATEGY_VERSION,
                Instant.now());
    }

    private static List<RagExperimentalPresetCode> dedupePreserveOrder(List<RagExperimentalPresetCode> requested) {
        if (requested == null || requested.isEmpty()) {
            return List.of();
        }
        Set<RagExperimentalPresetCode> seen = new LinkedHashSet<>();
        List<RagExperimentalPresetCode> out = new ArrayList<>();
        for (RagExperimentalPresetCode c : requested) {
            if (c != null && seen.add(c)) {
                out.add(c);
            }
        }
        return out;
    }

    private LabPresetRunPlanModels.LabPresetRunGroup buildGroup(
            LabPresetRunGroupKey gk,
            List<RagExperimentalPresetCode> codes,
            EvaluationRunEntity run,
            Map<String, String> skipped,
            List<String> executable,
            List<LabPresetRunPlanModels.LabPresetRunPlanItem> items) {
        ExperimentalPresetCanonicalCatalog.IndexRequirements mergedAgg = codes.stream()
                .map(ExperimentalPresetCanonicalCatalog::effectiveIndexRequirements)
                .reduce(ExperimentalPresetCanonicalCatalog.IndexRequirements.none(), this::mergeRequirements);
        SnapshotContext snapCtx = resolveSnapshotContext(run, mergedAgg, run != null ? run.getEmbeddingModelId() : null);
        UUID corpusId = labEvaluationSnapshotService.resolveCorpusId(run);
        if (gk == LabPresetRunGroupKey.MULTI_TURN_UNSUPPORTED_IN_SINGLE_TURN) {
            for (RagExperimentalPresetCode c : codes) {
                skipped.put(c.name(), "MULTI_TURN_SINGLE_TURN_LAB_UNSUPPORTED");
                items.add(
                        new LabPresetRunPlanModels.LabPresetRunPlanItem(
                                c.name(),
                                gk,
                                false,
                                true,
                                indexRequirementsMap(ExperimentalPresetCanonicalCatalog.effectiveIndexRequirements(c)),
                                false,
                                false,
                                "NOT_SUPPORTED",
                                "MULTI_TURN_SINGLE_TURN_LAB_UNSUPPORTED",
                                "Preset requires multi-turn harness; not executed in single-turn Lab benchmark.",
                                snapCtx.snapshotId,
                                snapCtx.profileHash,
                                snapshotCapsMap(snapCtx.caps),
                                corpusId,
                                false,
                                LabPresetRunPlanModels.STRATEGY_VERSION));
            }
            return new LabPresetRunPlanModels.LabPresetRunGroup(
                    gk,
                    codes.stream().map(RagExperimentalPresetCode::name).toList(),
                    indexRequirementsMap(mergedAgg),
                    snapshotCapsMap(snapCtx.caps),
                    snapCtx.snapshotId,
                    false,
                    false,
                    "NOT_SUPPORTED",
                    "MULTI_TURN_SINGLE_TURN_LAB_UNSUPPORTED",
                    "Preset requires multi-turn harness; not executed in single-turn Lab benchmark.",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    corpusId);
        }

        boolean allCompatible = true;
        boolean anyRequiresReindex = false;
        String worstReasonCode = null;
        String worstReason = null;
        String worstStatus = "COMPATIBLE";
        for (RagExperimentalPresetCode c : codes) {
            ExperimentalPresetCanonicalCatalog.IndexRequirements req =
                    ExperimentalPresetCanonicalCatalog.effectiveIndexRequirements(c);
            IndexCompatibilityResult idx = labIndexCompatibility(req, snapCtx, c);
            if (!idx.compatible()) {
                allCompatible = false;
            }
            if (idx.requiresReindex()) {
                anyRequiresReindex = true;
            }
            if (!idx.compatible() && idx.reasonCode() != null) {
                worstReasonCode = idx.reasonCode();
                worstReason = idx.message();
                worstStatus = idx.status();
            }
        }

        if (allCompatible) {
            for (RagExperimentalPresetCode c : codes) {
                executable.add(c.name());
                items.add(
                        new LabPresetRunPlanModels.LabPresetRunPlanItem(
                                c.name(),
                                gk,
                                true,
                                false,
                                indexRequirementsMap(ExperimentalPresetCanonicalCatalog.effectiveIndexRequirements(c)),
                                true,
                                false,
                                "COMPATIBLE",
                                null,
                                null,
                                snapCtx.snapshotId,
                                snapCtx.profileHash,
                                snapshotCapsMap(snapCtx.caps),
                                corpusId,
                                false,
                                LabPresetRunPlanModels.STRATEGY_VERSION));
            }
        } else {
            for (RagExperimentalPresetCode c : codes) {
                skipped.put(c.name(), worstReasonCode != null ? worstReasonCode : "INDEX_INCOMPATIBLE");
                boolean requiresReindex = IndexCompatibilityResult
                        .check(ExperimentalPresetCanonicalCatalog.effectiveIndexRequirements(c), snapCtx.hasUsableSnapshot, snapCtx.caps)
                        .requiresReindex();
                if (!snapCtx.hasUsableSnapshot && ExperimentalPresetCanonicalCatalog.needsVectorIndex(c)) {
                    requiresReindex = true;
                }
                items.add(
                        new LabPresetRunPlanModels.LabPresetRunPlanItem(
                                c.name(),
                                gk,
                                true,
                                false,
                                indexRequirementsMap(ExperimentalPresetCanonicalCatalog.effectiveIndexRequirements(c)),
                                false,
                                requiresReindex,
                                worstStatus,
                                worstReasonCode != null ? worstReasonCode : "INDEX_INCOMPATIBLE",
                                worstReason,
                                snapCtx.snapshotId,
                                snapCtx.profileHash,
                                snapshotCapsMap(snapCtx.caps),
                                corpusId,
                                false,
                                LabPresetRunPlanModels.STRATEGY_VERSION));
            }
        }

        boolean groupRequiresReindex = !allCompatible && anyRequiresReindex;
        return new LabPresetRunPlanModels.LabPresetRunGroup(
                gk,
                codes.stream().map(RagExperimentalPresetCode::name).toList(),
                indexRequirementsMap(mergedAgg),
                snapshotCapsMap(snapCtx.caps),
                snapCtx.snapshotId,
                allCompatible,
                groupRequiresReindex,
                allCompatible ? "COMPATIBLE" : worstStatus,
                worstReasonCode,
                worstReason,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                corpusId);
    }

    private ExperimentalPresetCanonicalCatalog.IndexRequirements mergeRequirements(
            ExperimentalPresetCanonicalCatalog.IndexRequirements a,
            ExperimentalPresetCanonicalCatalog.IndexRequirements b) {
        if (b == null) {
            return a;
        }
        if (a == null) {
            return b;
        }
        ExperimentalPresetCanonicalCatalog.RequiredMaterialization mat =
                mergeMat(a.requiredMaterialization(), b.requiredMaterialization());
        boolean meta = a.requiresMetadataSupport() || b.requiresMetadataSupport();
        return new ExperimentalPresetCanonicalCatalog.IndexRequirements(mat, meta);
    }

    private static ExperimentalPresetCanonicalCatalog.RequiredMaterialization mergeMat(
            ExperimentalPresetCanonicalCatalog.RequiredMaterialization x,
            ExperimentalPresetCanonicalCatalog.RequiredMaterialization y) {
        if (x == null || x == ExperimentalPresetCanonicalCatalog.RequiredMaterialization.NONE) {
            return y != null ? y : x;
        }
        if (y == null || y == ExperimentalPresetCanonicalCatalog.RequiredMaterialization.NONE) {
            return x;
        }
        int ox = order(x);
        int oy = order(y);
        return ox >= oy ? x : y;
    }

    private static int order(ExperimentalPresetCanonicalCatalog.RequiredMaterialization m) {
        return switch (m) {
            case NONE -> 0;
            case DOCUMENT_LEVEL -> 1;
            case CHUNK_LEVEL -> 2;
            case HYBRID -> 3;
        };
    }

    public static LabPresetRunGroupKey groupKeyFor(RagExperimentalPresetCode code) {
        if (ExperimentalPresetCanonicalCatalog.requiresMultiTurn(code)) {
            return LabPresetRunGroupKey.MULTI_TURN_UNSUPPORTED_IN_SINGLE_TURN;
        }
        ExperimentalPresetCanonicalCatalog.IndexRequirements eff =
                ExperimentalPresetCanonicalCatalog.effectiveIndexRequirements(code);
        if (eff.requiredMaterialization() == null
                || eff.requiredMaterialization() == ExperimentalPresetCanonicalCatalog.RequiredMaterialization.NONE) {
            return LabPresetRunGroupKey.NO_INDEX;
        }
        return switch (eff.requiredMaterialization()) {
            case DOCUMENT_LEVEL -> LabPresetRunGroupKey.DOCUMENT_LEVEL;
            case CHUNK_LEVEL ->
                    eff.requiresMetadataSupport()
                            ? LabPresetRunGroupKey.CHUNK_LEVEL_METADATA
                            : LabPresetRunGroupKey.CHUNK_LEVEL;
            case HYBRID -> LabPresetRunGroupKey.HYBRID_METADATA;
            case NONE -> LabPresetRunGroupKey.NO_INDEX;
        };
    }

    /** Preset execution order: group order then ordinal within group. */
    public List<RagExperimentalPresetCode> sortDefinitionsOrder(List<RagExperimentalPresetCode> codes) {
        List<RagExperimentalPresetCode> copy = new ArrayList<>(codes);
        copy.sort(
                Comparator.<RagExperimentalPresetCode, LabPresetRunGroupKey>comparing(LabPresetRunPlanService::groupKeyFor)
                        .thenComparingInt(RagExperimentalPresetCode::ordinal));
        return copy;
    }

    private SnapshotContext resolveSnapshotContext(
            EvaluationRunEntity run,
            ExperimentalPresetCanonicalCatalog.IndexRequirements requirements,
            String embeddingModelId) {
        LabEvaluationSnapshotService.ResolvedSnapshot resolved =
                labEvaluationSnapshotService.resolveCompatibleSnapshot(run, requirements, embeddingModelId);
        boolean hasUsableSnapshot = resolved.hasUsableSnapshot();
        IndexSnapshotCapabilities caps =
                resolved.capabilities() != null
                        ? resolved.capabilities()
                        : IndexSnapshotCapabilities.fromIndexProfile(Map.of());
        return new SnapshotContext(hasUsableSnapshot, caps, resolved.snapshotId(), resolved.indexProfileHash());
    }

    private static IndexCompatibilityResult labIndexCompatibility(
            ExperimentalPresetCanonicalCatalog.IndexRequirements req,
            SnapshotContext snapCtx,
            RagExperimentalPresetCode preset) {
        if (req == null
                || req.requiredMaterialization() == null
                || req.requiredMaterialization() == ExperimentalPresetCanonicalCatalog.RequiredMaterialization.NONE) {
            return IndexCompatibilityResult.ok();
        }
        if (ExperimentalPresetCanonicalCatalog.canRunWithoutRetrieval(preset)) {
            return IndexCompatibilityResult.ok();
        }
        if (snapCtx == null || !snapCtx.hasUsableSnapshot()) {
            return IndexCompatibilityResult.requiresReindex(
                    LabEvaluationSnapshotService.REINDEX_REQUIRED,
                    "Documents may exist, but no compatible snapshot was selected for this preset.");
        }
        IndexCompatibilityResult idx = IndexCompatibilityResult.check(req, true, snapCtx.caps());
        if (idx.compatible()) {
            return idx;
        }
        return IndexCompatibilityResult.requiresReindex(
                LabEvaluationSnapshotService.NO_COMPATIBLE_SNAPSHOT,
                idx.message() != null ? idx.message() : "No compatible snapshot satisfies this preset.");
    }

    private static Map<String, Object> indexRequirementsMap(ExperimentalPresetCanonicalCatalog.IndexRequirements req) {
        if (req == null) {
            return Map.of();
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(
                "requiredMaterializationStrategy",
                req.requiredMaterialization() != null ? req.requiredMaterialization().name() : null);
        m.put("requiresMetadataSupport", req.requiresMetadataSupport());
        return m;
    }

    private static Map<String, Object> snapshotCapsMap(IndexSnapshotCapabilities caps) {
        if (caps == null) {
            return Map.of();
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("materializationStrategy", caps.materializationStrategy());
        m.put("supportsMetadata", caps.supportsMetadata());
        m.put("embeddingModelId", caps.embeddingModelId());
        m.put("chunkMaxChars", caps.chunkMaxChars());
        m.put("chunkOverlap", caps.chunkOverlap());
        return m;
    }

    private record SnapshotContext(boolean hasUsableSnapshot, IndexSnapshotCapabilities caps, UUID snapshotId, String profileHash) {}
}
