package com.uniovi.rag.application.service.evaluation.config;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.uniovi.rag.application.service.evaluation.StartBenchmarkRunRequest;
import com.uniovi.rag.application.service.evaluation.preset.ExperimentalPresetBenchmarkGate;
import com.uniovi.rag.application.service.evaluation.preset.ExperimentalPresetCanonicalCatalog;
import com.uniovi.rag.application.service.knowledge.KnowledgeSnapshotService;
import com.uniovi.rag.application.service.runtime.config.IndexCompatibilityResult;
import com.uniovi.rag.application.service.runtime.config.IndexSnapshotCapabilities;
import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.domain.evaluation.BenchmarkKind;
import com.uniovi.rag.domain.evaluation.workbook.RagExperimentalPresetCode;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeIndexSnapshotEntity;
import com.uniovi.rag.infrastructure.vector.EmbeddingSpaceGuard;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
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

    public LabBenchmarkConfigPreflightService(
            RagFeatureConfiguration ragFeatureConfiguration,
            KnowledgeSnapshotService knowledgeSnapshotService,
            EmbeddingSpaceGuard embeddingSpaceGuard) {
        this.ragFeatureConfiguration = ragFeatureConfiguration;
        this.knowledgeSnapshotService = knowledgeSnapshotService;
        this.embeddingSpaceGuard = embeddingSpaceGuard;
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
            case EMBEDDING_RETRIEVAL -> validateEmbedding(request);
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
            validateIndexForPresets(userId, request.corpusId(), presets, details);
        }

        String embeddingModelId = resolveEmbeddingModelId(request);
        if (embeddingModelId != null && presets.stream().anyMatch(ExperimentalPresetCanonicalCatalog::embeddingRequired)) {
            assertEmbeddingDimension(embeddingModelId);
            details.put("embeddingModelId", embeddingModelId);
        }

        return okSummary(
                presets.stream().map(Enum::name).toList(),
                embeddingModelId,
                request.autoReindexEffective(),
                strictIndexCheck,
                details);
    }

    private LabBenchmarkConfigPreflightResult validateEmbedding(StartBenchmarkRunRequest request) {
        String embeddingModelId = resolveEmbeddingModelId(request);
        if (embeddingModelId == null || embeddingModelId.isBlank()) {
            return okSummary(List.of(), null, request.autoReindexEffective(), false, Map.of());
        }
        assertEmbeddingDimension(embeddingModelId.trim());
        return okSummary(
                List.of(),
                embeddingModelId.trim(),
                request.autoReindexEffective(),
                false,
                Map.of("embeddingModelId", embeddingModelId.trim()));
    }

    private void validateIndexForPresets(
            UUID userId,
            UUID corpusId,
            List<RagExperimentalPresetCode> presets,
            Map<String, Object> details) {
        RagExperimentalPresetCode strictest =
                presets.stream().max(Comparator.comparingInt(Enum::ordinal)).orElseThrow();

        if (!ExperimentalPresetCanonicalCatalog.requiresSnapshotForExecution(strictest)) {
            return;
        }

        Optional<KnowledgeIndexSnapshotEntity> active = knowledgeSnapshotService.findActiveCorpusSnapshot(corpusId);
        boolean hasActive = active.isPresent();
        UUID activeId = active.map(KnowledgeIndexSnapshotEntity::getId).orElse(null);
        details.put("activeSnapshotId", activeId != null ? activeId.toString() : null);
        details.put("strictestPreset", strictest.name());

        IndexSnapshotCapabilities caps =
                active.map(KnowledgeIndexSnapshotEntity::getIndexProfileJsonb)
                        .map(IndexSnapshotCapabilities::fromIndexProfile)
                        .orElse(IndexSnapshotCapabilities.fromIndexProfile(Map.of()));

        ExperimentalPresetCanonicalCatalog.IndexRequirements req =
                ExperimentalPresetCanonicalCatalog.effectiveIndexRequirements(strictest);
        IndexCompatibilityResult idx = IndexCompatibilityResult.check(req, hasActive, caps);

        if (idx.compatible()) {
            return;
        }

        String mapped = mapIndexCompatibilityToConfigCode(idx);
        fail(HttpStatus.BAD_REQUEST, mapped);
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

    private static String resolveEmbeddingModelId(StartBenchmarkRunRequest request) {
        if (request.embeddingModelId() != null && !request.embeddingModelId().isBlank()) {
            return request.embeddingModelId().trim();
        }
        if (request.embeddingModelIds() != null && !request.embeddingModelIds().isEmpty()) {
            return request.embeddingModelIds().getFirst().trim();
        }
        return null;
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
