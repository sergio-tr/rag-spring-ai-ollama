package com.uniovi.rag.domain.runtime.engine;

import com.uniovi.rag.domain.config.runtime.ResolvedRuntimeConfig;
import com.uniovi.rag.domain.runtime.advisor.PackedContextSet;
import com.uniovi.rag.domain.runtime.query.QueryPlan;

import java.util.List;
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
        /** Normalize input for {@link com.uniovi.rag.application.service.runtime.query.QueryUnderstandingPipeline} only. */
        String effectivePlanningInputText,
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
        Optional<UUID> originatingUserMessageId) {

    public ExecutionContext {
        documentFilter = List.copyOf(documentFilter);
        configHash = configHash == null ? Optional.empty() : configHash;
        pinnedResolvedConfigSnapshotId =
                pinnedResolvedConfigSnapshotId == null ? Optional.empty() : pinnedResolvedConfigSnapshotId;
        chatModelOverride = chatModelOverride == null ? Optional.empty() : chatModelOverride;
        queryPlan = queryPlan == null ? Optional.empty() : queryPlan;
        advisorPackedContextSet = advisorPackedContextSet == null ? Optional.empty() : advisorPackedContextSet;
        effectivePlanningInputText =
                effectivePlanningInputText != null ? effectivePlanningInputText : "";
        clarificationDisableReason =
                clarificationDisableReason == null ? Optional.empty() : clarificationDisableReason;
        originatingUserMessageId =
                originatingUserMessageId == null ? Optional.empty() : originatingUserMessageId;
    }

    /**
     * Stateless legacy HTTP path: no conversation id.
     */
    public boolean stateless() {
        return conversationId == null;
    }
}
