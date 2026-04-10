package com.uniovi.rag.application.service.runtime;

import com.uniovi.rag.application.service.runtime.advisor.AdvisorPolicyResolver;
import com.uniovi.rag.application.service.runtime.advisor.AdvisorStrategy;
import com.uniovi.rag.application.service.runtime.clarification.ClarificationPolicyResolver;
import com.uniovi.rag.application.service.runtime.clarification.ClarificationQuestionGenerator;
import com.uniovi.rag.application.service.runtime.clarification.ClarificationStrategy;
import com.uniovi.rag.application.service.runtime.functioncalling.FunctionCallingPolicyResolver;
import com.uniovi.rag.application.service.runtime.functioncalling.FunctionCallingStrategy;
import com.uniovi.rag.application.service.runtime.query.QueryUnderstandingPipeline;
import com.uniovi.rag.application.service.runtime.tool.DeterministicToolStrategy;
import com.uniovi.rag.application.port.PendingClarificationStore;
import com.uniovi.rag.domain.config.capability.CapabilitySet;
import com.uniovi.rag.domain.config.indexing.ReindexImpact;
import com.uniovi.rag.domain.config.prompt.SystemPromptLayers;
import com.uniovi.rag.domain.config.runtime.ConfigProvenance;
import com.uniovi.rag.domain.config.runtime.ResolvedRuntimeConfig;
import com.uniovi.rag.domain.config.validation.CompatibilityResult;
import com.uniovi.rag.domain.knowledge.MaterializationStrategy;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.domain.runtime.advisor.AdvisorDecision;
import com.uniovi.rag.domain.runtime.advisor.AdvisorMode;
import com.uniovi.rag.domain.runtime.clarification.ClarificationDecision;
import com.uniovi.rag.domain.runtime.clarification.ClarificationOutcome;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.engine.KnowledgeSnapshotSelection;
import com.uniovi.rag.domain.runtime.engine.RuntimeOperationKind;
import com.uniovi.rag.domain.runtime.memory.ConversationMemoryOutcome;
import com.uniovi.rag.domain.runtime.query.AmbiguityAssessment;
import com.uniovi.rag.domain.runtime.query.AmbiguityStatus;
import com.uniovi.rag.domain.runtime.query.ClassifierStatus;
import com.uniovi.rag.domain.runtime.query.EntityExtractionResult;
import com.uniovi.rag.domain.runtime.query.ExpectedAnswerShape;
import com.uniovi.rag.domain.runtime.query.QueryIntent;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.query.StructuredRewriteResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagExecutionOrchestratorClarificationTest {

    @Test
    void askedClarification_shortCircuitsLaterStages_andSingleQu() {
        RagConfig rag = ragClarificationOn();
        ExecutionContext in = ctx(rag);
        QueryPlan plan = planMissingDate();

        WorkflowSelector workflowSelector = mock(WorkflowSelector.class);
        QueryUnderstandingPipeline qu = mock(QueryUnderstandingPipeline.class);
        ExecutionContextFactory factory = mock(ExecutionContextFactory.class);
        DeterministicToolStrategy tools = mock(DeterministicToolStrategy.class);
        FunctionCallingPolicyResolver fcPolicy = mock(FunctionCallingPolicyResolver.class);
        FunctionCallingStrategy fcStrategy = mock(FunctionCallingStrategy.class);
        AdvisorPolicyResolver advisorPolicy = mock(AdvisorPolicyResolver.class);
        AdvisorStrategy advisorStrategy = mock(AdvisorStrategy.class);

        PendingClarificationStore pendingStore = mock(PendingClarificationStore.class);
        ClarificationStrategy clarificationStrategy = new ClarificationStrategy(pendingStore);
        ClarificationPolicyResolver clarificationPolicyResolver =
                new ClarificationPolicyResolver(new ClarificationQuestionGenerator());

        when(qu.buildPlan(in)).thenReturn(plan);
        when(factory.attachQueryPlan(in, plan)).thenAnswer(inv -> withPlan(inv.getArgument(0), plan));

        RagExecutionOrchestrator orchestrator =
                new RagExecutionOrchestrator(
                        workflowSelector,
                        qu,
                        factory,
                        tools,
                        fcPolicy,
                        fcStrategy,
                        advisorPolicy,
                        advisorStrategy,
                        clarificationPolicyResolver,
                        clarificationStrategy);

        var out = orchestrator.execute(in);

        assertThat(out.workflowName()).isEqualTo("clarification");
        assertThat(out.answerText()).isEqualTo("Which date or meeting are you referring to?");
        assertThat(out.executionTrace().clarificationQuestionAsked()).isTrue();
        assertThat(out.executionTrace().clarificationOutcome()).isEqualTo("ASKED_CLARIFICATION");

        verify(qu).buildPlan(in);
        verify(workflowSelector, never()).select(any());
        verify(tools, never()).tryExecute(any(), any(), any());
        verify(fcPolicy, never()).resolve(any(), any(), any());
        verify(fcStrategy, never()).tryExecute(any(), any(), any(), any());
        verify(advisorPolicy, never()).resolve(any(), any(), any());
        verify(advisorStrategy, never()).execute(any(), any(), any(), any());
        verify(pendingStore).saveReplace(any(), any());
    }

    @Test
    void resolvedFromPending_invokesClearBeforeWorkflow() {
        RagConfig rag = ragClarificationOn();
        UUID uid = UUID.randomUUID();
        UUID pid = UUID.randomUUID();
        UUID cid = UUID.randomUUID();
        ResolvedRuntimeConfig resolved =
                new ResolvedRuntimeConfig(
                        rag,
                        CapabilitySet.fromRagConfig(rag),
                        CompatibilityResult.ok(),
                        ReindexImpact.none(),
                        new SystemPromptLayers("", "", "", ""),
                        "sys",
                        new ConfigProvenance(null, null, null, List.of(), null, null),
                        rag);
        ExecutionContext merged =
                new ExecutionContext(
                        uid,
                        pid,
                        cid,
                        "ans",
                        RuntimeOperationKind.CHAT_MESSAGE,
                        resolved,
                        "sys",
                        KnowledgeSnapshotSelection.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        "corr",
                        List.of("all"),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        "ans",
                        "ans",
                        Optional.empty(),
                        ConversationMemoryOutcome.DISABLED_BY_CONFIG,
                        List.of(),
                        false,
                        false,
                        false,
                        false,
                        false,
                        true,
                        true,
                        false,
                        Optional.empty(),
                        Optional.empty());

        QueryPlan plan =
                new QueryPlan(
                        QueryPlan.VERSION_P11_QU_CLARIFICATION_CORE_V1,
                        "ans",
                        "ans",
                        "ans",
                        "rw",
                        "lbl",
                        Optional.empty(),
                        ClassifierStatus.OK,
                        QueryIntent.UNKNOWN,
                        Map.of(),
                        List.of(),
                        List.of(),
                        EntityExtractionResult.emptyWithNote(""),
                        StructuredRewriteResult.identityDisabled("ans", ""),
                        ExpectedAnswerShape.UNKNOWN,
                        new AmbiguityAssessment(AmbiguityStatus.SUFFICIENT, List.of(), List.of()),
                        "corr",
                        "",
                        List.of());

        WorkflowSelector workflowSelector = mock(WorkflowSelector.class);
        QueryUnderstandingPipeline qu = mock(QueryUnderstandingPipeline.class);
        ExecutionContextFactory factory = mock(ExecutionContextFactory.class);
        DeterministicToolStrategy tools = mock(DeterministicToolStrategy.class);
        FunctionCallingPolicyResolver fcPolicy = mock(FunctionCallingPolicyResolver.class);
        FunctionCallingStrategy fcStrategy = mock(FunctionCallingStrategy.class);
        AdvisorPolicyResolver advisorPolicy = mock(AdvisorPolicyResolver.class);
        AdvisorStrategy advisorStrategy = mock(AdvisorStrategy.class);
        ClarificationPolicyResolver clarificationPolicyResolver = mock(ClarificationPolicyResolver.class);
        ClarificationStrategy clarificationStrategy = mock(ClarificationStrategy.class);

        when(clarificationPolicyResolver.resolve(any(), any()))
                .thenReturn(
                        new ClarificationDecision(
                                false, ClarificationOutcome.RESOLVED_FROM_PENDING, null, ""));

        when(qu.buildPlan(merged)).thenReturn(plan);
        when(factory.attachQueryPlan(merged, plan)).thenAnswer(inv -> withPlan(inv.getArgument(0), plan));

        ExecutionWorkflow wf = mock(ExecutionWorkflow.class);
        when(wf.workflowName()).thenReturn("DirectLlmWorkflow");
        when(wf.execute(any()))
                .thenReturn(
                        com.uniovi.rag.domain.runtime.engine.RagExecutionResult.withPlaceholderTrace(
                                "ok", "DirectLlmWorkflow", false, false, List.of(), "none", List.of()));
        when(workflowSelector.select(any())).thenReturn(wf);
        when(tools.tryExecute(any(), any(), any()))
                .thenReturn(
                        com.uniovi.rag.domain.runtime.tool.DeterministicToolExecutionResult.skipped(
                                com.uniovi.rag.domain.runtime.tool.DeterministicToolOutcome.NOT_APPLICABLE,
                                List.of(),
                                Optional.empty()));
        when(advisorPolicy.resolve(any(), any(), any()))
                .thenReturn(
                        new AdvisorDecision(
                                AdvisorMode.ENABLED, false, List.of(), "", List.of(), Optional.empty()));

        RagExecutionOrchestrator orchestrator =
                new RagExecutionOrchestrator(
                        workflowSelector,
                        qu,
                        factory,
                        tools,
                        fcPolicy,
                        fcStrategy,
                        advisorPolicy,
                        advisorStrategy,
                        clarificationPolicyResolver,
                        clarificationStrategy);

        orchestrator.execute(merged);

        verify(clarificationStrategy).clearAfterResolved(cid);
        verify(workflowSelector).select(any());
    }

    private static RagConfig ragClarificationOn() {
        return new RagConfig(
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                true,
                true,
                true,
                false,
                5,
                0.2,
                "l",
                "e",
                "c",
                "r",
                false,
                RagConfig.DEFAULT_NAIVE_FULL_CORPUS_MAX_CHARS,
                RagConfig.DEFAULT_ADVANCED_RETRIEVAL_MAX_CONTEXT_CHARS,
                MaterializationStrategy.CHUNK_LEVEL);
    }

    private static ExecutionContext ctx(RagConfig rag) {
        ResolvedRuntimeConfig resolved =
                new ResolvedRuntimeConfig(
                        rag,
                        CapabilitySet.fromRagConfig(rag),
                        CompatibilityResult.ok(),
                        ReindexImpact.none(),
                        new SystemPromptLayers("", "", "", ""),
                        "sys",
                        new ConfigProvenance(null, null, null, List.of(), null, null),
                        rag);
        UUID cid = UUID.randomUUID();
        return new ExecutionContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                cid,
                "when is it",
                RuntimeOperationKind.CHAT_MESSAGE,
                resolved,
                "sys",
                KnowledgeSnapshotSelection.empty(),
                Optional.empty(),
                Optional.empty(),
                "corr",
                List.of("all"),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                "when is it",
                "when is it",
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
    }

    private static ExecutionContext withPlan(ExecutionContext ctx, QueryPlan plan) {
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

    private static QueryPlan planMissingDate() {
        return new QueryPlan(
                QueryPlan.VERSION_P11_QU_CLARIFICATION_CORE_V1,
                "when is it",
                "when is it",
                "when is it",
                "rw",
                "lbl",
                Optional.empty(),
                ClassifierStatus.OK,
                QueryIntent.UNKNOWN,
                Map.of(),
                List.of(),
                List.of(),
                EntityExtractionResult.emptyWithNote(""),
                StructuredRewriteResult.identityDisabled("when is it", ""),
                ExpectedAnswerShape.UNKNOWN,
                new AmbiguityAssessment(
                        AmbiguityStatus.MISSING_INFORMATION, List.of(), List.of("meeting date")),
                "corr",
                "",
                List.of());
    }
}
