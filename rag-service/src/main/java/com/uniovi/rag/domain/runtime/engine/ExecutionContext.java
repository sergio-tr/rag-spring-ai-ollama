package com.uniovi.rag.domain.runtime.engine;

import com.uniovi.rag.domain.config.runtime.ResolvedRuntimeConfig;
import com.uniovi.rag.domain.runtime.advisor.PackedContextSet;
import com.uniovi.rag.domain.runtime.memory.ConversationMemoryOutcome;
import com.uniovi.rag.domain.runtime.memory.ConversationMemorySlice;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRouteKind;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRoutingOutcome;
import com.uniovi.rag.domain.runtime.reasoning.StructuredAnswerPlan;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Canonical immutable context for one orchestrated RAG turn. Constructed only by {@code ExecutionContextFactory}.
 */
public record ExecutionContext(
        UUID userId,
        UUID projectId,
        UUID conversationId,
        String userQuery,
        RuntimeOperationKind operationKind,
        ResolvedRuntimeConfig resolved,
        String effectiveSystemPrompt,
        KnowledgeSnapshotSelection knowledgeSnapshotSelection,
        Optional<String> configHash,
        Optional<UUID> pinnedResolvedConfigSnapshotId,
        String correlationId,
        List<String> documentFilter,
        Optional<String> chatModelOverride,
        Optional<QueryPlan> queryPlan,
        Optional<PackedContextSet> advisorPackedContextSet,
        /** R8A: safe structured plan to guide controlled generation. */
        Optional<StructuredAnswerPlan> structuredAnswerPlan,
        /** P12: planning input after clarification pre-processing but before memory stage. */
        String preMemoryPlanningInputText,
        /** Normalize input for {@link com.uniovi.rag.application.service.runtime.query.QueryUnderstandingPipeline} only. */
        String effectivePlanningInputText,
        /** P12: selected bounded memory slice (when memory attempted and slice exists). */
        Optional<ConversationMemorySlice> memorySlice,
        /** P12: terminal memory outcome for this turn. */
        ConversationMemoryOutcome memoryOutcome,
        /** P12: raw memory stage traces (merged into final trace only by orchestrator). */
        List<ExecutionStageTrace> memoryStageTraces,
        /** P12: summary booleans for the frozen trace matrix. */
        boolean memoryAttempted,
        boolean memoryHistoryLoaded,
        boolean memoryCondensationAttempted,
        boolean memoryCondensationUsed,
        boolean memoryFallbackApplied,
        /**
         * P11 trace: {@code true} iff valid pending was loaded and {@link com.uniovi.rag.application.service.runtime.clarification.ClarifiedQueryRefiner}
         * produced merged planning text before QU.
         */
        boolean pendingClarificationLoadedForTrace,
        /** P11: valid non-null pending existed at load before any invalid recovery clear. */
        boolean validPendingExistedAtLoad,
        /** P11: invalid JSON/version was cleared this turn before QU. */
        boolean invalidPendingRecoveredThisTurn,
        /** P11: when present, clarification is disabled by gates ({@code config_disabled} or {@code no_persistable_conversation_scope}). */
        Optional<String> clarificationDisableReason,
        /** User message id for the current turn when provided by the chat pipeline (optional). */
        Optional<UUID> originatingUserMessageId,
        /** P13: whether adaptive routing ran for this turn (summary only; trace is assembled by orchestrator). */
        boolean routingAttempted,
        /** P13: terminal routing outcome (written by orchestrator). */
        AdaptiveRoutingOutcome routingOutcome,
        /** P13: selected primary route family for the turn (or compatibility default when disabled). */
        AdaptiveRouteKind routingRouteKind,
        /** P13: true iff a workflow fallback route was applied. */
        boolean routingFallbackApplied,
        /** P13: fallback workflow route family when fallback applied. */
        Optional<AdaptiveRouteKind> routingFallbackRouteKind,
        /** P13: true iff {@code WorkflowSelector.select} was invoked for this turn. */
        boolean routingWorkflowSelectorInvoked,
        /** P13: raw routing stage traces (merged into final trace only by orchestrator). */
        List<ExecutionStageTrace> routingStageTraces) {

    public ExecutionContext {
        documentFilter = List.copyOf(documentFilter);
        configHash = Objects.requireNonNullElseGet(configHash, Optional::empty);
        pinnedResolvedConfigSnapshotId = Objects.requireNonNullElseGet(pinnedResolvedConfigSnapshotId, Optional::empty);
        chatModelOverride = Objects.requireNonNullElseGet(chatModelOverride, Optional::empty);
        queryPlan = Objects.requireNonNullElseGet(queryPlan, Optional::empty);
        advisorPackedContextSet = Objects.requireNonNullElseGet(advisorPackedContextSet, Optional::empty);
        structuredAnswerPlan = Objects.requireNonNullElseGet(structuredAnswerPlan, Optional::empty);
        preMemoryPlanningInputText = preMemoryPlanningInputText != null ? preMemoryPlanningInputText : "";
        effectivePlanningInputText =
                effectivePlanningInputText != null ? effectivePlanningInputText : "";
        memorySlice = Objects.requireNonNullElseGet(memorySlice, Optional::empty);
        memoryOutcome = memoryOutcome == null ? ConversationMemoryOutcome.DISABLED_BY_CONFIG : memoryOutcome;
        memoryStageTraces = List.copyOf(memoryStageTraces == null ? List.of() : memoryStageTraces);
        clarificationDisableReason = Objects.requireNonNullElseGet(clarificationDisableReason, Optional::empty);
        originatingUserMessageId = Objects.requireNonNullElseGet(originatingUserMessageId, Optional::empty);
        routingOutcome =
                routingOutcome == null ? AdaptiveRoutingOutcome.DISABLED_BY_CONFIG : routingOutcome;
        routingRouteKind =
                routingRouteKind == null ? AdaptiveRouteKind.DIRECT_WORKFLOW_ROUTE : routingRouteKind;
        routingFallbackRouteKind = Objects.requireNonNullElseGet(routingFallbackRouteKind, Optional::empty);
        routingStageTraces = List.copyOf(routingStageTraces == null ? List.of() : routingStageTraces);
    }

    /**
     * Backwards-compatible constructor for call sites that predate P13 routing fields.
     * Routing defaults represent "not attempted, disabled-by-config" and empty routing stages.
     */
    public ExecutionContext(
            UUID userId,
            UUID projectId,
            UUID conversationId,
            String userQuery,
            RuntimeOperationKind operationKind,
            ResolvedRuntimeConfig resolved,
            String effectiveSystemPrompt,
            KnowledgeSnapshotSelection knowledgeSnapshotSelection,
            Optional<String> configHash,
            Optional<UUID> pinnedResolvedConfigSnapshotId,
            String correlationId,
            List<String> documentFilter,
            Optional<String> chatModelOverride,
            Optional<QueryPlan> queryPlan,
            Optional<PackedContextSet> advisorPackedContextSet,
            String preMemoryPlanningInputText,
            String effectivePlanningInputText,
            Optional<ConversationMemorySlice> memorySlice,
            ConversationMemoryOutcome memoryOutcome,
            List<ExecutionStageTrace> memoryStageTraces,
            boolean memoryAttempted,
            boolean memoryHistoryLoaded,
            boolean memoryCondensationAttempted,
            boolean memoryCondensationUsed,
            boolean memoryFallbackApplied,
            boolean pendingClarificationLoadedForTrace,
            boolean validPendingExistedAtLoad,
            boolean invalidPendingRecoveredThisTurn,
            Optional<String> clarificationDisableReason,
            Optional<UUID> originatingUserMessageId
    ) {
        this(
                userId,
                projectId,
                conversationId,
                userQuery,
                operationKind,
                resolved,
                effectiveSystemPrompt,
                knowledgeSnapshotSelection,
                configHash,
                pinnedResolvedConfigSnapshotId,
                correlationId,
                documentFilter,
                chatModelOverride,
                queryPlan,
                advisorPackedContextSet,
                Optional.empty(),
                preMemoryPlanningInputText,
                effectivePlanningInputText,
                memorySlice,
                memoryOutcome,
                memoryStageTraces,
                memoryAttempted,
                memoryHistoryLoaded,
                memoryCondensationAttempted,
                memoryCondensationUsed,
                memoryFallbackApplied,
                pendingClarificationLoadedForTrace,
                validPendingExistedAtLoad,
                invalidPendingRecoveredThisTurn,
                clarificationDisableReason,
                originatingUserMessageId,
                false,
                AdaptiveRoutingOutcome.DISABLED_BY_CONFIG,
                AdaptiveRouteKind.DIRECT_WORKFLOW_ROUTE,
                false,
                Optional.empty(),
                false,
                List.of());
    }

    /**
     * Stateless removed HTTP path: no conversation id.
     */
    public boolean stateless() {
        return conversationId == null;
    }
}
