package com.uniovi.rag.service.evaluation.preset;

import com.uniovi.rag.application.service.knowledge.KnowledgeSnapshotService;
import com.uniovi.rag.application.service.knowledge.KnowledgePipelineOrchestrator;
import com.uniovi.rag.application.service.knowledge.LabIndexProfileOverrideFactory;
import com.uniovi.rag.application.service.knowledge.ProjectIndexProfileService;
import com.uniovi.rag.domain.knowledge.CorpusScope;
import com.uniovi.rag.application.service.evaluation.BenchmarkResultRowKeys;
import com.uniovi.rag.application.service.evaluation.TypedBenchmarkDataset;
import com.uniovi.rag.application.service.runtime.config.IndexCompatibilityResult;
import com.uniovi.rag.application.service.runtime.config.IndexSnapshotCapabilities;
import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.configuration.RagImplementationProperties;
import com.uniovi.rag.application.service.runtime.WorkflowNameInference;
import com.uniovi.rag.domain.runtime.RagConfig;
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
import com.uniovi.rag.service.evaluation.AbstractEvaluationService;
import com.uniovi.rag.service.evaluation.EvaluationService;
import com.uniovi.rag.service.evaluation.baseline.ExperimentalSnapshotFactory;
import com.uniovi.rag.application.exception.RagServiceException;
import com.uniovi.rag.domain.exception.ErrorCode;
import com.uniovi.rag.service.async.LabJobCancelledException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Executes workbook {@code rag_preset_catalog_P0_P14} rows as sequential preset batches over shared typed questions,
 * wiring terminal runtime JSON into legacy HTTP evaluation via {@link BenchmarkPresetEvaluationContext}.
 */
@Service
public class TypedRagPresetBenchmarkOrchestrator {

    private static final String JSON_KEY_CORRECT_ANSWER = "correct_answer";
    private static final String JSON_KEY_GENERATED_ANSWER = "generated_answer";
    private static final String JSON_KEY_METRICS_PAYLOAD = "metrics_payload";
    private static final String JSON_KEY_RUN_PLAN_VERSION = "runPlanVersion";
    private static final String JSON_KEY_RUN_PLAN = "runPlan";

    private final EvaluationService evaluationService;
    private final EvaluationRunRepository evaluationRunRepository;
    private final ExperimentalSnapshotFactory experimentalSnapshotFactory;
    private final KnowledgeSnapshotService knowledgeSnapshotService;
    private final LabPresetRunPlanService labPresetRunPlanService;
    private final KnowledgePipelineOrchestrator knowledgePipelineOrchestrator;
    private final ProjectIndexProfileService projectIndexProfileService;
    private final LabIndexProfileOverrideFactory labIndexProfileOverrideFactory;
    private final CorpusAvailabilityGate corpusAvailabilityGate;

    public TypedRagPresetBenchmarkOrchestrator(
            EvaluationService evaluationService,
            EvaluationRunRepository evaluationRunRepository,
            ExperimentalSnapshotFactory experimentalSnapshotFactory,
            KnowledgeSnapshotService knowledgeSnapshotService,
            LabPresetRunPlanService labPresetRunPlanService,
            KnowledgePipelineOrchestrator knowledgePipelineOrchestrator,
            ProjectIndexProfileService projectIndexProfileService,
            LabIndexProfileOverrideFactory labIndexProfileOverrideFactory,
            CorpusAvailabilityGate corpusAvailabilityGate) {
        this.evaluationService = evaluationService;
        this.evaluationRunRepository = evaluationRunRepository;
        this.experimentalSnapshotFactory = experimentalSnapshotFactory;
        this.knowledgeSnapshotService = knowledgeSnapshotService;
        this.labPresetRunPlanService = labPresetRunPlanService;
        this.knowledgePipelineOrchestrator = knowledgePipelineOrchestrator;
        this.projectIndexProfileService = projectIndexProfileService;
        this.labIndexProfileOverrideFactory = labIndexProfileOverrideFactory;
        this.corpusAvailabilityGate = corpusAvailabilityGate;
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
            enrichRows(
                    (List<Map<String, Object>>) single.get("results"),
                    null,
                    null,
                    llmSnap.model(),
                    embSnap.model(),
                    null,
                    null,
                    LabPresetRunPlanModels.STRATEGY_VERSION,
                    null,
                    base);
            @SuppressWarnings("unchecked")
            Map<String, Object> summary =
                    single.get("evaluation_summary") instanceof Map<?, ?>
                            ? new LinkedHashMap<>((Map<String, Object>) single.get("evaluation_summary"))
                            : new LinkedHashMap<>();
            summary.put("runPlan", labPresetRunPlanService.build(run, List.of()).toMap());
            single.put("evaluation_summary", summary);
            return single;
        }

        List<RagExperimentalPresetCode> codesForPlan;
        if (requestedPresets == null || requestedPresets.isEmpty()) {
            codesForPlan =
                    labPresetRunPlanService.sortDefinitionsOrder(
                            catalog.stream().map(RagPresetDefinition::presetId).toList());
        } else {
            codesForPlan = labPresetRunPlanService.sortDefinitionsOrder(new ArrayList<>(requestedPresets));
        }
        LabPresetRunPlanModels.LabPresetRunPlan runPlan = labPresetRunPlanService.build(run, codesForPlan);
        AutoReindexPolicy autoReindexPolicy = AutoReindexPolicy.fromRun(run);

        Map<RagExperimentalPresetCode, RagPresetDefinition> defByPreset =
                catalog.stream()
                        .collect(Collectors.toMap(RagPresetDefinition::presetId, d -> d, (a, b) -> a, LinkedHashMap::new));

        int totalOps = codesForPlan.size() * Math.max(1, questions.size());
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

        for (LabPresetRunGroupKey gk : orderedGroupKeys()) {
            LabPresetRunPlanModels.LabPresetRunGroup baseGroup = findGroup(runPlan, gk);
            if (baseGroup == null || baseGroup.presetCodes() == null || baseGroup.presetCodes().isEmpty()) {
                continue;
            }
            try {
                if (cancellationCheck != null) {
                    cancellationCheck.run();
                }
            } catch (LabJobCancelledException ex) {
                cancelled = true;
                cancelReason = ex.getMessage();
                break;
            }

            GroupExecution exec = GroupExecution.initial(gk);
            Instant groupStartedAt = Instant.now();
            exec = exec.withStartedAt(groupStartedAt).withReindexStatus("PENDING");
            runPlan = updateGroup(runPlan, gk, mergeGroupExecution(baseGroup, exec));
            persistRunPlanBestEffort(evaluationRunId, runPlan);

            if (gk == LabPresetRunGroupKey.NO_INDEX) {
                exec = exec.withReindexAction("NONE").withReindexStatus("SKIPPED");
            } else if (gk == LabPresetRunGroupKey.MULTI_TURN_UNSUPPORTED_IN_SINGLE_TURN) {
                exec = exec.withReindexAction("NONE").withReindexStatus("NOT_SUPPORTED");
                for (String codeStr : baseGroup.presetCodes()) {
                    Optional<RagExperimentalPresetCode> parsed = RagExperimentalPresetCode.tryParse(codeStr);
                    if (parsed.isEmpty()) {
                        continue;
                    }
                    RagExperimentalPresetCode preset = parsed.get();
                    RagPresetDefinition def = defByPreset.get(preset);
                    String label = def != null ? def.name() : preset.name();
                    Optional<String> blocked = ExperimentalPresetBenchmarkGate.blockReason(preset);
                    String errCode = blocked.orElse("MULTI_TURN_SINGLE_TURN_LAB_UNSUPPORTED");
                    for (RagPresetQuestion q : questions) {
                        bump.run();
                        allRows.add(
                                notSupportedRow(
                                        q,
                                        label,
                                        preset,
                                        errCode,
                                        llmSnap.model(),
                                        embSnap.model(),
                                        gk,
                                        runPlan.strategyVersion(),
                                        exec,
                                        base));
                    }
                }
                exec = exec.withCompletedAt(Instant.now());
                runPlan = updateGroup(runPlan, gk, mergeGroupExecution(baseGroup, exec));
                persistRunPlanBestEffort(evaluationRunId, runPlan);
                continue;
            }

            // Auto-reindex and snapshot selection for index-requiring groups.
            if (autoReindexPolicy.enabled() && gk != LabPresetRunGroupKey.NO_INDEX) {
                try {
                    exec = ensureGroupSnapshot(run, baseGroup, exec, autoReindexPolicy);
                    runPlan = updateGroup(runPlan, gk, mergeGroupExecution(baseGroup, exec));
                    persistRunPlanBestEffort(evaluationRunId, runPlan);
                } catch (RuntimeException reindexEx) {
                    exec =
                            exec.withReindexAction(exec.reindexAction() != null ? exec.reindexAction() : "BUILD_AND_ACTIVATE")
                                    .withReindexStatus("FAILED")
                                    .withErrorCode("AUTO_REINDEX_FAILED")
                                    .withErrorReason(
                                            reindexEx.getMessage() != null ? reindexEx.getMessage() : "AUTO_REINDEX_FAILED")
                                    .withCompletedAt(Instant.now());
                    runPlan = updateGroup(runPlan, gk, mergeGroupExecution(baseGroup, exec));
                    persistRunPlanBestEffort(evaluationRunId, runPlan);

                    // Mark all presets for this group as skipped (per question) with stable code.
                    for (String codeStr : baseGroup.presetCodes()) {
                        Optional<RagExperimentalPresetCode> parsed = RagExperimentalPresetCode.tryParse(codeStr);
                        if (parsed.isEmpty()) {
                            continue;
                        }
                        RagExperimentalPresetCode preset = parsed.get();
                        RagPresetDefinition def = defByPreset.get(preset);
                        String label = def != null ? def.name() : preset.name();
                        for (RagPresetQuestion q : questions) {
                            bump.run();
                            allRows.add(
                                    skippedRow(
                                            q,
                                            label,
                                            preset,
                                            PreflightIndexCompatibility.compatible(
                                                    ExperimentalPresetCanonicalCatalog.effectiveIndexRequirements(preset),
                                                    null,
                                                    exec.groupSnapshotId(),
                                                    exec.groupIndexProfileHash()),
                                            gk,
                                            llmSnap.model(),
                                            embSnap.model(),
                                            runPlan.strategyVersion(),
                                            exec,
                                            "AUTO_REINDEX_FAILED",
                                            "AUTO_REINDEX_FAILED",
                                            null,
                                            base));
                        }
                    }
                    if (autoReindexPolicy.failOnReindexFailure()) {
                        break;
                    }
                    continue;
                }
            } else if (gk != LabPresetRunGroupKey.NO_INDEX) {
                exec = exec.withReindexAction("NONE").withReindexStatus("DISABLED");
            }

            // Execute presets for the group.
            for (String codeStr : baseGroup.presetCodes()) {
                Optional<RagExperimentalPresetCode> parsed = RagExperimentalPresetCode.tryParse(codeStr);
                if (parsed.isEmpty()) {
                    continue;
                }
                RagExperimentalPresetCode preset = parsed.get();
                RagPresetDefinition def = defByPreset.get(preset);
                if (def == null) {
                    continue;
                }
                Optional<String> blocked = ExperimentalPresetBenchmarkGate.blockReason(preset);
                if (blocked.isPresent()) {
                    for (RagPresetQuestion q : questions) {
                        bump.run();
                        allRows.add(
                                notSupportedRow(
                                        q,
                                        def.name(),
                                        preset,
                                        blocked.get(),
                                        llmSnap.model(),
                                        embSnap.model(),
                                        gk,
                                        runPlan.strategyVersion(),
                                        exec,
                                        base));
                    }
                    continue;
                }

                PreflightIndexCompatibility gate = checkPresetIndexCompatibility(run, preset);
                if (!gate.compatible()) {
                    for (RagPresetQuestion q : questions) {
                        bump.run();
                        allRows.add(
                                skippedRow(
                                        q,
                                        def.name(),
                                        preset,
                                        gate,
                                        gk,
                                        llmSnap.model(),
                                        embSnap.model(),
                                        runPlan.strategyVersion(),
                                        exec,
                                        null,
                                        null,
                                        null,
                                        base));
                    }
                    continue;
                }

                if (ExperimentalPresetCanonicalCatalog.requiresSnapshotAssembledCorpusEvidence(preset)) {
                    UUID projectId = run != null && run.getProject() != null ? run.getProject().getId() : null;
                    UUID snapForCorpus = resolvePresetSnapshotId(exec, gate);
                    CorpusAvailabilityGate.Result corpusProbe =
                            corpusAvailabilityGate.evaluate(
                                    projectId, snapForCorpus != null ? List.of(snapForCorpus) : List.of());
                    if (!corpusProbe.satisfied()) {
                        Map<String, Object> corpusExtras =
                                corpusAvailabilityGate.probe(
                                        projectId, snapForCorpus != null ? List.of(snapForCorpus) : List.of());
                        for (RagPresetQuestion q : questions) {
                            bump.run();
                            allRows.add(
                                    skippedRow(
                                            q,
                                            def.name(),
                                            preset,
                                            gate,
                                            gk,
                                            llmSnap.model(),
                                            embSnap.model(),
                                            runPlan.strategyVersion(),
                                            exec,
                                            CorpusAvailabilityGate.REASON_CODE,
                                            CorpusAvailabilityGate.REASON_MESSAGE,
                                            corpusExtras,
                                            base));
                        }
                        continue;
                    }
                }

                RagPresetExperimentalOverlay.Overlay overlay = RagPresetExperimentalOverlay.build(base, preset);
                lastConfigurationMap = new LinkedHashMap<>();
                overlay.features().getConfiguration().forEach(lastConfigurationMap::put);

                UUID resolvedSnapId = resolvePresetSnapshotId(exec, gate);
                try (AutoCloseable ignored =
                        BenchmarkPresetEvaluationContext.openLab(
                                overlay.terminalRuntimeJson(),
                                evaluationRunId,
                                run != null && run.getProject() != null ? run.getProject().getId() : null,
                                resolvedSnapId != null ? List.of(resolvedSnapId) : List.of(),
                                gk != null ? gk.name() : null,
                                preset != null ? preset.name() : null,
                                resolvedSnapId != null)) {
                    Map<String, Object> batch =
                            evaluationService.evaluateWithConfigurationForRagPresetQuestions(
                                    overlay.features(),
                                    impl,
                                    questions,
                                    itemProgress == null ? null : (i, n) -> bump.run());
                    List<Map<String, Object>> rows = (List<Map<String, Object>>) batch.get("results");
                    if (rows != null) {
                        enrichRows(
                                rows,
                                def.name(),
                                preset,
                                llmSnap.model(),
                                embSnap.model(),
                                gate,
                                gk,
                                runPlan.strategyVersion(),
                                exec,
                                base);
                        allRows.addAll(rows);
                    }
                } catch (Exception ex) {
                    for (RagPresetQuestion q : questions) {
                        bump.run();
                        if (ex instanceof RagServiceException rse
                                && rse.getErrorCode() == ErrorCode.UNSUPPORTED_RUNTIME_CONFIGURATION) {
                            allRows.add(
                                    notSupportedRow(
                                            q,
                                            def.name(),
                                            preset,
                                            rse.getErrorCode().name(),
                                            llmSnap.model(),
                                            embSnap.model(),
                                            gk,
                                            runPlan.strategyVersion(),
                                            exec,
                                            base));
                        } else {
                            allRows.add(
                                    failedRow(
                                            q,
                                            def.name(),
                                            preset,
                                            ex,
                                            llmSnap.model(),
                                            embSnap.model(),
                                            gate,
                                            gk,
                                            runPlan.strategyVersion(),
                                            exec,
                                            base));
                        }
                    }
                }
            }

            exec = exec.withCompletedAt(Instant.now()).withReindexStatus(exec.reindexStatus() != null ? exec.reindexStatus() : "DONE");
            runPlan = updateGroup(runPlan, gk, mergeGroupExecution(baseGroup, exec));
            persistRunPlanBestEffort(evaluationRunId, runPlan);
        }

        Map<String, Object> out = new HashMap<>();
        out.put("configuration", Map.of("preset_benchmark", true, "last_preset_feature_flags", lastConfigurationMap));
        out.put("results", allRows);
        Map<String, Object> summary = new LinkedHashMap<>(evaluationService.summarizeJudgeResults(allRows));
        summary.put("runPlan", runPlan.toMap());
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

    private static UUID resolvePresetSnapshotId(GroupExecution exec, PreflightIndexCompatibility gate) {
        if (exec != null && exec.groupSnapshotId() != null) {
            return exec.groupSnapshotId();
        }
        return gate != null ? gate.indexSnapshotId() : null;
    }

    private static List<LabPresetRunGroupKey> orderedGroupKeys() {
        return List.of(
                LabPresetRunGroupKey.NO_INDEX,
                LabPresetRunGroupKey.DOCUMENT_LEVEL,
                LabPresetRunGroupKey.CHUNK_LEVEL,
                LabPresetRunGroupKey.CHUNK_LEVEL_METADATA,
                LabPresetRunGroupKey.HYBRID_METADATA,
                LabPresetRunGroupKey.MULTI_TURN_UNSUPPORTED_IN_SINGLE_TURN);
    }

    private static LabPresetRunPlanModels.LabPresetRunGroup findGroup(
            LabPresetRunPlanModels.LabPresetRunPlan plan, LabPresetRunGroupKey key) {
        if (plan == null || key == null || plan.groups() == null) {
            return null;
        }
        for (LabPresetRunPlanModels.LabPresetRunGroup g : plan.groups()) {
            if (g != null && key == g.groupKey()) {
                return g;
            }
        }
        return null;
    }

    private static LabPresetRunPlanModels.LabPresetRunPlan updateGroup(
            LabPresetRunPlanModels.LabPresetRunPlan plan,
            LabPresetRunGroupKey key,
            Function<LabPresetRunPlanModels.LabPresetRunGroup, LabPresetRunPlanModels.LabPresetRunGroup> updater) {
        if (plan == null || key == null || updater == null || plan.groups() == null) {
            return plan;
        }
        List<LabPresetRunPlanModels.LabPresetRunGroup> next = new ArrayList<>(plan.groups().size());
        for (LabPresetRunPlanModels.LabPresetRunGroup g : plan.groups()) {
            if (g != null && key == g.groupKey()) {
                next.add(updater.apply(g));
            } else {
                next.add(g);
            }
        }
        return new LabPresetRunPlanModels.LabPresetRunPlan(
                next,
                plan.items(),
                plan.requestedPresetCodes(),
                plan.executablePresetCodes(),
                plan.skippedPresetCodes(),
                plan.resolvedSnapshotId(),
                plan.resolvedIndexProfileHash(),
                plan.hasActiveSnapshot(),
                plan.strategyVersion(),
                plan.createdAt());
    }

    private Function<LabPresetRunPlanModels.LabPresetRunGroup, LabPresetRunPlanModels.LabPresetRunGroup> mergeGroupExecution(
            LabPresetRunPlanModels.LabPresetRunGroup base,
            GroupExecution exec) {
        return g ->
                new LabPresetRunPlanModels.LabPresetRunGroup(
                        g.groupKey(),
                        g.presetCodes(),
                        g.aggregateIndexRequirements(),
                        g.activeSnapshotCapabilities(),
                        g.compatibleSnapshotId(),
                        g.compatible(),
                        g.requiresReindex(),
                        g.compatibilityStatus(),
                        g.reasonCode(),
                        g.reason(),
                        exec.reindexAction(),
                        exec.reindexStatus(),
                        exec.groupSnapshotId(),
                        exec.groupIndexProfileHash(),
                        exec.reindexEventId(),
                        exec.startedAt(),
                        exec.completedAt(),
                        exec.errorCode(),
                        exec.errorReason());
    }

    private void persistRunPlanBestEffort(UUID runId, LabPresetRunPlanModels.LabPresetRunPlan runPlan) {
        if (runId == null || runPlan == null) {
            return;
        }
        try {
            EvaluationRunEntity run = evaluationRunRepository.findById(runId).orElse(null);
            if (run == null) {
                return;
            }
            Map<String, Object> agg =
                    run.getAggregatesJson() != null ? new LinkedHashMap<>(run.getAggregatesJson()) : new LinkedHashMap<>();
            agg.put(JSON_KEY_RUN_PLAN, runPlan.toMap());
            run.setAggregatesJson(Map.copyOf(agg));
            evaluationRunRepository.save(run);
        } catch (Exception ignored) {
            // best-effort only
        }
    }

    private GroupExecution ensureGroupSnapshot(
            EvaluationRunEntity run,
            LabPresetRunPlanModels.LabPresetRunGroup group,
            GroupExecution exec,
            AutoReindexPolicy policy) {
        if (run == null || group == null || exec == null || !policy.enabled()) {
            return exec;
        }
        ProjectEntity project = run.getProject();
        if (project == null || project.getId() == null) {
            throw new IllegalStateException("AUTO_REINDEX_REQUIRES_PROJECT_CONTEXT");
        }
        UUID projectId = project.getId();

        ExperimentalPresetCanonicalCatalog.IndexRequirements req = groupRequirements(group);
        KnowledgeIndexSnapshotEntity snap = resolveSnapshot(run);
        boolean hasActive = snap != null && snap.getId() != null;
        Map<String, Object> profile = hasActive && snap.getIndexProfileJsonb() != null ? snap.getIndexProfileJsonb() : Map.of();
        String profileHash = hasActive ? snap.getIndexProfileHash() : null;
        IndexSnapshotCapabilities caps = IndexSnapshotCapabilities.fromIndexProfile(profile);
        IndexCompatibilityResult idx = IndexCompatibilityResult.check(req, hasActive, caps);

        if (idx.compatible()) {
            return exec.withReindexAction("REUSE_ACTIVE")
                    .withReindexStatus("REUSED")
                    .withGroupSnapshotId(hasActive ? snap.getId() : null)
                    .withGroupIndexProfileHash(profileHash);
        }

        if (!idx.requiresReindex() || !policy.allowActiveSnapshotMutation()) {
            return exec.withReindexAction("NONE")
                    .withReindexStatus("INCOMPATIBLE")
                    .withErrorCode(idx.reasonCode())
                    .withErrorReason(idx.message());
        }

        exec = exec.withReindexAction("BUILD_AND_ACTIVATE").withReindexStatus("BUILDING");
        // Build effective profile derived from requirements + group key; keep embedding/chunking from current profile.
        var current = projectIndexProfileService.ensureDefault(projectId);
        var effective = labIndexProfileOverrideFactory.buildEffectiveProfile(current, req, group.groupKey());

        UUID resolvedConfigSnapshotId =
                run.getResolvedConfigSnapshot() != null ? run.getResolvedConfigSnapshot().getId() : null;
        String resolvedConfigHash =
                run.getResolvedConfigSnapshot() != null ? run.getResolvedConfigSnapshot().getConfigHash() : null;
        UUID newSnapId =
                knowledgePipelineOrchestrator.rebuildScopeWithProfileOverride(
                        projectId,
                        CorpusScope.PROJECT_SHARED,
                        null,
                        resolvedConfigSnapshotId,
                        resolvedConfigHash,
                        effective);

        KnowledgeIndexSnapshotEntity after = knowledgeSnapshotService.findActiveProjectSnapshot(projectId).orElse(null);
        UUID activeId = after != null ? after.getId() : newSnapId;
        String afterHash = after != null ? after.getIndexProfileHash() : effective.profileHash();
        Map<String, Object> afterProfile = after != null && after.getIndexProfileJsonb() != null ? after.getIndexProfileJsonb() : effective.toSnapshotJsonb();
        IndexSnapshotCapabilities afterCaps = IndexSnapshotCapabilities.fromIndexProfile(afterProfile);
        IndexCompatibilityResult afterIdx = IndexCompatibilityResult.check(req, activeId != null, afterCaps);
        if (!afterIdx.compatible()) {
            throw new IllegalStateException(afterIdx.reasonCode() != null ? afterIdx.reasonCode() : "SNAPSHOT_BUILD_FAILED");
        }
        return exec.withReindexStatus("BUILT")
                .withGroupSnapshotId(activeId)
                .withGroupIndexProfileHash(afterHash);
    }

    private static ExperimentalPresetCanonicalCatalog.IndexRequirements groupRequirements(
            LabPresetRunPlanModels.LabPresetRunGroup group) {
        if (group == null || group.presetCodes() == null || group.presetCodes().isEmpty()) {
            return ExperimentalPresetCanonicalCatalog.IndexRequirements.none();
        }
        for (String code : group.presetCodes()) {
            Optional<RagExperimentalPresetCode> parsed = RagExperimentalPresetCode.tryParse(code);
            if (parsed.isPresent()) {
                return ExperimentalPresetCanonicalCatalog.effectiveIndexRequirements(parsed.get());
            }
        }
        return ExperimentalPresetCanonicalCatalog.IndexRequirements.none();
    }

    private record AutoReindexPolicy(
            boolean enabled,
            boolean allowActiveSnapshotMutation,
            boolean reuseCompatibleActiveSnapshot,
            boolean failOnReindexFailure) {
        @SuppressWarnings("unchecked")
        static AutoReindexPolicy fromRun(EvaluationRunEntity run) {
            if (run == null || run.getAggregatesJson() == null) {
                return new AutoReindexPolicy(false, false, true, true);
            }
            Object o = run.getAggregatesJson().get("autoReindexPolicy");
            if (!(o instanceof Map<?, ?> m)) {
                return new AutoReindexPolicy(false, false, true, true);
            }
            boolean enabled = Boolean.TRUE.equals(m.get("enabled"));
            boolean allowMut = Boolean.TRUE.equals(m.get("allowActiveSnapshotMutation"));
            boolean reuse = m.get("reuseCompatibleActiveSnapshot") == null || Boolean.TRUE.equals(m.get("reuseCompatibleActiveSnapshot"));
            boolean fail = m.get("failOnReindexFailure") == null || Boolean.TRUE.equals(m.get("failOnReindexFailure"));
            return new AutoReindexPolicy(enabled, allowMut, reuse, fail);
        }
    }

    private record GroupExecution(
            LabPresetRunGroupKey groupKey,
            String reindexAction,
            String reindexStatus,
            UUID groupSnapshotId,
            String groupIndexProfileHash,
            UUID reindexEventId,
            Instant startedAt,
            Instant completedAt,
            String errorCode,
            String errorReason) {
        static GroupExecution initial(LabPresetRunGroupKey key) {
            return new GroupExecution(key, null, null, null, null, null, null, null, null, null);
        }

        GroupExecution withReindexAction(String v) { return new GroupExecution(groupKey, v, reindexStatus, groupSnapshotId, groupIndexProfileHash, reindexEventId, startedAt, completedAt, errorCode, errorReason); }
        GroupExecution withReindexStatus(String v) { return new GroupExecution(groupKey, reindexAction, v, groupSnapshotId, groupIndexProfileHash, reindexEventId, startedAt, completedAt, errorCode, errorReason); }
        GroupExecution withGroupSnapshotId(UUID v) { return new GroupExecution(groupKey, reindexAction, reindexStatus, v, groupIndexProfileHash, reindexEventId, startedAt, completedAt, errorCode, errorReason); }
        GroupExecution withGroupIndexProfileHash(String v) { return new GroupExecution(groupKey, reindexAction, reindexStatus, groupSnapshotId, v, reindexEventId, startedAt, completedAt, errorCode, errorReason); }
        GroupExecution withReindexEventId(UUID v) { return new GroupExecution(groupKey, reindexAction, reindexStatus, groupSnapshotId, groupIndexProfileHash, v, startedAt, completedAt, errorCode, errorReason); }
        GroupExecution withStartedAt(Instant v) { return new GroupExecution(groupKey, reindexAction, reindexStatus, groupSnapshotId, groupIndexProfileHash, reindexEventId, v, completedAt, errorCode, errorReason); }
        GroupExecution withCompletedAt(Instant v) { return new GroupExecution(groupKey, reindexAction, reindexStatus, groupSnapshotId, groupIndexProfileHash, reindexEventId, startedAt, v, errorCode, errorReason); }
        GroupExecution withErrorCode(String v) { return new GroupExecution(groupKey, reindexAction, reindexStatus, groupSnapshotId, groupIndexProfileHash, reindexEventId, startedAt, completedAt, v, errorReason); }
        GroupExecution withErrorReason(String v) { return new GroupExecution(groupKey, reindexAction, reindexStatus, groupSnapshotId, groupIndexProfileHash, reindexEventId, startedAt, completedAt, errorCode, v); }
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
            String embeddingModelId,
            PreflightIndexCompatibility indexGate,
            LabPresetRunGroupKey groupKey,
            int runPlanVersion,
            GroupExecution exec,
            RagFeatureConfiguration applicationDefaults) {
        if (rows == null) {
            return;
        }
        String presetStr = preset != null ? preset.name() : null;
        LabPresetRunGroupKey gk = groupKey != null ? groupKey : LabPresetRunGroupKey.NO_INDEX;
        for (Map<String, Object> row : rows) {
            if (presetStr != null) {
                row.put(BenchmarkResultRowKeys.PRESET_CODE, presetStr);
            }
            row.put(BenchmarkResultRowKeys.PRESET_LABEL, presetLabel);
            row.put(BenchmarkResultRowKeys.LLM_MODEL_ID, llmModelId);
            row.put(BenchmarkResultRowKeys.EMBEDDING_MODEL_ID, embeddingModelId);
            Map<String, Object> metrics =
                    buildLabMetricsPayload(presetLabel, preset, gk, indexGate, runPlanVersion, exec, applicationDefaults);
            mergeEvaluationTelemetryIntoMetrics(row, metrics);
            applyExecutionEvidenceSemantics(metrics, preset);
            row.put(JSON_KEY_METRICS_PAYLOAD, metrics);
        }
    }

    private static void mergeEvaluationTelemetryIntoMetrics(Map<String, Object> row, Map<String, Object> metrics) {
        Object tel = row.remove(AbstractEvaluationService.EVALUATION_CHAT_TELEMETRY_ROW_KEY);
        if (!(tel instanceof Map<?, ?> map)) {
            return;
        }
        for (Map.Entry<?, ?> e : map.entrySet()) {
            if (e.getKey() != null) {
                metrics.put(e.getKey().toString(), e.getValue());
            }
        }
    }

    /** Corpus / snapshot semantics after telemetry merge (execution wins for workflowName, corpusChars, snapshots). */
    private static void applyExecutionEvidenceSemantics(Map<String, Object> metrics, RagExperimentalPresetCode preset) {
        boolean corpusReq = preset != null && ExperimentalPresetCanonicalCatalog.corpusRequired(preset);
        metrics.put("corpusRequired", corpusReq);
        Object cc = metrics.get("corpusChars");
        if (!(cc instanceof Number)) {
            cc = metrics.get("promptContextCharCount");
        }
        boolean avail = false;
        if (cc instanceof Number n && n.intValue() > 0) {
            avail = true;
        } else if (Boolean.TRUE.equals(metrics.get("closestEvidenceAvailable"))) {
            avail = true;
        } else if (Boolean.TRUE.equals(metrics.get("effectiveContextPresent"))) {
            avail = true;
        }
        metrics.put("corpusAvailable", avail);
        metrics.putIfAbsent("corpusTruncated", Boolean.FALSE);
        metrics.putIfAbsent("skippedReasonCode", "");
        metrics.putIfAbsent("skippedReason", "");
    }

    private static Map<String, Object> notSupportedRow(
            RagPresetQuestion q,
            String presetLabel,
            RagExperimentalPresetCode preset,
            String errorCode,
            String llmModelId,
            String embeddingModelId,
            LabPresetRunGroupKey groupKey,
            int runPlanVersion,
            GroupExecution exec,
            RagFeatureConfiguration applicationDefaults) {
        Map<String, Object> row = baseRow(q, presetLabel, preset, llmModelId, embeddingModelId);
        row.put(JSON_KEY_GENERATED_ANSWER, "");
        row.put("llm_evaluation", "");
        row.put(BenchmarkResultRowKeys.ITEM_OUTCOME, BenchmarkItemOutcome.NOT_SUPPORTED.name());
        row.put(BenchmarkResultRowKeys.ERROR_CODE, errorCode);
        row.put(BenchmarkResultRowKeys.REASON, errorCode);
        row.put(BenchmarkResultRowKeys.LATENCY_MS, 0L);
        LabPresetRunGroupKey gk =
                groupKey != null ? groupKey : LabPresetRunPlanService.groupKeyFor(preset);
        Map<String, Object> metrics =
                buildLabMetricsPayload(presetLabel, preset, gk, null, runPlanVersion, exec, applicationDefaults);
        metrics.put("skippedReasonCode", errorCode);
        metrics.put("skippedReason", errorCode);
        applyExecutionEvidenceSemantics(metrics, preset);
        row.put(JSON_KEY_METRICS_PAYLOAD, metrics);
        return row;
    }

    private static Map<String, Object> skippedRow(
            RagPresetQuestion q,
            String presetLabel,
            RagExperimentalPresetCode preset,
            PreflightIndexCompatibility gate,
            LabPresetRunGroupKey groupKey,
            String llmModelId,
            String embeddingModelId,
            int runPlanVersion,
            GroupExecution exec,
            String overrideErrorCode,
            String overrideReason,
            Map<String, Object> extraLabMetrics,
            RagFeatureConfiguration applicationDefaults) {
        Map<String, Object> row = baseRow(q, presetLabel, preset, llmModelId, embeddingModelId);
        row.put(JSON_KEY_GENERATED_ANSWER, "");
        row.put("llm_evaluation", "");
        row.put(BenchmarkResultRowKeys.ITEM_OUTCOME, BenchmarkItemOutcome.SKIPPED.name());
        String code =
                overrideErrorCode != null
                        ? overrideErrorCode
                        : gate != null && gate.reasonCode() != null ? gate.reasonCode() : "INDEX_REQUIRES_REINDEX";
        row.put(BenchmarkResultRowKeys.ERROR_CODE, code);
        String humanReason =
                overrideReason != null
                        ? overrideReason
                        : gate != null && gate.message() != null ? gate.message() : code;
        row.put(BenchmarkResultRowKeys.REASON, humanReason);
        row.put(BenchmarkResultRowKeys.LATENCY_MS, 0L);
        LabPresetRunGroupKey gk =
                groupKey != null ? groupKey : LabPresetRunPlanService.groupKeyFor(preset);
        Map<String, Object> metrics =
                buildLabMetricsPayload(presetLabel, preset, gk, gate, runPlanVersion, exec, applicationDefaults);
        if (extraLabMetrics != null && !extraLabMetrics.isEmpty()) {
            metrics.putAll(extraLabMetrics);
        }
        metrics.put("skippedReasonCode", code);
        metrics.put("skippedReason", humanReason);
        applyExecutionEvidenceSemantics(metrics, preset);
        row.put(JSON_KEY_METRICS_PAYLOAD, metrics);
        return row;
    }

    private static Map<String, Object> failedRow(
            RagPresetQuestion q,
            String presetLabel,
            RagExperimentalPresetCode preset,
            Exception ex,
            String llmModelId,
            String embeddingModelId,
            PreflightIndexCompatibility indexGate,
            LabPresetRunGroupKey groupKey,
            int runPlanVersion,
            GroupExecution exec,
            RagFeatureConfiguration applicationDefaults) {
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
        LabPresetRunGroupKey gk =
                groupKey != null ? groupKey : LabPresetRunPlanService.groupKeyFor(preset);
        Map<String, Object> metrics =
                buildLabMetricsPayload(presetLabel, preset, gk, indexGate, runPlanVersion, exec, applicationDefaults);
        metrics.put("skippedReasonCode", "");
        metrics.put("skippedReason", "");
        applyExecutionEvidenceSemantics(metrics, preset);
        row.put(JSON_KEY_METRICS_PAYLOAD, metrics);
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

    private static Map<String, Object> buildLabMetricsPayload(
            String presetLabel,
            RagExperimentalPresetCode preset,
            LabPresetRunGroupKey groupKey,
            PreflightIndexCompatibility gate,
            int runPlanVersion,
            GroupExecution exec,
            RagFeatureConfiguration applicationDefaults) {
        RagFeatureConfiguration defaults = applicationDefaults != null ? applicationDefaults : new RagFeatureConfiguration();
        RagConfig baseConfig =
                RagConfig.fromFeatureConfiguration(defaults, 10, 0.7, null, null, null, "SIMPLE");
        RagConfig effective =
                preset != null
                        ? RagConfig.applyJsonOverrides(
                                baseConfig, ExperimentalPresetCanonicalCatalog.effectiveTerminalRuntimeJson(preset))
                        : baseConfig;

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("presetCode", preset != null ? preset.name() : null);
        metrics.put(BenchmarkResultRowKeys.PRESET_CODE, preset != null ? preset.name() : null);
        metrics.put("presetLabel", presetLabel);
        metrics.put(BenchmarkResultRowKeys.PRESET_LABEL, presetLabel);
        metrics.put(
                "productPresetId",
                preset != null ? ExperimentalPresetCanonicalCatalog.productPresetId(preset).toString() : null);
        metrics.put("workflowName", WorkflowNameInference.inferWorkflowName(effective));
        metrics.put("activeFeatures", effective.toValueMap());
        metrics.put("useRetrieval", effective.useRetrieval());
        metrics.put("naiveFullCorpusInPromptEnabled", effective.naiveFullCorpusInPromptEnabled());
        metrics.put("materializationStrategy", effective.materializationStrategy().name());
        metrics.put("metadataEnabled", effective.metadataEnabled());
        metrics.put("expansionEnabled", effective.expansionEnabled());
        metrics.put("nerEnabled", effective.nerEnabled());
        metrics.put("reasoningEnabled", effective.reasoningEnabled());
        metrics.put("toolsEnabled", effective.toolsEnabled());
        metrics.put("functionCallingEnabled", effective.functionCallingEnabled());
        metrics.put("rankerEnabled", effective.rankerEnabled());
        metrics.put("postRetrievalEnabled", effective.postRetrievalEnabled());
        metrics.put("useAdvisor", effective.useAdvisor());
        metrics.put("adaptiveRoutingEnabled", effective.adaptiveRoutingEnabled());
        metrics.put("judgeEnabled", effective.judgeEnabled());
        metrics.put("clarificationEnabled", effective.clarificationEnabled());
        metrics.put("memoryEnabled", effective.memoryEnabled());
        metrics.put("corpusChars", null);
        metrics.put("corpusTruncated", Boolean.FALSE);
        metrics.put("corpusRequired", preset != null && ExperimentalPresetCanonicalCatalog.corpusRequired(preset));
        metrics.put("corpusAvailable", Boolean.FALSE);
        metrics.put("selectedSnapshotIds", selectedSnapshotIdsForExport(exec, gate));
        metrics.put("groundingPolicy", "");
        metrics.put("skippedReasonCode", "");
        metrics.put("skippedReason", "");

        metrics.put("groupKey", groupKey != null ? groupKey.name() : null);
        metrics.put(JSON_KEY_RUN_PLAN_VERSION, runPlanVersion);
        if (exec != null) {
            metrics.put(
                    "effectiveGroupSnapshotId",
                    exec.groupSnapshotId() != null ? exec.groupSnapshotId().toString() : null);
            metrics.put("groupSnapshotId", exec.groupSnapshotId() != null ? exec.groupSnapshotId().toString() : null);
            metrics.put("groupIndexProfileHash", exec.groupIndexProfileHash());
            metrics.put("reindexAction", exec.reindexAction());
            metrics.put("reindexStatus", exec.reindexStatus());
            metrics.put("forcedSnapshotSelection", exec.groupSnapshotId() != null);
            metrics.put("reindexEventId", exec.reindexEventId() != null ? exec.reindexEventId().toString() : null);
            metrics.put("reindexStartedAt", exec.startedAt() != null ? exec.startedAt().toString() : null);
            metrics.put("reindexCompletedAt", exec.completedAt() != null ? exec.completedAt().toString() : null);
            metrics.put("reindexErrorCode", exec.errorCode());
            metrics.put("reindexErrorReason", exec.errorReason());
        } else {
            metrics.put("forcedSnapshotSelection", Boolean.FALSE);
        }
        if (gate != null) {
            metrics.put("indexCompatibilityStatus", gate.status());
            metrics.put("requiresReindex", gate.requiresReindex());
            metrics.put("indexSnapshotId", gate.indexSnapshotId() != null ? gate.indexSnapshotId().toString() : null);
            metrics.put("indexProfileHash", gate.indexProfileHash());
            metrics.put("presetIndexRequirements", indexRequirementsMap(gate.presetIndexRequirements()));
            metrics.put("activeSnapshotCapabilities", snapshotCapsMap(gate.activeSnapshotCapabilities()));
        }
        return metrics;
    }

    /**
     * Snapshot ids aligned with {@link com.uniovi.rag.service.evaluation.BenchmarkPresetEvaluationContext} / execution
     * scope (group snapshot when present; otherwise gate snapshot).
     */
    private static List<String> selectedSnapshotIdsForExport(GroupExecution exec, PreflightIndexCompatibility gate) {
        if (exec != null && exec.groupSnapshotId() != null) {
            return List.of(exec.groupSnapshotId().toString());
        }
        if (gate != null && gate.indexSnapshotId() != null) {
            return List.of(gate.indexSnapshotId().toString());
        }
        return List.of();
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
