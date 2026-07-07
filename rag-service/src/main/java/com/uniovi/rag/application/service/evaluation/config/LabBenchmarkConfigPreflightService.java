package com.uniovi.rag.application.service.evaluation.config;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.uniovi.rag.application.service.embedding.EmbeddingOptionsValidator;
import com.uniovi.rag.application.service.evaluation.StartBenchmarkRunRequest;
import com.uniovi.rag.application.service.evaluation.corpus.EvaluationCorpusApplicationService;
import com.uniovi.rag.application.service.evaluation.corpus.LabCorpusReasonCodes;
import com.uniovi.rag.application.service.llm.catalog.EvaluationModelCatalogService;
import com.uniovi.rag.application.service.evaluation.preset.CorpusAvailabilityGate;
import com.uniovi.rag.application.service.evaluation.preset.ExperimentalPresetBenchmarkGate;
import com.uniovi.rag.application.service.evaluation.preset.ExperimentalPresetCanonicalCatalog;
import com.uniovi.rag.application.service.evaluation.preset.LabIndexSnapshotCompatibilityService;
import com.uniovi.rag.application.service.evaluation.preset.LabPresetRunGroupKey;
import com.uniovi.rag.application.service.evaluation.preset.LabPresetRunPlanService;
import com.uniovi.rag.application.service.knowledge.KnowledgeIndexSnapshotProfileAccess;
import com.uniovi.rag.application.service.knowledge.KnowledgeSnapshotService;
import com.uniovi.rag.application.service.knowledge.LabIndexProfileOverrideFactory;
import com.uniovi.rag.application.service.knowledge.ProjectIndexProfileService;
import com.uniovi.rag.infrastructure.persistence.KnowledgeIndexSnapshotRepository;
import com.uniovi.rag.application.service.runtime.config.IndexCompatibilityResult;
import com.uniovi.rag.application.service.runtime.config.IndexSnapshotCapabilities;
import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.domain.evaluation.BenchmarkKind;
import com.uniovi.rag.domain.evaluation.workbook.RagExperimentalPresetCode;
import com.uniovi.rag.domain.knowledge.ProjectIndexProfile;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeIndexSnapshotEntity;
import com.uniovi.rag.infrastructure.vector.EmbeddingSpaceGuard;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Lab benchmark runtime/preset preflight (M4). Runs after M3 corpus readiness and before HTTP 202.
 */
@Service
public class LabBenchmarkConfigPreflightService {

    private final RagFeatureConfiguration ragFeatureConfiguration;
    private final KnowledgeSnapshotService knowledgeSnapshotService;
    private final EmbeddingSpaceGuard embeddingSpaceGuard;
    private final LabIndexSnapshotCompatibilityService indexSnapshotCompatibilityService;
    private final EvaluationCorpusApplicationService evaluationCorpusApplicationService;
    private final ProjectIndexProfileService projectIndexProfileService;
    private final LabIndexProfileOverrideFactory labIndexProfileOverrideFactory;
    private final CorpusAvailabilityGate corpusAvailabilityGate;
    private final EvaluationModelCatalogService evaluationModelCatalogService;
    private final EmbeddingOptionsValidator embeddingOptionsValidator;
    private final KnowledgeIndexSnapshotRepository knowledgeIndexSnapshotRepository;
    private final KnowledgeIndexSnapshotProfileAccess snapshotProfileAccess;

    public LabBenchmarkConfigPreflightService(
            RagFeatureConfiguration ragFeatureConfiguration,
            KnowledgeSnapshotService knowledgeSnapshotService,
            EmbeddingSpaceGuard embeddingSpaceGuard,
            LabIndexSnapshotCompatibilityService indexSnapshotCompatibilityService,
            EvaluationCorpusApplicationService evaluationCorpusApplicationService,
            ProjectIndexProfileService projectIndexProfileService,
            LabIndexProfileOverrideFactory labIndexProfileOverrideFactory,
            CorpusAvailabilityGate corpusAvailabilityGate,
            EvaluationModelCatalogService evaluationModelCatalogService,
            EmbeddingOptionsValidator embeddingOptionsValidator,
            KnowledgeIndexSnapshotRepository knowledgeIndexSnapshotRepository,
            KnowledgeIndexSnapshotProfileAccess snapshotProfileAccess) {
        this.ragFeatureConfiguration = ragFeatureConfiguration;
        this.knowledgeSnapshotService = knowledgeSnapshotService;
        this.embeddingSpaceGuard = embeddingSpaceGuard;
        this.indexSnapshotCompatibilityService = indexSnapshotCompatibilityService;
        this.evaluationCorpusApplicationService = evaluationCorpusApplicationService;
        this.projectIndexProfileService = projectIndexProfileService;
        this.labIndexProfileOverrideFactory = labIndexProfileOverrideFactory;
        this.corpusAvailabilityGate = corpusAvailabilityGate;
        this.evaluationModelCatalogService = evaluationModelCatalogService;
        this.embeddingOptionsValidator = embeddingOptionsValidator;
        this.knowledgeIndexSnapshotRepository = knowledgeIndexSnapshotRepository;
        this.snapshotProfileAccess = snapshotProfileAccess;
    }

    /**
     * Validates preset/runtime configuration. Throws {@link ResponseStatusException} with a stable config code as
     * {@link ResponseStatusException#getReason()}.
     */
    public LabBenchmarkConfigPreflightResult validateOrThrow(
            UUID userId, BenchmarkKind kind, StartBenchmarkRunRequest request) {
        if (kind == null || request == null) {
            fail(HttpStatus.BAD_REQUEST, LabRuntimeConfigReasonCodes.CONFIG_VALIDATION_ERROR);
        }
        return switch (kind) {
            case RAG_PRESET_END_TO_END -> validateRag(userId, request);
            case EMBEDDING_RETRIEVAL -> validateEmbedding(userId, request);
            default -> okSummary(List.of(), null, request.autoReindexEffective(), false, Map.of());
        };
    }

    private LabBenchmarkConfigPreflightResult validateRag(UUID userId, StartBenchmarkRunRequest request) {
        List<String> rawCodes = request.experimentalPresetCodes();
        if (rawCodes == null || rawCodes.isEmpty()) {
            fail(HttpStatus.BAD_REQUEST, LabRuntimeConfigReasonCodes.EXPERIMENTAL_PRESET_CODES_EMPTY);
        }

        List<RagExperimentalPresetCode> presets = new ArrayList<>();
        for (String raw : rawCodes) {
            RagExperimentalPresetCode code = parsePresetCode(raw);
            presets.add(code);
            Optional<String> harness = ExperimentalPresetBenchmarkGate.blockReason(code);
            if (harness.isPresent()) {
                fail(HttpStatus.BAD_REQUEST, harness.get());
            }
            if (!ExperimentalPresetCanonicalCatalog.singleTurnBenchmarkSelectable(code)) {
                fail(HttpStatus.BAD_REQUEST, LabRuntimeConfigReasonCodes.PRESET_NOT_LAB_SELECTABLE);
            }
            RagConfig effective = effectiveRagConfig(code);
            Optional<String> runtimeBlock = runtimeBlockReason(effective);
            if (runtimeBlock.isPresent()) {
                fail(HttpStatus.BAD_REQUEST, mapRuntimeBlockToConfigCode(runtimeBlock.get()));
            }
        }

        // M4 default: index/snapshot compatibility is checked at enqueue whenever the selected tier
        // requires a snapshot, regardless of autoReindex (rebuild does not skip incompatibility).
        boolean strictIndexCheck =
                presets.stream().anyMatch(ExperimentalPresetCanonicalCatalog::requiresSnapshotForExecution);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("presetCodes", presets.stream().map(Enum::name).toList());

        if (request.corpusId() != null && strictIndexCheck) {
            if (request.autoReindexEffective()) {
                details.put("indexPreflight", "DEFERRED_AUTO_REINDEX");
            } else if (request.indexSnapshotId() != null) {
                validateExplicitSnapshotForPresets(
                        userId, request.corpusId(), request.indexSnapshotId(), presets, request, details);
            } else {
                validateIndexForPresets(userId, request.corpusId(), presets, request, details);
            }
        }

        validateP1CorpusSnapshotCompatibility(userId, request, presets, details);

        boolean needsEmbedding = presets.stream().anyMatch(ExperimentalPresetCanonicalCatalog::embeddingRequired);
        if (needsEmbedding) {
            evaluationModelCatalogService.assertHasCompatibleEmbeddingWhenRequired(userId);
        }

        String llmModelId = request.llmModelId() != null && !request.llmModelId().isBlank()
                ? request.llmModelId().trim()
                : null;
        if (llmModelId != null) {
            evaluationModelCatalogService.assertChatModelInCatalog(userId, llmModelId);
            details.put("llmModelId", llmModelId);
        }

        String embeddingModelId = resolveEmbeddingModelId(request);
        if (embeddingModelId != null && presets.stream().anyMatch(ExperimentalPresetCanonicalCatalog::embeddingRequired)) {
            evaluationModelCatalogService.assertEmbeddingCompatibleWithVectorStore(userId, embeddingModelId);
            embeddingOptionsValidator.validateRuntimeParameters(
                    userId, embeddingModelId, request.benchmarkRuntimeParameters());
            details.put("embeddingModelId", embeddingModelId);
        }

        return okSummary(
                presets.stream().map(Enum::name).toList(),
                embeddingModelId,
                request.autoReindexEffective(),
                strictIndexCheck,
                details);
    }

    private LabBenchmarkConfigPreflightResult validateEmbedding(UUID userId, StartBenchmarkRunRequest request) {
        evaluationModelCatalogService.assertHasCompatibleEmbeddingWhenRequired(userId);
        List<String> embeddingModelIds = resolveEmbeddingModelIds(request);
        if (embeddingModelIds.isEmpty()) {
            return okSummary(List.of(), null, request.autoReindexEffective(), false, Map.of());
        }
        for (String embeddingModelId : embeddingModelIds) {
            evaluationModelCatalogService.assertEmbeddingCompatibleWithVectorStore(userId, embeddingModelId);
            embeddingOptionsValidator.validateRuntimeParameters(
                    userId, embeddingModelId, request.benchmarkRuntimeParameters());
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("embeddingModelIds", embeddingModelIds);
        return okSummary(
                List.of(),
                embeddingModelIds.getFirst(),
                request.autoReindexEffective(),
                false,
                details);
    }

    private void validateP1CorpusSnapshotCompatibility(
            UUID userId,
            StartBenchmarkRunRequest request,
            List<RagExperimentalPresetCode> presets,
            Map<String, Object> details) {
        if (request.corpusId() == null || presets == null || presets.isEmpty()) {
            return;
        }
        boolean includesP1 = presets.stream().anyMatch(p -> p == RagExperimentalPresetCode.P1);
        if (!includesP1) {
            return;
        }

        Optional<KnowledgeIndexSnapshotEntity> active =
                knowledgeSnapshotService.findActiveCorpusSnapshot(request.corpusId());
        List<UUID> snapshotIds = active.map(s -> List.of(s.getId())).orElse(List.of());
        CorpusAvailabilityGate.Result gate =
                corpusAvailabilityGate.evaluateForPreset(
                        userId, request.corpusId(), snapshotIds, RagExperimentalPresetCode.P1);
        Map<String, Object> probe =
                corpusAvailabilityGate.probeForPreset(
                        userId, request.corpusId(), snapshotIds, RagExperimentalPresetCode.P1);

        details.put("p1CorpusEvidenceProbe", probe);
        details.put("corpusId", request.corpusId().toString());
        if (request.datasetId() != null) {
            details.put("datasetId", request.datasetId().toString());
        }
        details.put(
                "activeSnapshotId",
                active.map(s -> s.getId().toString()).orElse(null));

        if (gate.satisfied()) {
            details.put("p1SnapshotCompatible", true);
            details.put("p1SnapshotPreflight", "COMPATIBLE");
            return;
        }

        // P1 is in NO_INDEX group: generic index preflight deferral does not apply; auto-reindex prepares P1 rows.
        if (request.autoReindexEffective() && request.allowActiveSnapshotMutationEffective()) {
            details.put("p1SnapshotCompatible", "DEFERRED");
            details.put("p1SnapshotPreflight", "DEFERRED_AUTO_REINDEX");
            details.put(
                    "p1DeferredReason",
                    "P1 requires snapshot-bound vector rows; auto-reindex will prepare the NO_INDEX group snapshot.");
            return;
        }

        fail(HttpStatus.BAD_REQUEST, mapP1GateToConfigCode(gate.reasonCode()));
    }

    private static String mapP1GateToConfigCode(String reasonCode) {
        if (CorpusAvailabilityGate.SNAPSHOT_VECTOR_ROWS_MISSING.equals(reasonCode)
                || CorpusAvailabilityGate.REINDEX_REQUIRED.equals(reasonCode)
                || LabCorpusReasonCodes.SNAPSHOT_EMPTY.equals(reasonCode)) {
            return LabRuntimeConfigReasonCodes.FEATURE_REQUIRES_REINDEX;
        }
        if (CorpusAvailabilityGate.NO_COMPATIBLE_SNAPSHOT.equals(reasonCode)) {
            return LabRuntimeConfigReasonCodes.SNAPSHOT_CONFIG_MISMATCH;
        }
        return LabRuntimeConfigReasonCodes.FEATURE_REQUIRES_REINDEX;
    }

    private void validateExplicitSnapshotForPresets(
            UUID userId,
            UUID corpusId,
            UUID indexSnapshotId,
            List<RagExperimentalPresetCode> presets,
            StartBenchmarkRunRequest request,
            Map<String, Object> details) {
        KnowledgeIndexSnapshotEntity explicit =
                knowledgeIndexSnapshotRepository.findById(indexSnapshotId).orElse(null);
        if (explicit == null || explicit.getId() == null) {
            fail(HttpStatus.BAD_REQUEST, LabRuntimeConfigReasonCodes.FEATURE_REQUIRES_INDEX);
        }
        details.put("explicitIndexSnapshotId", indexSnapshotId.toString());
        RagExperimentalPresetCode strictest =
                presets.stream().max(Comparator.comparingInt(Enum::ordinal)).orElseThrow();
        validateActiveSnapshotReuseEligibility(
                userId,
                corpusId,
                strictest,
                explicit,
                resolveEmbeddingModelId(request),
                details);
        details.put("indexPreflight", "EXPLICIT_SNAPSHOT_COMPATIBLE");
    }

    private void validateActiveSnapshotReuseEligibility(
            UUID userId,
            UUID corpusId,
            RagExperimentalPresetCode strictest,
            KnowledgeIndexSnapshotEntity active,
            String embeddingModelIdOverride,
            Map<String, Object> details) {
        if (active == null || active.getId() == null) {
            fail(HttpStatus.BAD_REQUEST, LabRuntimeConfigReasonCodes.FEATURE_REQUIRES_INDEX);
        }
        EvaluationCorpusApplicationService.EvaluationCorpusContext context;
        try {
            context = evaluationCorpusApplicationService.requireContext(userId, corpusId);
        } catch (Exception ex) {
            fail(HttpStatus.BAD_REQUEST, LabRuntimeConfigReasonCodes.FEATURE_REQUIRES_INDEX);
            return;
        }
        if (context == null) {
            fail(HttpStatus.BAD_REQUEST, LabRuntimeConfigReasonCodes.FEATURE_REQUIRES_INDEX);
            return;
        }
        UUID indexProjectId = context.indexProjectId();
        LabPresetRunGroupKey groupKey = LabPresetRunPlanService.groupKeyFor(strictest);
        ExperimentalPresetCanonicalCatalog.IndexRequirements requirements =
                ExperimentalPresetCanonicalCatalog.effectiveIndexRequirements(strictest);
        ProjectIndexProfile base = projectIndexProfileService.ensureDefault(indexProjectId);
        if (embeddingModelIdOverride != null && !embeddingModelIdOverride.isBlank()) {
            base = labIndexProfileOverrideFactory.withEmbeddingModelId(base, embeddingModelIdOverride);
        }
        ProjectIndexProfile effective =
                labIndexProfileOverrideFactory.buildEffectiveProfile(base, requirements, groupKey);
        LabIndexSnapshotCompatibilityService.ReuseEligibility eligibility =
                indexSnapshotCompatibilityService.evaluateReuse(
                        userId,
                        corpusId,
                        indexProjectId,
                        active,
                        requirements,
                        embeddingModelIdOverride,
                        effective,
                        groupKey,
                        indexSnapshotCompatibilityService.requiresSnapshotBoundVectorRows(strictest));
        details.put("activeSnapshotReuseEligible", eligibility.eligible());
        if (!eligibility.eligible()) {
            fail(HttpStatus.BAD_REQUEST, mapReuseBlockerToConfigCode(eligibility.reasonCode()));
        }
    }

    private void validateIndexForPresets(
            UUID userId,
            UUID corpusId,
            List<RagExperimentalPresetCode> presets,
            StartBenchmarkRunRequest request,
            Map<String, Object> details) {
        List<RagExperimentalPresetCode> snapshotPresets =
                presets.stream().filter(ExperimentalPresetCanonicalCatalog::requiresSnapshotForExecution).toList();
        if (snapshotPresets.isEmpty()) {
            return;
        }

        Optional<KnowledgeIndexSnapshotEntity> active = knowledgeSnapshotService.findActiveCorpusSnapshot(corpusId);
        UUID activeId = active.map(KnowledgeIndexSnapshotEntity::getId).orElse(null);
        details.put("activeSnapshotId", activeId != null ? activeId.toString() : null);
        RagExperimentalPresetCode strictest =
                snapshotPresets.stream().max(Comparator.comparingInt(Enum::ordinal)).orElseThrow();
        details.put("strictestPreset", strictest.name());

        String embeddingModelId = resolveEmbeddingModelId(request);
        if (embeddingModelId != null) {
            details.put("indexPreflightEmbeddingModelId", embeddingModelId);
        }

        LinkedHashMap<String, Object> groupPreflight = new LinkedHashMap<>();
        LinkedHashSet<LabPresetRunGroupKey> groupKeys = new LinkedHashSet<>();
        for (RagExperimentalPresetCode preset : snapshotPresets) {
            groupKeys.add(LabPresetRunPlanService.groupKeyFor(preset));
        }
        boolean allGroupsEligible = true;
        for (LabPresetRunGroupKey groupKey : groupKeys) {
            RagExperimentalPresetCode representative =
                    snapshotPresets.stream()
                            .filter(p -> LabPresetRunPlanService.groupKeyFor(p) == groupKey)
                            .max(Comparator.comparingInt(Enum::ordinal))
                            .orElseThrow();
            Map<String, Object> groupDetails = new LinkedHashMap<>();
            boolean eligible =
                    validatePresetGroupSnapshotReuse(
                            userId, corpusId, representative, groupKey, embeddingModelId, groupDetails);
            groupPreflight.put(groupKey.name(), groupDetails);
            allGroupsEligible = allGroupsEligible && eligible;
        }
        details.put("presetGroupIndexPreflight", groupPreflight);
        details.put("activeSnapshotReuseEligible", allGroupsEligible);
    }

    /**
     * Validates reuse for the corpus snapshot that matches the preset run group (e.g. CHUNK_LEVEL for P3), not only the
     * corpus-active snapshot (which may be HYBRID for P8/P10).
     */
    private boolean validatePresetGroupSnapshotReuse(
            UUID userId,
            UUID corpusId,
            RagExperimentalPresetCode preset,
            LabPresetRunGroupKey groupKey,
            String embeddingModelIdOverride,
            Map<String, Object> groupDetails) {
        groupDetails.put("presetCode", preset.name());
        groupDetails.put("groupKey", groupKey.name());

        EvaluationCorpusApplicationService.EvaluationCorpusContext context;
        try {
            context = evaluationCorpusApplicationService.requireContext(userId, corpusId);
        } catch (Exception ex) {
            fail(HttpStatus.BAD_REQUEST, LabRuntimeConfigReasonCodes.FEATURE_REQUIRES_INDEX);
            return false;
        }
        if (context == null) {
            fail(HttpStatus.BAD_REQUEST, LabRuntimeConfigReasonCodes.FEATURE_REQUIRES_INDEX);
            return false;
        }
        UUID indexProjectId = context.indexProjectId();
        ExperimentalPresetCanonicalCatalog.IndexRequirements requirements =
                ExperimentalPresetCanonicalCatalog.effectiveIndexRequirements(preset);
        ProjectIndexProfile base = projectIndexProfileService.ensureDefault(indexProjectId);
        if (embeddingModelIdOverride != null && !embeddingModelIdOverride.isBlank()) {
            base = labIndexProfileOverrideFactory.withEmbeddingModelId(base, embeddingModelIdOverride);
        }
        ProjectIndexProfile effective =
                labIndexProfileOverrideFactory.buildEffectiveProfile(base, requirements, groupKey);

        Optional<KnowledgeIndexSnapshotEntity> compatible =
                findCorpusSnapshotForGroup(corpusId, requirements, groupKey, embeddingModelIdOverride);
        if (compatible.isEmpty()) {
            groupDetails.put("compatibleSnapshotId", null);
            groupDetails.put("reuseEligible", false);
            groupDetails.put("reasonCode", LabCorpusReasonCodes.NO_COMPATIBLE_SNAPSHOT);
            boolean hasAnyCorpusSnapshot =
                    knowledgeSnapshotService.findCorpusSnapshots(corpusId).stream().findAny().isPresent();
            fail(
                    HttpStatus.BAD_REQUEST,
                    hasAnyCorpusSnapshot
                            ? LabRuntimeConfigReasonCodes.SNAPSHOT_CONFIG_MISMATCH
                            : LabRuntimeConfigReasonCodes.FEATURE_REQUIRES_INDEX);
            return false;
        }

        KnowledgeIndexSnapshotEntity snapshot = compatible.get();
        groupDetails.put("compatibleSnapshotId", snapshot.getId().toString());
        IndexSnapshotCapabilities caps =
                IndexSnapshotCapabilities.fromIndexProfile(snapshotProfileAccess.resolveProfileJsonb(snapshot));
        groupDetails.put("materializationStrategy", caps.materializationStrategy());
        groupDetails.put("embeddingModelId", caps.embeddingModelId());

        LabIndexSnapshotCompatibilityService.ReuseEligibility eligibility =
                indexSnapshotCompatibilityService.evaluateReuse(
                        userId,
                        corpusId,
                        indexProjectId,
                        snapshot,
                        requirements,
                        embeddingModelIdOverride,
                        effective,
                        groupKey,
                        indexSnapshotCompatibilityService.requiresSnapshotBoundVectorRows(preset));
        groupDetails.put("reuseEligible", eligibility.eligible());
        if (eligibility.reasonCode() != null) {
            groupDetails.put("reasonCode", eligibility.reasonCode());
        }
        if (!eligibility.eligible()) {
            fail(HttpStatus.BAD_REQUEST, mapReuseBlockerToConfigCode(eligibility.reasonCode()));
            return false;
        }
        return true;
    }

    private Optional<KnowledgeIndexSnapshotEntity> findCorpusSnapshotForGroup(
            UUID corpusId,
            ExperimentalPresetCanonicalCatalog.IndexRequirements requirements,
            LabPresetRunGroupKey groupKey,
            String embeddingModelIdOverride) {
        String requiredMaterialization = requiredMaterializationForGroup(groupKey);
        return knowledgeSnapshotService.findCorpusSnapshots(corpusId).stream()
                .filter(
                        snap ->
                                indexSnapshotCompatibilityService.profileCompatible(
                                        snap, requirements, embeddingModelIdOverride))
                .filter(
                        snap ->
                                requiredMaterialization == null
                                        || requiredMaterialization.equalsIgnoreCase(
                                                IndexSnapshotCapabilities.fromIndexProfile(
                                                                snapshotProfileAccess.resolveProfileJsonb(snap))
                                                        .materializationStrategy()))
                .findFirst();
    }

    private static String requiredMaterializationForGroup(LabPresetRunGroupKey groupKey) {
        if (groupKey == null) {
            return null;
        }
        return switch (groupKey) {
            case DOCUMENT_LEVEL -> "DOCUMENT_LEVEL";
            case CHUNK_LEVEL, CHUNK_LEVEL_METADATA -> "CHUNK_LEVEL";
            case HYBRID_METADATA -> "HYBRID";
            case DIRECT_LLM, NO_INDEX, MULTI_TURN_UNSUPPORTED_IN_SINGLE_TURN -> null;
        };
    }

    private static String mapReuseBlockerToConfigCode(String reasonCode) {
        if (LabCorpusReasonCodes.SNAPSHOT_STALE.equals(reasonCode)) {
            return LabRuntimeConfigReasonCodes.FEATURE_REQUIRES_REINDEX;
        }
        if (LabCorpusReasonCodes.SNAPSHOT_EMPTY.equals(reasonCode)
                || LabCorpusReasonCodes.SNAPSHOT_VECTOR_ROWS_MISSING.equals(reasonCode)) {
            return LabRuntimeConfigReasonCodes.FEATURE_REQUIRES_REINDEX;
        }
        if (LabCorpusReasonCodes.NO_COMPATIBLE_SNAPSHOT.equals(reasonCode)) {
            return LabRuntimeConfigReasonCodes.SNAPSHOT_CONFIG_MISMATCH;
        }
        return LabRuntimeConfigReasonCodes.FEATURE_REQUIRES_REINDEX;
    }

    private static String mapIndexCompatibilityToConfigCode(IndexCompatibilityResult idx) {
        if (idx == null) {
            return LabRuntimeConfigReasonCodes.CONFIG_VALIDATION_ERROR;
        }
        if ("NO_ACTIVE_INDEX".equals(idx.reasonCode()) || !idx.compatible() && idx.status() != null && idx.status().contains("NO_ACTIVE")) {
            return LabRuntimeConfigReasonCodes.FEATURE_REQUIRES_INDEX;
        }
        if (idx.requiresReindex()) {
            String rc = idx.reasonCode();
            if (rc != null
                    && (rc.contains("MATERIALIZATION")
                            || rc.contains("METADATA")
                            || rc.contains("COMPATIBLE"))) {
                return LabRuntimeConfigReasonCodes.SNAPSHOT_CONFIG_MISMATCH;
            }
            return LabRuntimeConfigReasonCodes.FEATURE_REQUIRES_REINDEX;
        }
        return LabRuntimeConfigReasonCodes.SNAPSHOT_CONFIG_MISMATCH;
    }

    private static String mapRuntimeBlockToConfigCode(String runtimeBlock) {
        if (LabRuntimeConfigReasonCodes.USE_ADVISOR_REQUIRES_RETRIEVAL.equals(runtimeBlock)
                || LabRuntimeConfigReasonCodes.STRUCTURED_SEARCH_WITH_RETRIEVAL_NOT_SUPPORTED.equals(runtimeBlock)) {
            return LabRuntimeConfigReasonCodes.INCOMPATIBLE_FEATURES;
        }
        return LabRuntimeConfigReasonCodes.INVALID_RUNTIME_CONFIG;
    }

    private RagConfig effectiveRagConfig(RagExperimentalPresetCode code) {
        ObjectNode terminal = ExperimentalPresetCanonicalCatalog.effectiveTerminalRuntimeJson(code);
        RagConfig base =
                RagConfig.fromFeatureConfiguration(
                        ragFeatureConfiguration, 10, 0.7, null, null, null, "SIMPLE");
        return RagConfig.applyJsonOverrides(base, terminal);
    }

    private static Optional<String> runtimeBlockReason(RagConfig rag) {
        if (rag.useAdvisor() && !rag.useRetrieval()) {
            return Optional.of(LabRuntimeConfigReasonCodes.USE_ADVISOR_REQUIRES_RETRIEVAL);
        }
        if (rag.useRetrieval() && rag.materializationStrategy().name().equals("STRUCTURED_SEARCH")) {
            return Optional.of(LabRuntimeConfigReasonCodes.STRUCTURED_SEARCH_WITH_RETRIEVAL_NOT_SUPPORTED);
        }
        return Optional.empty();
    }

    private static RagExperimentalPresetCode parsePresetCode(String raw) {
        if (raw == null || raw.isBlank()) {
            fail(HttpStatus.BAD_REQUEST, LabRuntimeConfigReasonCodes.UNSUPPORTED_PRESET);
        }
        try {
            return RagExperimentalPresetCode.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            fail(HttpStatus.BAD_REQUEST, LabRuntimeConfigReasonCodes.UNSUPPORTED_PRESET);
            throw ex;
        }
    }

    private void assertEmbeddingDimension(String embeddingModelId) {
        try {
            embeddingSpaceGuard.assertFitsPhysicalVectorColumn(embeddingModelId);
        } catch (ResponseStatusException ex) {
            if (ex.getReason() != null && ex.getReason().contains(LabRuntimeConfigReasonCodes.EMBEDDING_DIMENSION_MISMATCH)) {
                fail(HttpStatus.BAD_REQUEST, LabRuntimeConfigReasonCodes.EMBEDDING_DIMENSION_MISMATCH);
            }
            throw ex;
        }
    }

    private static List<String> resolveEmbeddingModelIds(StartBenchmarkRunRequest request) {
        if (request.embeddingModelIds() != null && !request.embeddingModelIds().isEmpty()) {
            return request.embeddingModelIds().stream()
                    .filter(s -> s != null && !s.isBlank())
                    .map(String::trim)
                    .toList();
        }
        if (request.embeddingModelId() != null && !request.embeddingModelId().isBlank()) {
            return List.of(request.embeddingModelId().trim());
        }
        return List.of();
    }

    private static String resolveEmbeddingModelId(StartBenchmarkRunRequest request) {
        List<String> ids = resolveEmbeddingModelIds(request);
        return ids.isEmpty() ? null : ids.getFirst();
    }

    private static LabBenchmarkConfigPreflightResult okSummary(
            List<String> presetCodes,
            String embeddingModelId,
            boolean autoReindex,
            boolean strictIndexCheck,
            Map<String, Object> details) {
        return new LabBenchmarkConfigPreflightResult(
                true,
                "OK",
                presetCodes,
                embeddingModelId,
                autoReindex,
                strictIndexCheck,
                details);
    }

    private static void fail(HttpStatus status, String code) {
        throw new ResponseStatusException(status, code);
    }
}
