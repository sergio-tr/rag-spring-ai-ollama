package com.uniovi.rag.application.service.runtime.config;

import com.uniovi.rag.application.service.evaluation.preset.ExperimentalPresetCanonicalCatalog;
import com.uniovi.rag.application.service.knowledge.KnowledgeSnapshotService;
import com.uniovi.rag.domain.knowledge.IndexSnapshotStatus;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeIndexSnapshotEntity;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Resolves a project-scoped index snapshot compatible with preset index requirements.
 *
 * <p>Projects may keep multiple ACTIVE snapshots (one per distinct index profile hash). Runtime execution
 * binds the snapshot whose materialization and metadata capabilities satisfy the selected preset.
 */
@Service
public class MaterializationAwareSnapshotResolver {

    public record ResolvedProjectSnapshot(
            UUID snapshotId,
            String indexProfileHash,
            Map<String, Object> indexProfile,
            IndexSnapshotCapabilities capabilities,
            boolean compatibleWithRequirements) {}

    private final KnowledgeSnapshotService knowledgeSnapshotService;

    public MaterializationAwareSnapshotResolver(KnowledgeSnapshotService knowledgeSnapshotService) {
        this.knowledgeSnapshotService = knowledgeSnapshotService;
    }

    public Optional<ResolvedProjectSnapshot> resolveProjectSnapshot(
            UUID projectId, ExperimentalPresetCanonicalCatalog.IndexRequirements requirements) {
        if (projectId == null) {
            return Optional.empty();
        }
        ExperimentalPresetCanonicalCatalog.IndexRequirements req =
                requirements != null ? requirements : ExperimentalPresetCanonicalCatalog.IndexRequirements.none();

        Optional<KnowledgeIndexSnapshotEntity> compatible =
                knowledgeSnapshotService.findProjectSnapshots(projectId).stream()
                        .filter(snap -> isActiveProjectSnapshotCompatible(snap, req))
                        .min(materializationPreferenceComparator(req));
        if (compatible.isPresent()) {
            return Optional.of(toResolved(compatible.get(), true));
        }

        return knowledgeSnapshotService
                .findActiveProjectSnapshot(projectId)
                .map(snap -> toResolved(snap, isActiveProjectSnapshotCompatible(snap, req)));
    }

    public static ExperimentalPresetCanonicalCatalog.IndexRequirements requirementsFromPresetAndRag(
            Optional<UUID> presetIdOpt, RagConfig rag) {
        if (presetIdOpt.isPresent()) {
            var code = ExperimentalPresetCanonicalCatalog.tryResolveCodeByProductPresetId(presetIdOpt.get());
            if (code != null) {
                ExperimentalPresetCanonicalCatalog.IndexRequirements catalog =
                        ExperimentalPresetCanonicalCatalog.effectiveIndexRequirements(code);
                if (catalog.requiredMaterialization() != null
                        && catalog.requiredMaterialization()
                                != ExperimentalPresetCanonicalCatalog.RequiredMaterialization.NONE) {
                    return catalog;
                }
                // P0/P1 and other NONE-catalog presets must not inherit runtime seed
                // materializationStrategy (e.g. CHUNK_LEVEL on P0) as an index binding requirement.
                if (catalog.requiredMaterialization()
                        == ExperimentalPresetCanonicalCatalog.RequiredMaterialization.NONE) {
                    return catalog;
                }
                ExperimentalPresetCanonicalCatalog.RequiredMaterialization fromRuntime =
                        requiredMaterializationFromRuntimeValues(
                                ExperimentalPresetCanonicalCatalog.effectiveRuntimeValues(code));
                if (fromRuntime != null
                        && fromRuntime != ExperimentalPresetCanonicalCatalog.RequiredMaterialization.NONE) {
                    return new ExperimentalPresetCanonicalCatalog.IndexRequirements(
                            fromRuntime, catalog.requiresMetadataSupport());
                }
                return catalog;
            }
        }
        if (rag == null || !rag.useRetrieval()) {
            return ExperimentalPresetCanonicalCatalog.IndexRequirements.none();
        }
        ExperimentalPresetCanonicalCatalog.RequiredMaterialization req =
                rag.materializationStrategy() != null
                        ? switch (rag.materializationStrategy()) {
                            case DOCUMENT_LEVEL ->
                                    ExperimentalPresetCanonicalCatalog.RequiredMaterialization.DOCUMENT_LEVEL;
                            case CHUNK_LEVEL -> ExperimentalPresetCanonicalCatalog.RequiredMaterialization.CHUNK_LEVEL;
                            case HYBRID -> ExperimentalPresetCanonicalCatalog.RequiredMaterialization.HYBRID;
                            default -> ExperimentalPresetCanonicalCatalog.RequiredMaterialization.CHUNK_LEVEL;
                        }
                        : ExperimentalPresetCanonicalCatalog.RequiredMaterialization.CHUNK_LEVEL;
        return new ExperimentalPresetCanonicalCatalog.IndexRequirements(req, rag.metadataEnabled());
    }

    private static ExperimentalPresetCanonicalCatalog.RequiredMaterialization requiredMaterializationFromRuntimeValues(
            Map<String, Object> runtimeValues) {
        if (runtimeValues == null || runtimeValues.isEmpty()) {
            return null;
        }
        Object raw = runtimeValues.get("materializationStrategy");
        if (!(raw instanceof String s) || s.isBlank()) {
            return null;
        }
        return switch (s.trim().toUpperCase()) {
            case "DOCUMENT_LEVEL" -> ExperimentalPresetCanonicalCatalog.RequiredMaterialization.DOCUMENT_LEVEL;
            case "HYBRID" -> ExperimentalPresetCanonicalCatalog.RequiredMaterialization.HYBRID;
            case "CHUNK_LEVEL" -> ExperimentalPresetCanonicalCatalog.RequiredMaterialization.CHUNK_LEVEL;
            default -> null;
        };
    }

    private static boolean isActiveProjectSnapshotCompatible(
            KnowledgeIndexSnapshotEntity snap,
            ExperimentalPresetCanonicalCatalog.IndexRequirements requirements) {
        if (snap == null || snap.getId() == null || snap.getStatus() != IndexSnapshotStatus.ACTIVE) {
            return false;
        }
        IndexSnapshotCapabilities caps = IndexSnapshotCapabilities.fromIndexProfile(snap.getIndexProfileJsonb());
        return IndexCompatibilityResult.check(requirements, true, caps).compatible();
    }

    private static ResolvedProjectSnapshot toResolved(
            KnowledgeIndexSnapshotEntity snap, boolean compatibleWithRequirements) {
        Map<String, Object> profile =
                snap.getIndexProfileJsonb() != null ? snap.getIndexProfileJsonb() : Map.of();
        return new ResolvedProjectSnapshot(
                snap.getId(),
                snap.getIndexProfileHash(),
                profile,
                IndexSnapshotCapabilities.fromIndexProfile(profile),
                compatibleWithRequirements);
    }

    /** Prefer exact materialization match, then HYBRID superset, then newest ACTIVE snapshot. */
    static Comparator<KnowledgeIndexSnapshotEntity> materializationPreferenceComparator(
            ExperimentalPresetCanonicalCatalog.IndexRequirements requirements) {
        return Comparator.comparingInt(
                        (KnowledgeIndexSnapshotEntity snap) ->
                                materializationPreferenceRank(
                                        requirements,
                                        IndexSnapshotCapabilities.fromIndexProfile(snap.getIndexProfileJsonb())))
                .thenComparing(newestActiveFirst());
    }

    private static int materializationPreferenceRank(
            ExperimentalPresetCanonicalCatalog.IndexRequirements requirements,
            IndexSnapshotCapabilities capabilities) {
        if (requirements == null
                || requirements.requiredMaterialization() == null
                || requirements.requiredMaterialization()
                        == ExperimentalPresetCanonicalCatalog.RequiredMaterialization.NONE) {
            return 0;
        }
        String required = requirements.requiredMaterialization().name();
        String snapStrategy = capabilities != null ? capabilities.materializationStrategy() : null;
        if (snapStrategy != null && required.equalsIgnoreCase(snapStrategy.trim())) {
            return 0;
        }
        if (snapStrategy != null && "HYBRID".equalsIgnoreCase(snapStrategy.trim())) {
            return 1;
        }
        return 2;
    }

    /** Prefer the newest ACTIVE snapshot when multiple rows match (stable tie-break). */
    static Comparator<KnowledgeIndexSnapshotEntity> newestActiveFirst() {
        return Comparator.comparing(
                        KnowledgeIndexSnapshotEntity::getUpdatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(
                        KnowledgeIndexSnapshotEntity::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder()));
    }
}
