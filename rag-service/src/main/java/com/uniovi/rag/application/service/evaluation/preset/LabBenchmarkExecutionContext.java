package com.uniovi.rag.application.service.evaluation.preset;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Thread-local Lab benchmark execution scope: terminal runtime JSON override and optional project/snapshot binding
 * while {@link com.uniovi.rag.application.service.runtime.ExecutionContextFactory#buildForHttpQuery(String, String)} builds
 * {@link com.uniovi.rag.domain.runtime.engine.ExecutionContext}.
 * <p>Scoped per benchmark batch; always cleared via try-with-resources. Not used by product chat.</p>
 */
public final class LabBenchmarkExecutionContext {

    private static final ThreadLocal<JsonNode> TERMINAL_OVERRIDE = new ThreadLocal<>();
    private static final ThreadLocal<LabRuntimeContext> LAB_CONTEXT = new ThreadLocal<>();
    private static final ThreadLocal<Map<String, CampaignPresetBinding>> CAMPAIGN_PRESET_BINDINGS =
            ThreadLocal.withInitial(HashMap::new);
  private static final ThreadLocal<Map<String, Map<String, CampaignParentOutcome>>> CAMPAIGN_PARENT_OUTCOMES =
            ThreadLocal.withInitial(HashMap::new);
    private static final ThreadLocal<UUID> CURRENT_CAMPAIGN_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> CURRENT_DATASET_QUESTION_ID = new ThreadLocal<>();

    private LabBenchmarkExecutionContext() {}

    /** Active terminal merge JSON for the current Lab benchmark item, if any. */
    public static Optional<JsonNode> currentTerminalOverride() {
        return Optional.ofNullable(TERMINAL_OVERRIDE.get());
    }

    /** Active Lab-only runtime context (project/snapshot binding) for the current benchmark run, if any. */
    public static Optional<LabRuntimeContext> currentLabRuntimeContext() {
        return Optional.ofNullable(LAB_CONTEXT.get());
    }

    /**
     * Installs a terminal runtime JSON layer (same keys as {@link com.uniovi.rag.domain.runtime.RagConfig#applyJsonOverrides}).
     */
    public static AutoCloseable open(JsonNode terminalRuntimeOverride) {
        if (terminalRuntimeOverride == null || terminalRuntimeOverride.isNull()) {
            return () -> {};
        }
        TERMINAL_OVERRIDE.set(terminalRuntimeOverride);
        return () -> TERMINAL_OVERRIDE.remove();
    }

    /**
     * Installs both terminal runtime override and Lab runtime binding (project + explicit snapshot ids).
     *
     * <p>Lab-only: never used by normal chat runtime.</p>
     */
    public static AutoCloseable openLab(
            JsonNode terminalRuntimeOverride,
            UUID runId,
            UUID projectId,
            List<UUID> snapshotIds,
            String groupKey,
            String presetCode,
            boolean forcedSnapshotSelection) {
        AutoCloseable a = open(terminalRuntimeOverride);
        List<UUID> boundSnapshotIds = snapshotIds != null ? List.copyOf(snapshotIds) : List.of();
        LAB_CONTEXT.set(
                new LabRuntimeContext(
                        runId,
                        projectId,
                        boundSnapshotIds,
                        groupKey,
                        presetCode,
                        forcedSnapshotSelection));
        if (presetCode != null
                && !presetCode.isBlank()
                && projectId != null
                && !boundSnapshotIds.isEmpty()) {
            CAMPAIGN_PRESET_BINDINGS
                    .get()
                    .put(
                            presetCode,
                            new CampaignPresetBinding(
                                    runId, projectId, boundSnapshotIds, groupKey));
        }
        return () -> {
            try {
                a.close();
            } finally {
                LAB_CONTEXT.remove();
            }
        };
    }

    public static Optional<CampaignPresetBinding> campaignPresetBinding(String presetCode) {
        if (presetCode == null || presetCode.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(CAMPAIGN_PRESET_BINDINGS.get().get(presetCode));
    }

    /**
     * Clears per-preset snapshot bindings for the current benchmark item batch.
     * Campaign parent outcomes are intentionally retained until {@link #openCampaignScope(UUID)} closes.
     */
    public static void clearCampaignPresetBindings() {
        CAMPAIGN_PRESET_BINDINGS.remove();
    }

    /** Binds a campaign id for the current coordinator thread until the returned scope closes. */
    public static AutoCloseable openCampaignScope(UUID campaignId) {
        if (campaignId == null) {
            return () -> {};
        }
        CURRENT_CAMPAIGN_ID.set(campaignId);
        return () -> {
            CURRENT_CAMPAIGN_ID.remove();
            CampaignParentOutcomeStore.clear(campaignId);
        };
    }

    public static Optional<UUID> currentCampaignId() {
        return Optional.ofNullable(CURRENT_CAMPAIGN_ID.get());
    }

    public static Runnable openBenchmarkItemScope(String datasetQuestionId) {
        if (datasetQuestionId == null || datasetQuestionId.isBlank()) {
            return () -> {};
        }
        CURRENT_DATASET_QUESTION_ID.set(datasetQuestionId);
        return CURRENT_DATASET_QUESTION_ID::remove;
    }

    public static Optional<String> currentDatasetQuestionId() {
        return Optional.ofNullable(CURRENT_DATASET_QUESTION_ID.get());
    }

    public static void recordCampaignParentOutcome(
            String presetCode, String datasetQuestionId, CampaignParentOutcome outcome) {
        if (presetCode == null
                || presetCode.isBlank()
                || datasetQuestionId == null
                || datasetQuestionId.isBlank()
                || outcome == null) {
            return;
        }
        Optional<UUID> campaignId = currentCampaignId();
        if (campaignId.isPresent()) {
            CampaignParentOutcomeStore.record(campaignId.get(), presetCode, datasetQuestionId, outcome);
            return;
        }
        CAMPAIGN_PARENT_OUTCOMES
                .get()
                .computeIfAbsent(presetCode, ignored -> new HashMap<>())
                .put(datasetQuestionId, outcome);
    }

    public static Optional<CampaignParentOutcome> campaignParentOutcome(
            String presetCode, String datasetQuestionId) {
        if (presetCode == null || presetCode.isBlank() || datasetQuestionId == null || datasetQuestionId.isBlank()) {
            return Optional.empty();
        }
        Optional<UUID> campaignId = currentCampaignId();
        if (campaignId.isPresent()) {
            return CampaignParentOutcomeStore.lookup(campaignId.get(), presetCode, datasetQuestionId);
        }
        return Optional.ofNullable(CAMPAIGN_PARENT_OUTCOMES.get().get(presetCode))
                .map(byQuestion -> byQuestion.get(datasetQuestionId));
    }

    public static boolean hasCampaignParentOutcome(String presetCode, String datasetQuestionId) {
        return campaignParentOutcome(presetCode, datasetQuestionId).isPresent();
    }

    /** Removes any installed override (safe if none). */
    public static void clear() {
        UUID campaignId = CURRENT_CAMPAIGN_ID.get();
        TERMINAL_OVERRIDE.remove();
        LAB_CONTEXT.remove();
        CURRENT_DATASET_QUESTION_ID.remove();
        CURRENT_CAMPAIGN_ID.remove();
        CAMPAIGN_PARENT_OUTCOMES.remove();
        clearCampaignPresetBindings();
        if (campaignId != null) {
            CampaignParentOutcomeStore.clear(campaignId);
        }
    }

    public record LabRuntimeContext(
            UUID runId,
            UUID projectId,
            List<UUID> snapshotIds,
            String groupKey,
            String presetCode,
            boolean forcedSnapshotSelection
    ) {
        public LabRuntimeContext {
            snapshotIds = snapshotIds != null ? List.copyOf(snapshotIds) : List.of();
        }
    }

    public record CampaignPresetBinding(
            UUID runId, UUID projectId, List<UUID> snapshotIds, String groupKey) {
        public CampaignPresetBinding {
            snapshotIds = snapshotIds != null ? List.copyOf(snapshotIds) : List.of();
        }
    }
}
