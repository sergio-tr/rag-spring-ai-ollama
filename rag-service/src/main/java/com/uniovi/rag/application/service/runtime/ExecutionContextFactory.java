package com.uniovi.rag.application.service.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.uniovi.rag.application.port.ModelCatalogPort;
import com.uniovi.rag.application.service.RuntimeConfigResolutionService;
import com.uniovi.rag.domain.config.EffectiveModelPolicy;
import com.uniovi.rag.domain.config.runtime.ResolvedRuntimeConfig;
import com.uniovi.rag.domain.runtime.RagExecutionContext;
import com.uniovi.rag.application.service.runtime.memory.ConversationMemoryStrategy;
import com.uniovi.rag.application.service.runtime.clarification.ClarificationBootstrap;
import com.uniovi.rag.application.service.runtime.clarification.ClarificationStateResolver;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.advisor.PackedContextSet;
import com.uniovi.rag.domain.runtime.engine.KnowledgeSnapshotSelection;
import com.uniovi.rag.domain.runtime.engine.RuntimeOperationKind;
import com.uniovi.rag.domain.runtime.memory.ConversationMemoryExecutionResult;
import com.uniovi.rag.domain.runtime.memory.ConversationMemoryOutcome;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.infrastructure.observability.TraceMdcBridge;
import com.uniovi.rag.service.config.ChatScopedRagConfigResolver;
import io.micrometer.tracing.Tracer;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Sole constructor of {@link ExecutionContext} for orchestrated turns.
 */
@Service
public class ExecutionContextFactory {

    private final RuntimeConfigResolutionService runtimeConfigResolutionService;
    private final KnowledgeRuntimeSnapshotSelector knowledgeRuntimeSnapshotSelector;
    private final ChatScopedRagConfigResolver chatScopedRagConfigResolver;
    private final ModelCatalogPort modelCatalogPort;
    private final Tracer tracer;
    private final ClarificationStateResolver clarificationStateResolver;
    private final ConversationMemoryStrategy conversationMemoryStrategy;

    public ExecutionContextFactory(
            RuntimeConfigResolutionService runtimeConfigResolutionService,
            KnowledgeRuntimeSnapshotSelector knowledgeRuntimeSnapshotSelector,
            ChatScopedRagConfigResolver chatScopedRagConfigResolver,
            ModelCatalogPort modelCatalogPort,
            ClarificationStateResolver clarificationStateResolver,
            ConversationMemoryStrategy conversationMemoryStrategy,
            @org.springframework.beans.factory.annotation.Autowired(required = false) Tracer tracer) {
        this.runtimeConfigResolutionService = runtimeConfigResolutionService;
        this.knowledgeRuntimeSnapshotSelector = knowledgeRuntimeSnapshotSelector;
        this.chatScopedRagConfigResolver = chatScopedRagConfigResolver;
        this.modelCatalogPort = modelCatalogPort;
        this.clarificationStateResolver = clarificationStateResolver;
        this.conversationMemoryStrategy = conversationMemoryStrategy;
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
        KnowledgeSnapshotSelection snapshots =
                knowledgeRuntimeSnapshotSelector.select(projectId, conversationId);
        List<String> filter = copyDocumentFilter(documentFilter);
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

    public ExecutionContext buildForLegacyHttp(String rawUserQuery, String chatModelOverride) {
        String correlationId =
                Optional.ofNullable(TraceMdcBridge.currentCorrelationTraceId(tracer))
                        .orElseGet(() -> UUID.randomUUID().toString());
        Optional<String> model = validateAndNormalizeChatModel(chatModelOverride);
        ResolvedRuntimeConfig resolved =
                runtimeConfigResolutionService.resolveForOrchestratedExecute(
                        null, null, null, correlationId);
        KnowledgeSnapshotSelection snapshots = knowledgeRuntimeSnapshotSelector.select(null, null);
        return buildWithClarification(
                null,
                null,
                null,
                rawUserQuery,
                RuntimeOperationKind.LEGACY_HTTP,
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
        KnowledgeSnapshotSelection snapshots =
                knowledgeRuntimeSnapshotSelector.select(projectId, conversationId);
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
                originatingUserMessageId);

        ConversationMemoryExecutionResult mem = conversationMemoryStrategy.execute(base, preMemory);
        boolean attempted =
                mem.outcome() != ConversationMemoryOutcome.DISABLED_BY_CONFIG
                        && mem.outcome() != ConversationMemoryOutcome.NO_CONVERSATION_SCOPE;
        boolean historyLoaded = attempted && (mem.outcome() != ConversationMemoryOutcome.DISABLED_BY_CONFIG);
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
                preMemory,
                mem.finalPlanningInputText(),
                mem.slice(),
                mem.outcome(),
                mem.stageTraces(),
                attempted,
                historyLoaded,
                mem.condensationAttempted(),
                mem.condensationUsed(),
                mem.fallbackApplied(),
                base.pendingClarificationLoadedForTrace(),
                base.validPendingExistedAtLoad(),
                base.invalidPendingRecoveredThisTurn(),
                base.clarificationDisableReason(),
                base.originatingUserMessageId());
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
                ctx.originatingUserMessageId());
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
                ctx.originatingUserMessageId());
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
