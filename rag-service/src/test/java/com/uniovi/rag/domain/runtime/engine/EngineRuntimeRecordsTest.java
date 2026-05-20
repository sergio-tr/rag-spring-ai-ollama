package com.uniovi.rag.domain.runtime.engine;

import com.uniovi.rag.domain.config.runtime.ResolvedRuntimeConfig;
import com.uniovi.rag.domain.runtime.memory.ConversationMemoryOutcome;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalDiagnostics;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalMode;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRouteKind;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRoutingOutcome;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Covers compact constructors, normalization, and helpers on runtime engine records (wave 6.05).
 */
class EngineRuntimeRecordsTest {

    private static ExecutionContext baseChatContext(ResolvedRuntimeConfig resolved) {
        return new ExecutionContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "q",
                RuntimeOperationKind.CHAT_MESSAGE,
                resolved,
                "sys",
                KnowledgeSnapshotSelection.empty(),
                Optional.empty(),
                Optional.empty(),
                "corr",
                List.of(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                "",
                "",
                Optional.empty(),
                ConversationMemoryOutcome.DISABLED_BY_CONFIG,
                List.of(),
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                Optional.empty(),
                Optional.empty(),
                false,
                AdaptiveRoutingOutcome.DISABLED_BY_CONFIG,
                AdaptiveRouteKind.DIRECT_WORKFLOW_ROUTE,
                false,
                Optional.empty(),
                false,
                List.of());
    }

    @Test
    void executionContext_normalizesNullOptionalsAndCopiesLists() {
        ResolvedRuntimeConfig resolved = mock(ResolvedRuntimeConfig.class);
        UUID uid = UUID.randomUUID();
        ExecutionContext ctx =
                new ExecutionContext(
                        uid,
                        null,
                        null,
                        "q",
                        RuntimeOperationKind.STATELESS_HTTP,
                        resolved,
                        "sys",
                        KnowledgeSnapshotSelection.empty(),
                        null,
                        null,
                        "c1",
                        List.of("d1"),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        ConversationMemoryOutcome.DISABLED_BY_CONFIG,
                        null,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        null,
                        null,
                        false,
                        null,
                        null,
                        false,
                        null,
                        false,
                        null);

        assertThat(ctx.configHash()).isEmpty();
        assertThat(ctx.documentFilter()).containsExactly("d1");
        assertThat(ctx.preMemoryPlanningInputText()).isEmpty();
        assertThat(ctx.effectivePlanningInputText()).isEmpty();
        assertThat(ctx.memorySlice()).isEmpty();
        assertThat(ctx.memoryStageTraces()).isEmpty();
        assertThat(ctx.clarificationDisableReason()).isEmpty();
        assertThat(ctx.routingFallbackRouteKind()).isEmpty();
        assertThat(ctx.routingStageTraces()).isEmpty();
        assertThat(ctx.routingRouteKind()).isEqualTo(AdaptiveRouteKind.DIRECT_WORKFLOW_ROUTE);
        assertThat(ctx.routingOutcome()).isEqualTo(AdaptiveRoutingOutcome.DISABLED_BY_CONFIG);
        assertThat(ctx.stateless()).isTrue();
    }

    @Test
    void executionContext_minimalConstructor_defaultsRoutingFields() {
        ResolvedRuntimeConfig resolved = mock(ResolvedRuntimeConfig.class);
        UUID cid = UUID.randomUUID();
        ExecutionContext ctx =
                new ExecutionContext(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        cid,
                        "hi",
                        RuntimeOperationKind.CHAT_MESSAGE,
                        resolved,
                        "sys",
                        KnowledgeSnapshotSelection.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        "corr",
                        List.of(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        "",
                        "",
                        Optional.empty(),
                        ConversationMemoryOutcome.DISABLED_BY_CONFIG,
                        List.of(),
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        Optional.empty(),
                        Optional.empty());

        assertThat(ctx.routingAttempted()).isFalse();
        assertThat(ctx.routingOutcome()).isEqualTo(AdaptiveRoutingOutcome.DISABLED_BY_CONFIG);
        assertThat(ctx.routingWorkflowSelectorInvoked()).isFalse();
        assertThat(ctx.stateless()).isFalse();
    }

    @Test
    void executionStageTrace_rejectsBlankStageName() {
        assertThatThrownBy(() -> new ExecutionStageTrace("", 0L, ExecutionStageOutcome.SUCCESS, "m"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ExecutionStageTrace("  ", 0L, ExecutionStageOutcome.SUCCESS, "m"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void executionStageTrace_nullMessageBecomesEmpty() {
        ExecutionStageTrace t = new ExecutionStageTrace("s", 3L, ExecutionStageOutcome.FAILED, null);
        assertThat(t.message()).isEmpty();
    }

    @Test
    void executionTrace_normalizesNullOptionalsAndCopiesStages() {
        UUID sid = UUID.randomUUID();
        ExecutionTrace t =
                new ExecutionTrace(
                        List.of(new ExecutionStageTrace("a", 1L, ExecutionStageOutcome.SUCCESS, "ok")),
                        "wf",
                        true,
                        true,
                        List.of(sid),
                        Optional.<UUID>empty(),
                        Optional.<String>empty(),
                        "",
                        "",
                        "",
                        "",
                        "",
                        "",
                        false,
                        "",
                        false,
                        false,
                        false,
                        false,
                        false,
                        "",
                        "",
                        false,
                        "",
                        false,
                        "",
                        "",
                        "",
                        false,
                        "",
                        "",
                        false,
                        Optional.<RetrievalDiagnostics>empty(),
                        false,
                        false,
                        "",
                        "",
                        0,
                        0,
                        false,
                        "",
                        false,
                        false,
                        false,
                        "",
                        false,
                        "",
                        "",
                        false,
                        "",
                        false,
                        false,
                        "",
                        "",
                "",
                0,
                List.of(),
                "",
                0,
                false,
                "");

        assertThat(t.usedResolvedConfigSnapshotId()).isEmpty();
        assertThat(t.usedConfigHash()).isEmpty();
        assertThat(t.queryPlanVersion()).isEmpty();
        assertThat(t.retrievalDiagnostics()).isEmpty();
        assertThat(t.stages()).hasSize(1);
        assertThat(t.usedKnowledgeSnapshotIds()).containsExactly(sid);
    }

    @Test
    void executionTrace_placeholder_isStableEmptyShell() {
        ExecutionTrace p = ExecutionTrace.placeholder();
        assertThat(p.stages()).isEmpty();
        assertThat(p.workflowName()).isEmpty();
        assertThat(p.retrievalUsed()).isFalse();
    }

    @Test
    void knowledgeSnapshotSelection_emptyAndCopy() {
        KnowledgeSnapshotSelection k = KnowledgeSnapshotSelection.empty();
        assertThat(k.orderedSnapshotIds()).isEmpty();
        assertThat(k.projectSharedSnapshotId()).isEmpty();

        UUID a = UUID.randomUUID();
        KnowledgeSnapshotSelection k2 =
                new KnowledgeSnapshotSelection(
                        List.of(a), null, Optional.of(UUID.randomUUID()), null, null, null);
        assertThat(k2.orderedSnapshotIds()).containsExactly(a);
        assertThat(k2.projectSnapshotSignatureHash()).isEmpty();
    }

    @Test
    void ragExecutionResult_withPlaceholderTrace_andWithFinalTrace() {
        UUID snap = UUID.randomUUID();
        RagExecutionResult r =
                RagExecutionResult.withPlaceholderTrace(
                        "ans", "wf", true, false, List.of(snap), "tool", List.of());
        assertThat(r.answerText()).isEqualTo("ans");
        assertThat(r.usedKnowledgeSnapshotIds()).containsExactly(snap);
        assertThat(r.executionTrace()).isEqualTo(ExecutionTrace.placeholder());

        ExecutionTrace fin = ExecutionTrace.placeholder();
        RagExecutionResult r2 = r.withFinalTrace(fin);
        assertThat(r2.executionTrace()).isSameAs(fin);
    }

    @Test
    void ragExecutionResult_withDiagnosticsOverload() {
        RagExecutionResult r =
                RagExecutionResult.withPlaceholderTrace(
                        "a",
                        "w",
                        false,
                        false,
                        List.of(),
                        "t",
                        Optional.of(
                                new RetrievalDiagnostics(
                                        RetrievalMode.DENSE_ONLY,
                                        Optional.empty(),
                                        "snap",
                                        1,
                                        0,
                                        1,
                                        0,
                                        0,
                                        0,
                                        0,
                                        0,
                                        0,
                                        false,
                                        List.of(),
                                        List.of(),
                                        Optional.empty())),
                        List.of());
        assertThat(r.retrievalDiagnostics()).isPresent();
    }

    @Test
    void workflowId_andRuntimeOperationKind_values() {
        assertThat(WorkflowId.values()).contains(WorkflowId.DIRECT_LLM, WorkflowId.CHUNK_DENSE_METADATA);
        assertThat(RuntimeOperationKind.CHAT_MESSAGE.name()).isEqualTo("CHAT_MESSAGE");
        assertThat(ExecutionStageOutcome.FAILED).isNotEqualTo(ExecutionStageOutcome.SUCCESS);
    }

    @Test
    void executionContext_routingFlags_roundTrip() {
        ResolvedRuntimeConfig resolved = mock(ResolvedRuntimeConfig.class);
        ExecutionContext base = baseChatContext(resolved);
        ExecutionContext routed =
                new ExecutionContext(
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
                        true,
                        AdaptiveRoutingOutcome.PRIMARY_ROUTE_SELECTED,
                        AdaptiveRouteKind.RETRIEVAL_WORKFLOW_ROUTE,
                        true,
                        Optional.of(AdaptiveRouteKind.DIRECT_WORKFLOW_ROUTE),
                        true,
                        List.of(new ExecutionStageTrace("route", 2L, ExecutionStageOutcome.SUCCESS, "ok")));

        assertThat(routed.routingAttempted()).isTrue();
        assertThat(routed.routingFallbackApplied()).isTrue();
        assertThat(routed.routingFallbackRouteKind()).contains(AdaptiveRouteKind.DIRECT_WORKFLOW_ROUTE);
        assertThat(routed.routingStageTraces()).hasSize(1);
    }
}
