package com.uniovi.rag.application.service.evaluation;

import com.uniovi.rag.application.service.evaluation.preset.LabPresetRunGroupKey;
import com.uniovi.rag.domain.EvaluationRunStatus;
import com.uniovi.rag.domain.LabJobEventType;
import com.uniovi.rag.infrastructure.persistence.EvaluationCampaignRepository;
import com.uniovi.rag.infrastructure.persistence.EvaluationRunRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationCampaignEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationRunEntity;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Emits canonical ITEM_* / RUN_* / CAMPAIGN_* SSE events with global and per-run counters.
 */
@Service
public class LabJobProgressTracker {

    private static final String META_JOB_PROGRESS = "jobProgress";
    private static final String META_CAMPAIGN_STARTED = "campaignStartedEmitted";

    private final LabJobEventService labJobEventService;
    private final EvaluationRunRepository evaluationRunRepository;
    private final EvaluationCampaignRepository evaluationCampaignRepository;
    private final ObjectProvider<LabJobProgressTracker> self;

    public LabJobProgressTracker(
            LabJobEventService labJobEventService,
            EvaluationRunRepository evaluationRunRepository,
            EvaluationCampaignRepository evaluationCampaignRepository,
            ObjectProvider<LabJobProgressTracker> self) {
        this.labJobEventService = labJobEventService;
        this.evaluationRunRepository = evaluationRunRepository;
        this.evaluationCampaignRepository = evaluationCampaignRepository;
        this.self = self;
    }

    @Transactional
    public void emitRunStarted(
            UUID taskId,
            UUID runId,
            int runTotalItems,
            Integer globalTotalItems,
            String modelId,
            String presetCode) {
        Integer resolvedGlobal = globalTotalItems;
        EvaluationRunEntity runEntity = runId != null ? evaluationRunRepository.findById(runId).orElse(null) : null;
        if (resolvedGlobal == null && runEntity != null && runTotalItems > 0) {
            UUID campaignId = evaluationRunRepository.findCampaignIdByRunId(runId).orElse(null);
            if (campaignId != null) {
                int axisCount = evaluationRunRepository.findByCampaign_IdOrderByCreatedAtAsc(campaignId).size();
                resolvedGlobal = axisCount * runTotalItems;
                storeGlobalTotalOnCampaign(campaignId, resolvedGlobal);
            }
        }
        RunContext ctx = resolveContext(runId, runTotalItems, resolvedGlobal, modelId, presetCode);
        maybeEmitCampaignStarted(taskId, ctx);
        labJobEventService.record(new LabJobEventRequest(
                taskId,
                LabJobEventType.RUN_STARTED,
                "Run started",
                Map.of("runTotalItems", runTotalItems),
                ctx.campaignId(),
                runId,
                null,
                0,
                ctx.globalTotalItems(),
                0,
                runTotalItems,
                ctx.modelId(),
                ctx.presetCode()));
    }

    public BiConsumer<Integer, Integer> itemProgressCallback(
            UUID taskId,
            UUID runId,
            int runTotalItems,
            Integer globalTotalItems,
            String modelId,
            String presetCode,
            Runnable beforeEachItem) {
        RunContext ctx = resolveContext(runId, runTotalItems, globalTotalItems, modelId, presetCode);
        return (index, total) -> {
            if (beforeEachItem != null) {
                beforeEachItem.run();
            }
            self.getObject().onItemFinished(taskId, ctx, "item-" + index, index, total);
        };
    }

    @Transactional
    public void onItemFinished(UUID taskId, RunContext ctx, String itemId, int index, int runTotal) {
        emitItemStarted(taskId, ctx, itemId, index, runTotal);
        emitItemCompleted(taskId, ctx, itemId, index, runTotal);
    }

    void emitItemStarted(UUID taskId, RunContext ctx, String itemId, int index, int runTotal) {
        int runCompleted = Math.max(0, index - 1);
        GlobalCounters global = readGlobalCounters(ctx);
        labJobEventService.record(new LabJobEventRequest(
                taskId,
                LabJobEventType.ITEM_STARTED,
                "Item " + index + "/" + runTotal + " started",
                Map.of("itemIndex", index, "runTotalItems", runTotal),
                ctx.campaignId(),
                ctx.runId(),
                itemId,
                global.completed(),
                global.total(),
                runCompleted,
                runTotal,
                ctx.modelId(),
                ctx.presetCode()));
    }

    void emitItemCompleted(UUID taskId, RunContext ctx, String itemId, int index, int runTotal) {
        int runCompleted = index;
        GlobalCounters global = incrementGlobalIfCampaign(ctx);
        labJobEventService.record(new LabJobEventRequest(
                taskId,
                LabJobEventType.ITEM_COMPLETED,
                "Item " + index + "/" + runTotal + " completed",
                Map.of("itemIndex", index, "runTotalItems", runTotal),
                ctx.campaignId(),
                ctx.runId(),
                itemId,
                global.completed(),
                global.total(),
                runCompleted,
                runTotal,
                ctx.modelId(),
                ctx.presetCode()));
    }

    @Transactional
    public void emitRunCompleted(UUID taskId, UUID runId, String message) {
        RunContext ctx = resolveContext(runId, null, null, null, null);
        GlobalCounters global = readGlobalCounters(ctx);
        labJobEventService.record(new LabJobEventRequest(
                taskId,
                LabJobEventType.RUN_COMPLETED,
                message != null ? message : "Run completed",
                Map.of(),
                ctx.campaignId(),
                runId,
                null,
                global.completed(),
                global.total(),
                ctx.runTotalItems(),
                ctx.runTotalItems(),
                ctx.modelId(),
                ctx.presetCode()));
        maybeEmitCampaignCompleted(taskId, ctx);
    }

    private void maybeEmitCampaignStarted(UUID taskId, RunContext ctx) {
        if (ctx.campaignId() == null) {
            return;
        }
        EvaluationCampaignEntity camp =
                evaluationCampaignRepository.findById(ctx.campaignId()).orElse(null);
        if (camp == null) {
            return;
        }
        Map<String, Object> meta =
                camp.getMetaJson() != null ? new LinkedHashMap<>(camp.getMetaJson()) : new LinkedHashMap<>();
        if (Boolean.TRUE.equals(meta.get(META_CAMPAIGN_STARTED))) {
            return;
        }
        meta.put(META_CAMPAIGN_STARTED, true);
        Map<String, Object> progress = progressMap(meta);
        progress.put("globalTotalItems", ctx.globalTotalItems());
        progress.put("globalCompletedItems", 0);
        meta.put(META_JOB_PROGRESS, progress);
        camp.setMetaJson(Map.copyOf(meta));
        evaluationCampaignRepository.save(camp);
        labJobEventService.record(new LabJobEventRequest(
                taskId,
                LabJobEventType.CAMPAIGN_STARTED,
                "Campaign started",
                Map.of("campaignId", ctx.campaignId().toString()),
                ctx.campaignId(),
                null,
                null,
                0,
                ctx.globalTotalItems(),
                null,
                null,
                null,
                null));
    }

    private void maybeEmitCampaignCompleted(UUID taskId, RunContext ctx) {
        if (ctx.campaignId() == null) {
            return;
        }
        List<EvaluationRunEntity> runs =
                evaluationRunRepository.findByCampaignIdAndUserId(ctx.campaignId(), ctx.userId());
        boolean allTerminal =
                !runs.isEmpty()
                        && runs.stream()
                                .allMatch(
                                        r ->
                                                r.getStatus() == EvaluationRunStatus.DONE
                                                        || r.getStatus() == EvaluationRunStatus.ERROR
                                                        || r.getStatus() == EvaluationRunStatus.CANCELLED
                                                        || r.getStatus() == EvaluationRunStatus.PARTIAL_CANCELLED);
        if (!allTerminal) {
            return;
        }
        GlobalCounters global = readGlobalCounters(ctx);
        labJobEventService.record(new LabJobEventRequest(
                taskId,
                LabJobEventType.CAMPAIGN_COMPLETED,
                "Campaign completed",
                Map.of("campaignId", ctx.campaignId().toString()),
                ctx.campaignId(),
                null,
                null,
                global.completed(),
                global.total(),
                null,
                null,
                null,
                null));
    }

    private GlobalCounters incrementGlobalIfCampaign(RunContext ctx) {
        if (ctx.campaignId() == null) {
            return new GlobalCounters(null, null);
        }
        EvaluationCampaignEntity camp =
                evaluationCampaignRepository.findById(ctx.campaignId()).orElse(null);
        if (camp == null) {
            return new GlobalCounters(null, ctx.globalTotalItems());
        }
        Map<String, Object> meta =
                camp.getMetaJson() != null ? new LinkedHashMap<>(camp.getMetaJson()) : new LinkedHashMap<>();
        Map<String, Object> progress = progressMap(meta);
        int completed = intValue(progress.get("globalCompletedItems")) + 1;
        progress.put("globalCompletedItems", completed);
        if (ctx.globalTotalItems() != null) {
            progress.put("globalTotalItems", ctx.globalTotalItems());
        }
        meta.put(META_JOB_PROGRESS, progress);
        camp.setMetaJson(Map.copyOf(meta));
        evaluationCampaignRepository.save(camp);
        return new GlobalCounters(completed, intValue(progress.get("globalTotalItems")));
    }

    private GlobalCounters readGlobalCounters(RunContext ctx) {
        if (ctx.campaignId() == null) {
            return new GlobalCounters(null, ctx.globalTotalItems());
        }
        EvaluationCampaignEntity camp =
                evaluationCampaignRepository.findById(ctx.campaignId()).orElse(null);
        if (camp == null || camp.getMetaJson() == null) {
            return new GlobalCounters(null, ctx.globalTotalItems());
        }
        Map<String, Object> progress = progressMap(camp.getMetaJson());
        return new GlobalCounters(
                intOrNull(progress.get("globalCompletedItems")),
                intOrNull(progress.get("globalTotalItems")) != null
                        ? intOrNull(progress.get("globalTotalItems"))
                        : ctx.globalTotalItems());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> progressMap(Map<String, Object> meta) {
        Object raw = meta.get(META_JOB_PROGRESS);
        if (raw instanceof Map<?, ?> m) {
            return new LinkedHashMap<>((Map<String, Object>) m);
        }
        return new LinkedHashMap<>();
    }

    private RunContext resolveContext(
            UUID runId, Integer runTotalItems, Integer globalTotalItems, String modelId, String presetCode) {
        EvaluationRunEntity run = runId != null ? evaluationRunRepository.findById(runId).orElse(null) : null;
        UUID campaignId =
                runId != null ? evaluationRunRepository.findCampaignIdByRunId(runId).orElse(null) : null;
        UUID userId = run != null && run.getUser() != null ? run.getUser().getId() : null;
        UUID taskId = run != null && run.getAsyncTask() != null ? run.getAsyncTask().getId() : null;
        String resolvedModel = modelId;
        if (resolvedModel == null && run != null) {
            resolvedModel = run.getLlmModelId();
        }
        String resolvedPreset = presetCode;
        if (resolvedPreset == null && run != null && run.getAggregatesJson() != null) {
            Object codes = run.getAggregatesJson().get("requested_preset_codes");
            if (codes instanceof List<?> list && !list.isEmpty()) {
                resolvedPreset = String.valueOf(list.getFirst());
            }
        }
        Integer resolvedRunTotal = runTotalItems;
        Integer resolvedGlobal = globalTotalItems;
        if (campaignId != null && resolvedGlobal == null) {
            EvaluationCampaignEntity camp = evaluationCampaignRepository.findById(campaignId).orElse(null);
            if (camp != null && camp.getMetaJson() != null) {
                Map<String, Object> progress = progressMap(camp.getMetaJson());
                resolvedGlobal = intOrNull(progress.get("globalTotalItems"));
            }
        }
        return new RunContext(
                taskId, runId, campaignId, userId, resolvedRunTotal, resolvedGlobal, resolvedModel, resolvedPreset);
    }

    private static int intValue(Object raw) {
        if (raw instanceof Number n) {
            return n.intValue();
        }
        return 0;
    }

    private static Integer intOrNull(Object raw) {
        if (raw instanceof Number n) {
            return n.intValue();
        }
        return null;
    }

    public record RunContext(
            UUID primaryTaskId,
            UUID runId,
            UUID campaignId,
            UUID userId,
            Integer runTotalItems,
            Integer globalTotalItems,
            String modelId,
            String presetCode) {}

    private void storeGlobalTotalOnCampaign(UUID campaignId, int globalTotal) {
        EvaluationCampaignEntity camp = evaluationCampaignRepository.findById(campaignId).orElse(null);
        if (camp == null) {
            return;
        }
        Map<String, Object> meta =
                camp.getMetaJson() != null ? new LinkedHashMap<>(camp.getMetaJson()) : new LinkedHashMap<>();
        Map<String, Object> progress = progressMap(meta);
        progress.put("globalTotalItems", globalTotal);
        if (!progress.containsKey("globalCompletedItems")) {
            progress.put("globalCompletedItems", 0);
        }
        meta.put(META_JOB_PROGRESS, progress);
        camp.setMetaJson(Map.copyOf(meta));
        evaluationCampaignRepository.save(camp);
    }

    private record GlobalCounters(Integer completed, Integer total) {}

    @Transactional
    public void emitRagEvaluationAccepted(
            UUID taskId, UUID runId, UUID corpusId, UUID datasetId, UUID campaignId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (corpusId != null) {
            payload.put("corpusId", corpusId.toString());
            payload.put("knowledgeBaseId", corpusId.toString());
        }
        if (datasetId != null) {
            payload.put("datasetId", datasetId.toString());
        }
        labJobEventService.record(
                LabJobEventRequest.of(taskId, LabJobEventType.RAG_EVALUATION_ACCEPTED, "RAG evaluation accepted")
                        .withPayload(payload)
                        .withScope(campaignId, runId, null, null, null, null, null, null, null));
    }

    @Transactional
    public void emitSnapshotPreparationStarted(
            UUID taskId, UUID runId, LabPresetRunGroupKey groupKey, UUID corpusId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (groupKey != null) {
            payload.put("groupKey", groupKey.name());
        }
        if (corpusId != null) {
            payload.put("corpusId", corpusId.toString());
            payload.put("knowledgeBaseId", corpusId.toString());
        }
        labJobEventService.record(
                LabJobEventRequest.of(
                                taskId,
                                LabJobEventType.SNAPSHOT_PREPARATION_STARTED,
                                "Preparing index snapshot for " + (groupKey != null ? groupKey.name() : "group"))
                        .withPayload(payload)
                        .withScope(null, runId, null, null, null, null, null, null, null));
    }

    @Transactional
    public void emitSnapshotPreparationCompleted(
            UUID taskId, UUID runId, LabPresetRunGroupKey groupKey, UUID snapshotId, UUID corpusId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (groupKey != null) {
            payload.put("groupKey", groupKey.name());
        }
        if (snapshotId != null) {
            payload.put("snapshotId", snapshotId.toString());
        }
        if (corpusId != null) {
            payload.put("corpusId", corpusId.toString());
            payload.put("knowledgeBaseId", corpusId.toString());
        }
        labJobEventService.record(
                LabJobEventRequest.of(
                                taskId,
                                LabJobEventType.SNAPSHOT_PREPARATION_COMPLETED,
                                "Index snapshot ready for " + (groupKey != null ? groupKey.name() : "group"))
                        .withPayload(payload)
                        .withScope(null, runId, null, null, null, null, null, null, null));
    }
}
