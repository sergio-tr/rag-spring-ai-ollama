package com.uniovi.rag.service.evaluation.preset;

import com.uniovi.rag.application.service.knowledge.KnowledgeSnapshotService;
import com.uniovi.rag.application.service.evaluation.BenchmarkResultRowKeys;
import com.uniovi.rag.application.service.evaluation.TypedBenchmarkDataset;
import com.uniovi.rag.application.service.runtime.config.IndexCompatibilityResult;
import com.uniovi.rag.application.service.runtime.config.IndexSnapshotCapabilities;
import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.configuration.RagImplementationProperties;
import com.uniovi.rag.domain.evaluation.BenchmarkItemOutcome;
import com.uniovi.rag.domain.evaluation.snapshot.EmbeddingExperimentalSnapshot;
import com.uniovi.rag.domain.evaluation.snapshot.LlmExperimentalSnapshot;
import com.uniovi.rag.domain.evaluation.workbook.DifficultyLevel;
import com.uniovi.rag.domain.evaluation.workbook.RagExperimentalPresetCode;
import com.uniovi.rag.domain.evaluation.workbook.RagPresetDefinition;
import com.uniovi.rag.domain.evaluation.workbook.RagPresetQuestion;
import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.infrastructure.persistence.EvaluationRunRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeIndexSnapshotEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.ProjectEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationRunEntity;
import com.uniovi.rag.service.evaluation.EvaluationService;
import com.uniovi.rag.service.evaluation.baseline.ExperimentalSnapshotFactory;
import com.uniovi.rag.application.exception.RagServiceException;
import com.uniovi.rag.domain.exception.ErrorCode;
import com.uniovi.rag.service.async.LabJobCancelledException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

/**
 * Executes workbook {@code rag_preset_catalog_P0_P14} rows as sequential preset batches over shared typed questions,
 * wiring terminal runtime JSON into legacy HTTP evaluation via {@link BenchmarkPresetEvaluationContext}.
 */
@Service
public class TypedRagPresetBenchmarkOrchestrator {

    private static final String JSON_KEY_CORRECT_ANSWER = "correct_answer";
    private static final String JSON_KEY_GENERATED_ANSWER = "generated_answer";
    private static final String JSON_KEY_METRICS_PAYLOAD = "metrics_payload";

    private final EvaluationService evaluationService;
    private final EvaluationRunRepository evaluationRunRepository;
    private final ExperimentalSnapshotFactory experimentalSnapshotFactory;
    private final KnowledgeSnapshotService knowledgeSnapshotService;

    public TypedRagPresetBenchmarkOrchestrator(
            EvaluationService evaluationService,
            EvaluationRunRepository evaluationRunRepository,
            ExperimentalSnapshotFactory experimentalSnapshotFactory,
            KnowledgeSnapshotService knowledgeSnapshotService) {
        this.evaluationService = evaluationService;
        this.evaluationRunRepository = evaluationRunRepository;
        this.experimentalSnapshotFactory = experimentalSnapshotFactory;
        this.knowledgeSnapshotService = knowledgeSnapshotService;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> runPresetBenchmark(
            UUID evaluationRunId,
            TypedBenchmarkDataset.RagPresetQuestions rag,
            RagFeatureConfiguration applicationFeatureDefaults,
            RagImplementationProperties implementationProperties,
            Set<RagExperimentalPresetCode> requestedPresets,
            BiConsumer<Integer, Integer> itemProgress) {
        return runPresetBenchmark(
                evaluationRunId,
                rag,
                applicationFeatureDefaults,
                implementationProperties,
                requestedPresets,
                itemProgress,
                null);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> runPresetBenchmark(
            UUID evaluationRunId,
            TypedBenchmarkDataset.RagPresetQuestions rag,
            RagFeatureConfiguration applicationFeatureDefaults,
            RagImplementationProperties implementationProperties,
            Set<RagExperimentalPresetCode> requestedPresets,
            BiConsumer<Integer, Integer> itemProgress,
            Runnable cancellationCheck) {
        RagImplementationProperties impl =
                implementationProperties != null ? implementationProperties : new RagImplementationProperties();
        RagFeatureConfiguration base =
                applicationFeatureDefaults != null ? applicationFeatureDefaults : new RagFeatureConfiguration();

        EvaluationRunEntity run =
                evaluationRunId != null ? evaluationRunRepository.findById(evaluationRunId).orElse(null) : null;
        LlmExperimentalSnapshot llmSnap = experimentalSnapshotFactory.buildLlmSnapshot(run);
        EmbeddingExperimentalSnapshot embSnap = experimentalSnapshotFactory.buildEmbeddingSnapshot(run);

        List<RagPresetQuestion> questions = rag.questions();
        List<RagPresetDefinition> catalog = rag.presetCatalog();

        if (catalog == null || catalog.isEmpty()) {
            Map<String, Object> single =
                    evaluationService.evaluateWithConfigurationForRagPresetQuestions(
                            base, impl, questions, itemProgress);
            enrichRows((List<Map<String, Object>>) single.get("results"), null, null, llmSnap.model(), embSnap.model());
            return single;
        }

        List<RagPresetDefinition> sorted = new ArrayList<>(catalog);
        if (requestedPresets != null && !requestedPresets.isEmpty()) {
            sorted.removeIf(d -> !requestedPresets.contains(d.presetId()));
        }
        sorted.sort(Comparator.comparing(d -> d.presetId().ordinal()));

        int totalOps = sorted.size() * Math.max(1, questions.size());
        AtomicInteger progressed = new AtomicInteger(0);
        Runnable bump = () -> {
            if (cancellationCheck != null) {
                cancellationCheck.run();
            }
            if (itemProgress != null) {
                itemProgress.accept(progressed.incrementAndGet(), totalOps);
            }
        };

        List<Map<String, Object>> allRows = new ArrayList<>();
        Map<String, Object> lastConfigurationMap = new LinkedHashMap<>();
        boolean cancelled = false;
        String cancelReason = null;

        for (RagPresetDefinition def : sorted) {
            try {
                if (cancellationCheck != null) {
                    cancellationCheck.run();
                }
            } catch (LabJobCancelledException ex) {
                cancelled = true;
                cancelReason = ex.getMessage();
                break;
            }
            RagExperimentalPresetCode preset = def.presetId();
            Optional<String> blocked = ExperimentalPresetBenchmarkGate.blockReason(preset);
            if (blocked.isPresent()) {
                for (RagPresetQuestion q : questions) {
                    bump.run();
                    allRows.add(notSupportedRow(q, def.name(), preset, blocked.get(), llmSnap.model(), embSnap.model()));
                }
                continue;
            }

            PreflightIndexCompatibility gate = checkPresetIndexCompatibility(run, preset);
            if (!gate.compatible()) {
                for (RagPresetQuestion q : questions) {
                    bump.run();
                    allRows.add(skippedRow(
                            q,
                            def.name(),
                            preset,
                            gate,
                            llmSnap.model(),
                            embSnap.model()));
                }
                continue;
            }

            RagPresetExperimentalOverlay.Overlay overlay = RagPresetExperimentalOverlay.build(base, preset);
            lastConfigurationMap = new LinkedHashMap<>();
            overlay.features().getConfiguration().forEach(lastConfigurationMap::put);

            try (AutoCloseable ignored =
                    BenchmarkPresetEvaluationContext.open(overlay.terminalRuntimeJson())) {
                Map<String, Object> batch =
                        evaluationService.evaluateWithConfigurationForRagPresetQuestions(
                                overlay.features(),
                                impl,
                                questions,
                                itemProgress == null ? null : (i, n) -> bump.run());
                List<Map<String, Object>> rows = (List<Map<String, Object>>) batch.get("results");
                if (rows != null) {
                    enrichRows(rows, def.name(), preset, llmSnap.model(), embSnap.model());
                    allRows.addAll(rows);
                }
            } catch (Exception ex) {
                for (RagPresetQuestion q : questions) {
                    bump.run();
                    if (ex instanceof RagServiceException rse
                            && rse.getErrorCode() == ErrorCode.UNSUPPORTED_RUNTIME_CONFIGURATION) {
                        allRows.add(notSupportedRow(q, def.name(), preset, rse.getErrorCode().name(), llmSnap.model(), embSnap.model()));
                    } else {
                        allRows.add(failedRow(q, def.name(), preset, ex, llmSnap.model(), embSnap.model()));
                    }
                }
            }
            if (cancelled) {
                break;
            }
        }

        Map<String, Object> out = new HashMap<>();
        out.put("configuration", Map.of("preset_benchmark", true, "last_preset_feature_flags", lastConfigurationMap));
        out.put("results", allRows);
        Map<String, Object> summary = new LinkedHashMap<>(evaluationService.summarizeJudgeResults(allRows));
        if (cancelled) {
            summary.put("cancelled", true);
            if (cancelReason != null && !cancelReason.isBlank()) {
                summary.put("cancel_reason", cancelReason);
            }
            summary.put("completed_items", allRows.size());
            summary.put("total_items", totalOps);
        }
        out.put("evaluation_summary", summary);
        return out;
    }

    private PreflightIndexCompatibility checkPresetIndexCompatibility(
            EvaluationRunEntity run,
            RagExperimentalPresetCode preset) {
        ExperimentalPresetCanonicalCatalog.IndexRequirements req =
                preset != null ? ExperimentalPresetCanonicalCatalog.effectiveIndexRequirements(preset) : null;
        // Presets that require no index must never be blocked by snapshot absence.
        if (req == null || req.requiredMaterialization() == null
                || req.requiredMaterialization() == ExperimentalPresetCanonicalCatalog.RequiredMaterialization.NONE) {
            return PreflightIndexCompatibility.compatible(req, null, null, null);
        }

        KnowledgeIndexSnapshotEntity snap = resolveSnapshot(run);
        boolean hasActive = snap != null && snap.getId() != null;
        Map<String, Object> profile = hasActive && snap.getIndexProfileJsonb() != null ? snap.getIndexProfileJsonb() : Map.of();
        String profileHash = hasActive ? snap.getIndexProfileHash() : null;
        IndexSnapshotCapabilities caps = IndexSnapshotCapabilities.fromIndexProfile(profile);
        IndexCompatibilityResult idx = IndexCompatibilityResult.check(req, hasActive, caps);
        return new PreflightIndexCompatibility(
                idx.compatible(),
                idx.requiresReindex(),
                idx.status(),
                idx.reasonCode(),
                idx.message(),
                req,
                caps,
                hasActive ? snap.getId() : null,
                profileHash);
    }

    private KnowledgeIndexSnapshotEntity resolveSnapshot(EvaluationRunEntity run) {
        if (run == null) {
            return null;
        }
        KnowledgeIndexSnapshotEntity explicit = run.getIndexSnapshot();
        if (explicit != null && explicit.getId() != null) {
            return explicit;
        }
        ProjectEntity p = run.getProject();
        if (p == null || p.getId() == null) {
            return null;
        }
        return knowledgeSnapshotService.findActiveProjectSnapshot(p.getId()).orElse(null);
    }

    private static void enrichRows(
            List<Map<String, Object>> rows,
            String presetLabel,
            RagExperimentalPresetCode preset,
            String llmModelId,
            String embeddingModelId) {
        if (rows == null) {
            return;
        }
        String presetStr = preset != null ? preset.name() : null;
        for (Map<String, Object> row : rows) {
            if (presetStr != null) {
                row.put(BenchmarkResultRowKeys.PRESET_CODE, presetStr);
            }
            row.put(BenchmarkResultRowKeys.PRESET_LABEL, presetLabel);
            row.put(BenchmarkResultRowKeys.LLM_MODEL_ID, llmModelId);
            row.put(BenchmarkResultRowKeys.EMBEDDING_MODEL_ID, embeddingModelId);
        }
    }

    private static Map<String, Object> notSupportedRow(
            RagPresetQuestion q,
            String presetLabel,
            RagExperimentalPresetCode preset,
            String errorCode,
            String llmModelId,
            String embeddingModelId) {
        Map<String, Object> row = baseRow(q, presetLabel, preset, llmModelId, embeddingModelId);
        row.put(JSON_KEY_GENERATED_ANSWER, "");
        row.put("llm_evaluation", "");
        row.put(BenchmarkResultRowKeys.ITEM_OUTCOME, BenchmarkItemOutcome.NOT_SUPPORTED.name());
        row.put(BenchmarkResultRowKeys.ERROR_CODE, errorCode);
        row.put(BenchmarkResultRowKeys.REASON, errorCode);
        row.put(BenchmarkResultRowKeys.LATENCY_MS, 0L);
        return row;
    }

    private static Map<String, Object> skippedRow(
            RagPresetQuestion q,
            String presetLabel,
            RagExperimentalPresetCode preset,
            PreflightIndexCompatibility gate,
            String llmModelId,
            String embeddingModelId) {
        Map<String, Object> row = baseRow(q, presetLabel, preset, llmModelId, embeddingModelId);
        row.put(JSON_KEY_GENERATED_ANSWER, "");
        row.put("llm_evaluation", "");
        row.put(BenchmarkResultRowKeys.ITEM_OUTCOME, BenchmarkItemOutcome.SKIPPED.name());
        String code = gate != null && gate.reasonCode() != null ? gate.reasonCode() : "INDEX_REQUIRES_REINDEX";
        row.put(BenchmarkResultRowKeys.ERROR_CODE, code);
        row.put(BenchmarkResultRowKeys.REASON, gate != null && gate.message() != null ? gate.message() : code);
        row.put(BenchmarkResultRowKeys.LATENCY_MS, 0L);
        row.put(JSON_KEY_METRICS_PAYLOAD, buildIndexGateMetricsPayload(presetLabel, preset, gate));
        return row;
    }

    private static Map<String, Object> failedRow(
            RagPresetQuestion q,
            String presetLabel,
            RagExperimentalPresetCode preset,
            Exception ex,
            String llmModelId,
            String embeddingModelId) {
        Map<String, Object> row = baseRow(q, presetLabel, preset, llmModelId, embeddingModelId);
        row.put(JSON_KEY_GENERATED_ANSWER, "");
        row.put("llm_evaluation", "");
        row.put(BenchmarkResultRowKeys.ITEM_OUTCOME, BenchmarkItemOutcome.FAILED.name());
        row.put(BenchmarkResultRowKeys.ERROR_CODE, "PRESET_BATCH_EXCEPTION");
        row.put(BenchmarkResultRowKeys.REASON, "PRESET_BATCH_EXCEPTION");
        row.put(
                "error",
                ex.getMessage() != null && !ex.getMessage().isBlank()
                        ? ex.getMessage()
                        : ex.getClass().getSimpleName());
        row.put(BenchmarkResultRowKeys.LATENCY_MS, 0L);
        return row;
    }

    private static Map<String, Object> baseRow(
            RagPresetQuestion q,
            String presetLabel,
            RagExperimentalPresetCode preset,
            String llmModelId,
            String embeddingModelId) {
        Map<String, Object> row = new HashMap<>();
        row.put("question", q.question());
        row.put(JSON_KEY_CORRECT_ANSWER, q.expectedAnswer() != null ? q.expectedAnswer() : "");
        row.put(BenchmarkResultRowKeys.DATASET_QUESTION_ID, q.id());
        row.put(BenchmarkResultRowKeys.DIFFICULTY, q.difficulty().map(DifficultyLevel::name).orElse(null));
        row.put("query_type", q.queryType().map(QueryType::name).orElse(null));
        row.put(BenchmarkResultRowKeys.PRESET_CODE, preset.name());
        row.put(BenchmarkResultRowKeys.PRESET_LABEL, presetLabel);
        row.put(BenchmarkResultRowKeys.LLM_MODEL_ID, llmModelId);
        row.put(BenchmarkResultRowKeys.EMBEDDING_MODEL_ID, embeddingModelId);
        row.put("tool_used", null);
        row.put("used_tool", false);
        return row;
    }

    private static Map<String, Object> buildIndexGateMetricsPayload(
            String presetLabel,
            RagExperimentalPresetCode preset,
            PreflightIndexCompatibility gate) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put(BenchmarkResultRowKeys.PRESET_CODE, preset != null ? preset.name() : null);
        metrics.put(BenchmarkResultRowKeys.PRESET_LABEL, presetLabel);
        if (gate == null) {
            return metrics;
        }
        metrics.put("indexCompatibilityStatus", gate.status());
        metrics.put("requiresReindex", gate.requiresReindex());
        metrics.put("indexSnapshotId", gate.indexSnapshotId() != null ? gate.indexSnapshotId().toString() : null);
        metrics.put("indexProfileHash", gate.indexProfileHash());
        metrics.put("presetIndexRequirements", indexRequirementsMap(gate.presetIndexRequirements()));
        metrics.put("activeSnapshotCapabilities", snapshotCapsMap(gate.activeSnapshotCapabilities()));
        return metrics;
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

    private record PreflightIndexCompatibility(
            boolean compatible,
            boolean requiresReindex,
            String status,
            String reasonCode,
            String message,
            ExperimentalPresetCanonicalCatalog.IndexRequirements presetIndexRequirements,
            IndexSnapshotCapabilities activeSnapshotCapabilities,
            UUID indexSnapshotId,
            String indexProfileHash) {
        static PreflightIndexCompatibility compatible(
                ExperimentalPresetCanonicalCatalog.IndexRequirements req,
                IndexSnapshotCapabilities caps,
                UUID indexSnapshotId,
                String indexProfileHash) {
            return new PreflightIndexCompatibility(true, false, "COMPATIBLE", null, null, req, caps, indexSnapshotId, indexProfileHash);
        }
    }
}
