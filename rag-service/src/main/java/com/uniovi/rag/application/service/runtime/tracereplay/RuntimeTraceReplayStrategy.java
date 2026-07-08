package com.uniovi.rag.application.service.runtime.tracereplay;

import com.uniovi.rag.application.exception.RagServiceException;
import com.uniovi.rag.application.service.runtime.ExecutionWorkflow;
import com.uniovi.rag.application.service.runtime.memory.ConversationMemoryStrategy;
import com.uniovi.rag.application.service.runtime.query.QueryUnderstandingPipeline;
import com.uniovi.rag.application.service.runtime.tool.DeterministicToolExecutor;
import com.uniovi.rag.domain.runtime.RagExecutionContext;
import com.uniovi.rag.domain.runtime.RagExecutionContext;
import com.uniovi.rag.domain.runtime.RagExecutionContextHolder;
import com.uniovi.rag.domain.runtime.RagSnapshotContextHolder;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.engine.ExecutionTrace;
import com.uniovi.rag.domain.runtime.engine.KnowledgeSnapshotSelection;
import com.uniovi.rag.domain.runtime.engine.RagExecutionResult;
import com.uniovi.rag.domain.runtime.engine.RuntimeOperationKind;
import com.uniovi.rag.domain.runtime.memory.ConversationMemoryOutcome;
import com.uniovi.rag.domain.runtime.memory.ConversationMemoryExecutionResult;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRoutingOutcome;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolDecision;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolExecutionResult;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolKind;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolOutcome;
import com.uniovi.rag.domain.runtime.tool.ToolExecutionMode;
import com.uniovi.rag.domain.runtime.tracereplay.RuntimeTraceReplayOutcome;
import com.uniovi.rag.domain.runtime.tracereplay.RuntimeTraceReplayResult;
import com.uniovi.rag.interfaces.rest.dto.RuntimeExecutionTraceDetailDto;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Executes pinned replay for one supported trace (no adaptive routing, clarification, judge, FC, or advisor).
 */
@Service
public class RuntimeTraceReplayStrategy {

    private final QueryUnderstandingPipeline queryUnderstandingPipeline;
    private final ConversationMemoryStrategy conversationMemoryStrategy;
    private final DeterministicToolExecutor deterministicToolExecutor;
    private final Map<String, ExecutionWorkflow> workflowsByName;

    public RuntimeTraceReplayStrategy(
            QueryUnderstandingPipeline queryUnderstandingPipeline,
            ConversationMemoryStrategy conversationMemoryStrategy,
            DeterministicToolExecutor deterministicToolExecutor,
            List<ExecutionWorkflow> workflows) {
        this.queryUnderstandingPipeline = queryUnderstandingPipeline;
        this.conversationMemoryStrategy = conversationMemoryStrategy;
        this.deterministicToolExecutor = deterministicToolExecutor;
        this.workflowsByName =
                workflows.stream()
                        .collect(
                                Collectors.toMap(
                                        ExecutionWorkflow::workflowName,
                                        w -> w,
                                        (a, b) -> a));
    }

    public RuntimeTraceReplayResult execute(
            RuntimeExecutionTraceDetailDto trace,
            RuntimeTraceReplayInputLoader.ReplayLoadedInputs inputs,
            PinnedReplayExecutionSpec pin) {
        try {
            return switch (pin.routeFamily()) {
                case DIRECT_WORKFLOW_ROUTE, RETRIEVAL_WORKFLOW_ROUTE -> runWorkflow(trace, inputs, pin);
                case DETERMINISTIC_TOOL_ROUTE -> runDeterministicTool(trace, inputs, pin);
                default ->
                        RuntimeTraceReplayResult.unsupported(
                                RuntimeTraceReplayOutcome.UNSUPPORTED_ROUTE_FAMILY, Optional.of("route=" + pin.routeFamily()));
            };
        } catch (RagServiceException e) {
            return RuntimeTraceReplayResult.failedSafe(e.getMessage());
        } catch (RuntimeException e) {
            return RuntimeTraceReplayResult.failedSafe(e.getClass().getSimpleName());
        }
    }

    private RuntimeTraceReplayResult runWorkflow(
            RuntimeExecutionTraceDetailDto trace,
            RuntimeTraceReplayInputLoader.ReplayLoadedInputs inputs,
            PinnedReplayExecutionSpec pin) {
        ExecutionWorkflow workflow = workflowsByName.get(pin.workflowName());
        if (workflow == null) {
            return RuntimeTraceReplayResult.failedSafe("unknown_workflow=" + pin.workflowName());
        }
        if (requiresKnowledgeSnapshots(pin.workflowName()) && pin.knowledgeSnapshotIds().isEmpty()) {
            return RuntimeTraceReplayResult.failedSafe("knowledge_snapshots_required");
        }

        ExecutionContext ctxAfterQu = buildContextAndRunQu(trace, inputs, pin);
        RagExecutionContextHolder.set(toRagExecutionContextHolder(ctxAfterQu));
        RagSnapshotContextHolder.set(ctxAfterQu.knowledgeSnapshotSelection().orderedSnapshotIds());
        try {
            RagExecutionResult partial = workflow.execute(ctxAfterQu);
            return RuntimeTraceReplayResult.success(
                    partial.answerText(), partial.executionTrace() != null ? partial.executionTrace() : ExecutionTrace.placeholder());
        } finally {
            RagExecutionContextHolder.clear();
            RagSnapshotContextHolder.clear();
        }
    }

    private RuntimeTraceReplayResult runDeterministicTool(
            RuntimeExecutionTraceDetailDto trace,
            RuntimeTraceReplayInputLoader.ReplayLoadedInputs inputs,
            PinnedReplayExecutionSpec pin) {
        final DeterministicToolKind kind;
        try {
            kind = DeterministicToolKind.valueOf(pin.deterministicToolKind());
        } catch (IllegalArgumentException e) {
            return RuntimeTraceReplayResult.failedSafe("unknown_tool_kind=" + pin.deterministicToolKind());
        }

        ExecutionContext ctxAfterQu = buildContextAndRunQu(trace, inputs, pin);
        QueryPlan plan = ctxAfterQu.queryPlan().orElseThrow(() -> new IllegalStateException("query plan missing"));
        DeterministicToolDecision decision =
                new DeterministicToolDecision(
                        ToolExecutionMode.ENABLED,
                        DeterministicToolOutcome.SELECTED,
                        true,
                        Optional.of(kind),
                        List.of("replay_pin=" + kind),
                        normalizedInputs(plan),
                        Optional.empty(),
                        Optional.empty());
        DeterministicToolExecutionResult toolResult = deterministicToolExecutor.execute(decision, ctxAfterQu, plan);
        if (toolResult.outcome() == DeterministicToolOutcome.EXECUTED_SUCCESS && toolResult.success()) {
            return RuntimeTraceReplayResult.success(toolResult.answerText(), ExecutionTrace.placeholder());
        }
        return RuntimeTraceReplayResult.failedSafe(
                "tool_outcome=" + toolResult.outcome() + " success=" + toolResult.success());
    }

    /**
     * P18: memory (historical window) then exactly one QU invocation.
     */
    private ExecutionContext buildContextAndRunQu(
            RuntimeExecutionTraceDetailDto trace,
            RuntimeTraceReplayInputLoader.ReplayLoadedInputs inputs,
            PinnedReplayExecutionSpec pin) {
        String userQuery = inputs.userMessage().getContent() != null ? inputs.userMessage().getContent() : "";
        String correlationId =
                trace.correlationId() != null && !trace.correlationId().isBlank()
                        ? trace.correlationId()
                        : "replay-" + trace.id();

        KnowledgeSnapshotSelection knowledge =
                pin.knowledgeSnapshotIds().isEmpty()
                        ? KnowledgeSnapshotSelection.empty()
                        : new KnowledgeSnapshotSelection(
                                pin.knowledgeSnapshotIds(),
                                Optional.empty(),
                                Optional.empty(),
                                Optional.empty(),
                                Optional.empty(),
                                Optional.empty());

        String configHash = trace.configHash() != null ? trace.configHash() : "";

        ExecutionContext baseBeforeMemory =
                new ExecutionContext(
                        trace.userId(),
                        trace.projectId(),
                        trace.conversationId(),
                        userQuery,
                        RuntimeOperationKind.CHAT_MESSAGE,
                        inputs.resolved(),
                        inputs.resolved().effectiveSystemPrompt(),
                        knowledge,
                        Optional.of(configHash),
                        Optional.ofNullable(trace.resolvedConfigSnapshotId()),
                        correlationId,
                        inputs.documentFilter(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        userQuery,
                        userQuery,
                        Optional.empty(),
                        inputs.resolved().toRagConfig().memoryEnabled() && trace.conversationId() != null
                                ? ConversationMemoryOutcome.NO_HISTORY_AVAILABLE
                                : ConversationMemoryOutcome.DISABLED_BY_CONFIG,
                        List.of(),
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        Optional.of("replay_trace_replay"),
                        Optional.ofNullable(trace.messageId()),
                        false,
                        AdaptiveRoutingOutcome.DISABLED_BY_CONFIG,
                        pin.routeFamily(),
                        false,
                        Optional.empty(),
                        false,
                        List.of());

        ConversationMemoryExecutionResult mem =
                conversationMemoryStrategy.executeWithEligibleHistory(
                        baseBeforeMemory, userQuery, inputs.memoryEligibleTurns());

        boolean attempted =
                mem.outcome() != ConversationMemoryOutcome.DISABLED_BY_CONFIG
                        && mem.outcome() != ConversationMemoryOutcome.NO_CONVERSATION_SCOPE;
        boolean historyLoaded = attempted && (mem.outcome() != ConversationMemoryOutcome.DISABLED_BY_CONFIG);

        ExecutionContext afterMemory =
                new ExecutionContext(
                        baseBeforeMemory.userId(),
                        baseBeforeMemory.projectId(),
                        baseBeforeMemory.conversationId(),
                        baseBeforeMemory.userQuery(),
                        baseBeforeMemory.operationKind(),
                        baseBeforeMemory.resolved(),
                        baseBeforeMemory.effectiveSystemPrompt(),
                        baseBeforeMemory.knowledgeSnapshotSelection(),
                        baseBeforeMemory.configHash(),
                        baseBeforeMemory.pinnedResolvedConfigSnapshotId(),
                        baseBeforeMemory.correlationId(),
                        baseBeforeMemory.documentFilter(),
                        baseBeforeMemory.chatModelOverride(),
                        baseBeforeMemory.queryPlan(),
                        baseBeforeMemory.advisorPackedContextSet(),
                        baseBeforeMemory.structuredAnswerPlan(),
                        userQuery,
                        mem.finalPlanningInputText(),
                        mem.slice(),
                        mem.outcome(),
                        mem.stageTraces(),
                        attempted,
                        historyLoaded,
                        mem.condensationAttempted(),
                        mem.condensationUsed(),
                        mem.fallbackApplied(),
                        baseBeforeMemory.pendingClarificationLoadedForTrace(),
                        baseBeforeMemory.validPendingExistedAtLoad(),
                        baseBeforeMemory.invalidPendingRecoveredThisTurn(),
                        baseBeforeMemory.clarificationDisableReason(),
                        baseBeforeMemory.originatingUserMessageId(),
                        false,
                        AdaptiveRoutingOutcome.DISABLED_BY_CONFIG,
                        pin.routeFamily(),
                        false,
                        Optional.empty(),
                        false,
                        List.of());

        QueryPlan plan = queryUnderstandingPipeline.buildPlan(afterMemory);
        return attachQueryPlan(afterMemory, plan);
    }

    private static ExecutionContext attachQueryPlan(ExecutionContext ctx, QueryPlan plan) {
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
                ctx.advisorPackedContextSet(),
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

    private static RagExecutionContext toRagExecutionContextHolder(ExecutionContext ctx) {
        return RagExecutionContext.fromEngineContext(ctx);
    }

    private static boolean requiresKnowledgeSnapshots(String workflowName) {
        return "FullCorpusWorkflow".equals(workflowName)
                || "DocumentDenseRagWorkflow".equals(workflowName)
                || "ChunkDenseRagWorkflow".equals(workflowName)
                || "ChunkDenseMetadataWorkflow".equals(workflowName);
    }

    private static Map<String, String> normalizedInputs(QueryPlan plan) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("queryText", plan.rewrittenQueryText());
        m.put("correlationId", plan.correlationId());
        m.put("intent", plan.queryIntent().name());
        for (var e : plan.slots().entrySet()) {
            m.put("slots." + e.getKey(), e.getValue());
        }
        var ner = plan.entityExtractionResult();
        if (!ner.dates().isEmpty()) {
            m.put("entities.dates", String.join(",", ner.dates()));
        }
        if (!ner.people().isEmpty()) {
            m.put("entities.people", String.join(",", ner.people()));
        }
        if (!ner.locations().isEmpty()) {
            m.put("entities.locations", String.join(",", ner.locations()));
        }
        if (!ner.topics().isEmpty()) {
            m.put("entities.topics", String.join(",", ner.topics()));
        }
        if (!ner.organizations().isEmpty()) {
            m.put("entities.organizations", String.join(",", ner.organizations()));
        }
        return m;
    }
}
