package com.uniovi.rag.application.service.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.uniovi.rag.application.port.ModelCatalogPort;
import com.uniovi.rag.application.service.RuntimeConfigResolutionService;
import com.uniovi.rag.application.service.runtime.clarification.ClarificationBootstrap;
import com.uniovi.rag.application.service.runtime.clarification.ClarificationStateResolver;
import com.uniovi.rag.application.service.runtime.memory.ConversationMemoryStrategy;
import com.uniovi.rag.domain.config.EffectiveModelPolicy;
import com.uniovi.rag.domain.config.runtime.ResolvedRuntimeConfig;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.domain.runtime.RagExecutionContext;
import com.uniovi.rag.domain.runtime.advisor.PackedContextSet;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.engine.KnowledgeSnapshotSelection;
import com.uniovi.rag.domain.runtime.engine.RuntimeOperationKind;
import com.uniovi.rag.domain.runtime.memory.ConversationMemoryExecutionResult;
import com.uniovi.rag.domain.runtime.memory.ConversationMemoryOutcome;
import com.uniovi.rag.domain.runtime.reasoning.StructuredAnswerPlan;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRouteKind;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRoutingOutcome;
import com.uniovi.rag.infrastructure.observability.TraceMdcBridge;
import com.uniovi.rag.application.service.evaluation.preset.ExperimentalPresetCanonicalCatalog;
import com.uniovi.rag.application.service.runtime.config.MaterializationAwareSnapshotResolver;
import com.uniovi.rag.application.service.config.ChatScopedRagConfigResolver;
import com.uniovi.rag.application.service.config.llm.ResolvedLlmConfigResolver;
import com.uniovi.rag.application.exception.llm.LlmSafeOperationLogger;
import com.uniovi.rag.application.service.runtime.llm.OrchestrationLlmConfigScope;
import com.uniovi.rag.domain.llm.ResolvedLlmConfig;
import com.uniovi.rag.application.service.evaluation.preset.LabBenchmarkExecutionContext;
import io.micrometer.tracing.Tracer;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Sole constructor of {@link ExecutionContext} for orchestrated turns.
 */
@Service
public class ExecutionContextFactory {

    private static final Logger log = LoggerFactory.getLogger(ExecutionContextFactory.class);

    private final RuntimeConfigResolutionService runtimeConfigResolutionService;
    private final KnowledgeRuntimeSnapshotSelector knowledgeRuntimeSnapshotSelector;
    private final ChatScopedRagConfigResolver chatScopedRagConfigResolver;
    private final ModelCatalogPort modelCatalogPort;
    private final Tracer tracer;
    private final ClarificationStateResolver clarificationStateResolver;
    private final ConversationMemoryStrategy conversationMemoryStrategy;
    private final ResolvedLlmConfigResolver resolvedLlmConfigResolver;

    public ExecutionContextFactory(
            RuntimeConfigResolutionService runtimeConfigResolutionService,
            KnowledgeRuntimeSnapshotSelector knowledgeRuntimeSnapshotSelector,
            ChatScopedRagConfigResolver chatScopedRagConfigResolver,
            ModelCatalogPort modelCatalogPort,
            ClarificationStateResolver clarificationStateResolver,
            ConversationMemoryStrategy conversationMemoryStrategy,
            ResolvedLlmConfigResolver resolvedLlmConfigResolver,
            @Autowired(required = false) Tracer tracer) {
        this.runtimeConfigResolutionService = runtimeConfigResolutionService;
        this.knowledgeRuntimeSnapshotSelector = knowledgeRuntimeSnapshotSelector;
        this.chatScopedRagConfigResolver = chatScopedRagConfigResolver;
        this.modelCatalogPort = modelCatalogPort;
        this.clarificationStateResolver = clarificationStateResolver;
        this.conversationMemoryStrategy = conversationMemoryStrategy;
        this.resolvedLlmConfigResolver = resolvedLlmConfigResolver;
        this.tracer = tracer;
    }

    public ExecutionContext buildForChatMessage(
            UUID userId,
            UUID projectId,
            UUID conversationId,
            String rawUserQuery,
            List<String> documentFilter,
            String chatModelOverride) {
        return buildForChatMessage(
                userId, projectId, conversationId, rawUserQuery, documentFilter, chatModelOverride, Optional.empty());
    }

    public ExecutionContext buildForChatMessage(
            UUID userId,
            UUID projectId,
            UUID conversationId,
            String rawUserQuery,
            List<String> documentFilter,
            String chatModelOverride,
            Optional<UUID> originatingUserMessageId) {
        String correlationId =
                Optional.ofNullable(TraceMdcBridge.currentCorrelationTraceId(tracer))
                        .orElseGet(() -> UUID.randomUUID().toString());
        Optional<String> model = validateAndNormalizeChatModel(chatModelOverride);
        JsonNode merged =
                conversationId != null
                        ? chatScopedRagConfigResolver.mergedConversationConfigAsJson(conversationId)
                        : null;
        ResolvedRuntimeConfig resolved =
                runtimeConfigResolutionService.resolveForOrchestratedExecute(
                        userId, projectId, merged, correlationId);
        Optional<UUID> presetId =
                resolved.provenance() != null && resolved.provenance().presetId() != null
                        ? Optional.of(resolved.provenance().presetId())
                        : Optional.empty();
        ExperimentalPresetCanonicalCatalog.IndexRequirements indexRequirements =
                MaterializationAwareSnapshotResolver.requirementsFromPresetAndRag(
                        presetId, resolved.toRagConfig());
        KnowledgeSnapshotSelection snapshots =
                knowledgeRuntimeSnapshotSelector.select(projectId, conversationId, indexRequirements);
        List<String> filter = copyDocumentFilter(documentFilter);
        bindResolvedLlmConfig(userId, projectId, resolved, merged, model);
        return buildWithClarification(
                userId,
                projectId,
                conversationId,
                rawUserQuery,
                RuntimeOperationKind.CHAT_MESSAGE,
                resolved,
                snapshots,
                correlationId,
                filter,
                model,
                originatingUserMessageId);
    }

    /** Lab evaluation and other HTTP-scoped turns without a conversation (uses thread-local lab context when set). */
    public ExecutionContext buildForHttpQuery(String rawUserQuery, String chatModelOverride) {
        String correlationId =
                Optional.ofNullable(TraceMdcBridge.currentCorrelationTraceId(tracer))
                        .orElseGet(() -> UUID.randomUUID().toString());
        Optional<String> model = validateAndNormalizeChatModel(chatModelOverride);
        JsonNode benchmarkTerminal =
                LabBenchmarkExecutionContext.currentTerminalOverride().orElse(null);
        LabBenchmarkExecutionContext.LabRuntimeContext labCtx =
                LabBenchmarkExecutionContext.currentLabRuntimeContext().orElse(null);
        UUID projectId = labCtx != null ? labCtx.projectId() : null;
        ResolvedRuntimeConfig resolved =
                runtimeConfigResolutionService.resolveForOrchestratedExecute(
                        null, projectId, benchmarkTerminal, correlationId);
        KnowledgeSnapshotSelection snapshots;
        if (labCtx != null && labCtx.snapshotIds() != null && !labCtx.snapshotIds().isEmpty()) {
            snapshots = knowledgeRuntimeSnapshotSelector.selectExplicit(projectId, labCtx.snapshotIds());
        } else if (labCtx != null && labCtx.forcedSnapshotSelection()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "LAB_FORCED_SNAPSHOT_SELECTION_REQUIRES_SNAPSHOT_IDS");
        } else {
            snapshots = knowledgeRuntimeSnapshotSelector.select(projectId, null);
        }
        bindResolvedLlmConfig(null, projectId, resolved, benchmarkTerminal, model);
        return buildWithClarification(
                null,
                projectId,
                null,
                rawUserQuery,
                RuntimeOperationKind.STATELESS_HTTP,
                resolved,
                snapshots,
                correlationId,
                List.of(RagExecutionContext.ALL_DOCUMENTS),
                model,
                Optional.empty());
    }

    public ExecutionContext buildForLabProcess(
            UUID userId,
            UUID projectId,
            UUID conversationId,
            String rawUserQuery,
            List<String> documentFilter,
            String chatModelOverride) {
        String correlationId =
                Optional.ofNullable(TraceMdcBridge.currentCorrelationTraceId(tracer))
                        .orElseGet(() -> UUID.randomUUID().toString());
        Optional<String> model = validateAndNormalizeChatModel(chatModelOverride);
        JsonNode merged =
                conversationId != null
                        ? chatScopedRagConfigResolver.mergedConversationConfigAsJson(conversationId)
                        : null;
        ResolvedRuntimeConfig resolved =
                runtimeConfigResolutionService.resolveForOrchestratedExecute(
                        userId, projectId, merged, correlationId);
        Optional<UUID> presetId =
                resolved.provenance() != null && resolved.provenance().presetId() != null
                        ? Optional.of(resolved.provenance().presetId())
                        : Optional.empty();
        ExperimentalPresetCanonicalCatalog.IndexRequirements indexRequirements =
                MaterializationAwareSnapshotResolver.requirementsFromPresetAndRag(
                        presetId, resolved.toRagConfig());
        KnowledgeSnapshotSelection snapshots =
                knowledgeRuntimeSnapshotSelector.select(projectId, conversationId, indexRequirements);
        bindResolvedLlmConfig(userId, projectId, resolved, merged, model);
        return buildWithClarification(
                userId,
                projectId,
                conversationId,
                rawUserQuery,
                RuntimeOperationKind.LAB_PROCESS,
                resolved,
                snapshots,
                correlationId,
                copyDocumentFilter(documentFilter),
                model,
                Optional.empty());
    }

    private void bindResolvedLlmConfig(
            UUID userId,
            UUID projectId,
            ResolvedRuntimeConfig resolved,
            JsonNode terminalConversationMergedOverride,
            Optional<String> chatModelOverride) {
        UUID presetId =
                resolved != null
                                && resolved.provenance() != null
                                && resolved.provenance().presetId() != null
                        ? resolved.provenance().presetId()
                        : null;
        ResolvedLlmConfig llm =
                resolvedLlmConfigResolver.resolveForOrchestratedExecute(
                        userId, projectId, presetId, terminalConversationMergedOverride, chatModelOverride);
        LlmSafeOperationLogger.logResolvedConfig(log, llm);
        OrchestrationLlmConfigScope.bind(llm);
    }

    private ExecutionContext buildWithClarification(
            UUID userId,
            UUID projectId,
            UUID conversationId,
            String rawUserQuery,
            RuntimeOperationKind operationKind,
            ResolvedRuntimeConfig resolved,
            KnowledgeSnapshotSelection snapshots,
            String correlationId,
            List<String> documentFilter,
            Optional<String> chatModelOverride,
            Optional<UUID> originatingUserMessageId) {
        String uq = rawUserQuery != null ? rawUserQuery : "";
        RagConfig rag = resolved.toRagConfig();
        Optional<String> disableReason = clarificationDisableReason(rag, conversationId);
        ClarificationBootstrap boot = clarificationStateResolver.bootstrap(conversationId, uq);
        String preMemory = boot.effectivePlanningInputText();
        ExecutionContext base = new ExecutionContext(
                userId,
                projectId,
                conversationId,
                uq,
                operationKind,
                resolved,
                resolved.effectiveSystemPrompt(),
                snapshots,
                Optional.empty(),
                Optional.empty(),
                correlationId,
                documentFilter,
                chatModelOverride,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                preMemory,
                boot.effectivePlanningInputText(),
                Optional.empty(),
                rag.memoryEnabled() && conversationId != null
                        ? ConversationMemoryOutcome.NO_HISTORY_AVAILABLE
                        : (conversationId == null ? ConversationMemoryOutcome.NO_CONVERSATION_SCOPE : ConversationMemoryOutcome.DISABLED_BY_CONFIG),
                List.of(),
                false,
                false,
                false,
                false,
                false,
                boot.pendingClarificationLoadedForTrace(),
                boot.validPendingExistedAtLoad(),
                boot.invalidPendingRecoveredThisTurn(),
                disableReason,
                originatingUserMessageId,
                false,
                AdaptiveRoutingOutcome.DISABLED_BY_CONFIG,
                AdaptiveRouteKind.DIRECT_WORKFLOW_ROUTE,
                false,
                Optional.empty(),
                false,
                List.of());

        ConversationMemoryExecutionResult mem = conversationMemoryStrategy.execute(base, preMemory);
        boolean attempted =
                mem.outcome() != ConversationMemoryOutcome.DISABLED_BY_CONFIG
                        && mem.outcome() != ConversationMemoryOutcome.NO_CONVERSATION_SCOPE;
        boolean historyLoaded = attempted && (mem.outcome() != ConversationMemoryOutcome.DISABLED_BY_CONFIG);
        boolean memoryAppliedForTrace = memoryAppliedForTrace(mem);
        return new ExecutionContext(
                base.userId(),
                base.projectId(),
                base.conversationId(),
                base.userQuery(),
                base.operationKind(),
                base.resolved(),
                base.effectiveSystemPrompt(),
                base.knowledgeSnapshotSelection(),
                base.configHash(),
                base.pinnedResolvedConfigSnapshotId(),
                base.correlationId(),
                base.documentFilter(),
                base.chatModelOverride(),
                base.queryPlan(),
                base.advisorPackedContextSet(),
                base.structuredAnswerPlan(),
                preMemory,
                mem.finalPlanningInputText(),
                mem.slice(),
                mem.outcome(),
                mem.stageTraces(),
                attempted,
                historyLoaded,
                mem.condensationAttempted(),
                memoryAppliedForTrace,
                mem.fallbackApplied(),
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

    /** True when memory changed planning input (condensation or deterministic follow-up expansion). */
    static boolean memoryAppliedForTrace(ConversationMemoryExecutionResult mem) {
        if (mem == null) {
            return false;
        }
        if (mem.condensationUsed()) {
            return true;
        }
        return mem.outcome() == ConversationMemoryOutcome.MEMORY_APPLIED;
    }

    private static Optional<String> clarificationDisableReason(RagConfig rag, UUID conversationId) {
        if (!rag.clarificationEnabled()) {
            return Optional.of("config_disabled");
        }
        if (conversationId == null) {
            return Optional.of("no_persistable_conversation_scope");
        }
        return Optional.empty();
    }

    public ExecutionContext attachQueryPlan(ExecutionContext ctx, QueryPlan plan) {
        if (ctx == null) {
            throw new IllegalArgumentException("ctx must not be null");
        }
        if (plan == null) {
            throw new IllegalArgumentException("plan must not be null");
        }
        if (ctx.queryPlan().isPresent()) {
            throw new IllegalStateException("ExecutionContext already contains a QueryPlan");
        }
        return new ExecutionContext(
                ctx.userId(),
                ctx.projectId(),
                ctx.conversationId(),
                ctx.userQuery(),
                ctx.operationKind(),
                ctx.resolved(),
                ctx.effectiveSystemPrompt(),
                ctx.knowledgeSnapshotSelection(),
                ctx.configHash(),
                ctx.pinnedResolvedConfigSnapshotId(),
                ctx.correlationId(),
                ctx.documentFilter(),
                ctx.chatModelOverride(),
                Optional.of(plan),
                Optional.empty(),
                ctx.structuredAnswerPlan(),
                ctx.preMemoryPlanningInputText(),
                ctx.effectivePlanningInputText(),
                ctx.memorySlice(),
                ctx.memoryOutcome(),
                ctx.memoryStageTraces(),
                ctx.memoryAttempted(),
                ctx.memoryHistoryLoaded(),
                ctx.memoryCondensationAttempted(),
                ctx.memoryCondensationUsed(),
                ctx.memoryFallbackApplied(),
                ctx.pendingClarificationLoadedForTrace(),
                ctx.validPendingExistedAtLoad(),
                ctx.invalidPendingRecoveredThisTurn(),
                ctx.clarificationDisableReason(),
                ctx.originatingUserMessageId(),
                ctx.routingAttempted(),
                ctx.routingOutcome(),
                ctx.routingRouteKind(),
                ctx.routingFallbackApplied(),
                ctx.routingFallbackRouteKind(),
                ctx.routingWorkflowSelectorInvoked(),
                ctx.routingStageTraces());
    }

    /**
     * Attaches P10 advisor packed context for dense workflows; must be called only after {@link #attachQueryPlan}.
     */
    public ExecutionContext attachAdvisorPackedContextSet(ExecutionContext ctx, PackedContextSet packedContextSet) {
        if (ctx == null) {
            throw new IllegalArgumentException("ctx must not be null");
        }
        if (packedContextSet == null) {
            throw new IllegalArgumentException("packedContextSet must not be null");
        }
        if (ctx.queryPlan().isEmpty()) {
            throw new IllegalStateException("ExecutionContext must contain a QueryPlan before attaching advisor packed context");
        }
        return new ExecutionContext(
                ctx.userId(),
                ctx.projectId(),
                ctx.conversationId(),
                ctx.userQuery(),
                ctx.operationKind(),
                ctx.resolved(),
                ctx.effectiveSystemPrompt(),
                ctx.knowledgeSnapshotSelection(),
                ctx.configHash(),
                ctx.pinnedResolvedConfigSnapshotId(),
                ctx.correlationId(),
                ctx.documentFilter(),
                ctx.chatModelOverride(),
                ctx.queryPlan(),
                Optional.of(packedContextSet),
                ctx.structuredAnswerPlan(),
                ctx.preMemoryPlanningInputText(),
                ctx.effectivePlanningInputText(),
                ctx.memorySlice(),
                ctx.memoryOutcome(),
                ctx.memoryStageTraces(),
                ctx.memoryAttempted(),
                ctx.memoryHistoryLoaded(),
                ctx.memoryCondensationAttempted(),
                ctx.memoryCondensationUsed(),
                ctx.memoryFallbackApplied(),
                ctx.pendingClarificationLoadedForTrace(),
                ctx.validPendingExistedAtLoad(),
                ctx.invalidPendingRecoveredThisTurn(),
                ctx.clarificationDisableReason(),
                ctx.originatingUserMessageId(),
                ctx.routingAttempted(),
                ctx.routingOutcome(),
                ctx.routingRouteKind(),
                ctx.routingFallbackApplied(),
                ctx.routingFallbackRouteKind(),
                ctx.routingWorkflowSelectorInvoked(),
                ctx.routingStageTraces());
    }

    /**
     * Attaches a safe structured answer plan for R8A.
     */
    public ExecutionContext attachStructuredAnswerPlan(ExecutionContext ctx, StructuredAnswerPlan plan) {
        if (ctx == null) {
            throw new IllegalArgumentException("ctx must not be null");
        }
        if (plan == null) {
            throw new IllegalArgumentException("plan must not be null");
        }
        if (ctx.structuredAnswerPlan().isPresent()) {
            throw new IllegalStateException("ExecutionContext already contains a StructuredAnswerPlan");
        }
        return new ExecutionContext(
                ctx.userId(),
                ctx.projectId(),
                ctx.conversationId(),
                ctx.userQuery(),
                ctx.operationKind(),
                ctx.resolved(),
                ctx.effectiveSystemPrompt(),
                ctx.knowledgeSnapshotSelection(),
                ctx.configHash(),
                ctx.pinnedResolvedConfigSnapshotId(),
                ctx.correlationId(),
                ctx.documentFilter(),
                ctx.chatModelOverride(),
                ctx.queryPlan(),
                ctx.advisorPackedContextSet(),
                Optional.of(plan),
                ctx.preMemoryPlanningInputText(),
                ctx.effectivePlanningInputText(),
                ctx.memorySlice(),
                ctx.memoryOutcome(),
                ctx.memoryStageTraces(),
                ctx.memoryAttempted(),
                ctx.memoryHistoryLoaded(),
                ctx.memoryCondensationAttempted(),
                ctx.memoryCondensationUsed(),
                ctx.memoryFallbackApplied(),
                ctx.pendingClarificationLoadedForTrace(),
                ctx.validPendingExistedAtLoad(),
                ctx.invalidPendingRecoveredThisTurn(),
                ctx.clarificationDisableReason(),
                ctx.originatingUserMessageId(),
                ctx.routingAttempted(),
                ctx.routingOutcome(),
                ctx.routingRouteKind(),
                ctx.routingFallbackApplied(),
                ctx.routingFallbackRouteKind(),
                ctx.routingWorkflowSelectorInvoked(),
                ctx.routingStageTraces());
    }

    private Optional<String> validateAndNormalizeChatModel(String chatModelOverride) {
        if (chatModelOverride == null || chatModelOverride.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(
                    EffectiveModelPolicy.validateChatModelOverride(
                            chatModelOverride, modelCatalogPort.allowedLlmNamesInGovernance()));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    private static List<String> copyDocumentFilter(List<String> documentFilter) {
        if (documentFilter == null || documentFilter.isEmpty()) {
            return List.of(RagExecutionContext.ALL_DOCUMENTS);
        }
        return List.copyOf(documentFilter);
    }
}
