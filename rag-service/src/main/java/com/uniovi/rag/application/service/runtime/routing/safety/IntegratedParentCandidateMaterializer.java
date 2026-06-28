package com.uniovi.rag.application.service.runtime.routing.safety;

import com.uniovi.rag.application.service.evaluation.preset.ExperimentalPresetCanonicalCatalog;
import com.uniovi.rag.domain.config.capability.CapabilitySet;
import com.uniovi.rag.domain.config.runtime.ResolvedRuntimeConfig;
import com.uniovi.rag.domain.evaluation.workbook.RagExperimentalPresetCode;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.engine.KnowledgeSnapshotSelection;
import com.uniovi.rag.domain.runtime.memory.ConversationMemoryOutcome;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRouteKind;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRoutingOutcome;
import java.util.List;
import java.util.Optional;

/** Materializes a parent preset runtime config for integrated-route monotonic fallback. */
public final class IntegratedParentCandidateMaterializer {

    public static final RagExperimentalPresetCode DEFAULT_PARENT_PRESET = RagExperimentalPresetCode.P7;
    public static final RagExperimentalPresetCode SECONDARY_PARENT_PRESET = RagExperimentalPresetCode.P6;

    private IntegratedParentCandidateMaterializer() {}

    public static ExecutionContext materialize(
            ExecutionContext base, RagExperimentalPresetCode parentPreset) {
        return materialize(base, parentPreset, fallbackSnapshots(base));
    }

    public static ExecutionContext materialize(
            ExecutionContext base,
            RagExperimentalPresetCode parentPreset,
            KnowledgeSnapshotSelection snapshots) {
        RagConfig core = base.resolved().resolvedCoreConfig();
        RagConfig parentConfig =
                RagConfig.applyJsonOverrides(
                        core, ExperimentalPresetCanonicalCatalog.effectiveTerminalRuntimeJson(parentPreset));
        ResolvedRuntimeConfig parentResolved =
                new ResolvedRuntimeConfig(
                        parentConfig,
                        CapabilitySet.fromRagConfig(parentConfig),
                        base.resolved().compatibility(),
                        base.resolved().reindexImpact(),
                        base.resolved().systemPromptLayers(),
                        base.resolved().effectiveSystemPrompt(),
                        base.resolved().provenance(),
                        parentConfig);
        return new ExecutionContext(
                base.userId(),
                base.projectId(),
                base.conversationId(),
                base.userQuery(),
                base.operationKind(),
                parentResolved,
                base.effectiveSystemPrompt(),
                snapshots,
                base.configHash(),
                base.pinnedResolvedConfigSnapshotId(),
                base.correlationId(),
                base.documentFilter(),
                base.chatModelOverride(),
                base.queryPlan(),
                Optional.empty(),
                Optional.empty(),
                base.preMemoryPlanningInputText(),
                base.effectivePlanningInputText(),
                base.memorySlice(),
                base.memoryOutcome(),
                base.memoryStageTraces(),
                base.memoryAttempted(),
                base.memoryHistoryLoaded(),
                base.memoryCondensationAttempted(),
                base.memoryCondensationUsed(),
                base.memoryFallbackApplied(),
                base.pendingClarificationLoadedForTrace(),
                base.validPendingExistedAtLoad(),
                base.invalidPendingRecoveredThisTurn(),
                base.clarificationDisableReason(),
                base.originatingUserMessageId(),
                false,
                AdaptiveRoutingOutcome.DISABLED_BY_CONFIG,
                AdaptiveRouteKind.DIRECT_WORKFLOW_ROUTE,
                false,
                Optional.empty(),
                false,
                List.of());
    }

    public static KnowledgeSnapshotSelection fallbackSnapshots(ExecutionContext base) {
        return base.knowledgeSnapshotSelection();
    }
}
