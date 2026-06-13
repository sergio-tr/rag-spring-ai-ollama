package com.uniovi.rag.application.service.runtime;

import com.uniovi.rag.application.service.runtime.advisor.AdvisorPolicyResolver;
import com.uniovi.rag.application.service.runtime.advisor.AdvisorStrategy;
import com.uniovi.rag.application.service.runtime.clarification.ClarificationPolicyResolver;
import com.uniovi.rag.application.service.runtime.clarification.ClarificationStrategy;
import com.uniovi.rag.application.service.runtime.functioncalling.FunctionCallingPolicyResolver;
import com.uniovi.rag.application.service.runtime.functioncalling.FunctionCallingStrategy;
import com.uniovi.rag.application.service.runtime.judge.JudgeStrategy;
import com.uniovi.rag.application.service.runtime.query.QueryUnderstandingPipeline;
import com.uniovi.rag.application.service.runtime.reasoning.AnswerVerificationService;
import com.uniovi.rag.application.service.runtime.reasoning.StructuredAnswerPlanService;
import com.uniovi.rag.application.service.runtime.routing.AdaptiveRoutingStrategy;
import com.uniovi.rag.application.service.runtime.routing.DeterministicToolRoutingStrategy;
import com.uniovi.rag.application.service.runtime.tool.DeterministicToolStrategy;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.engine.KnowledgeSnapshotSelection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;

/** Covers chat snapshot degradation ({@link RagExecutionOrchestrator#selectExecutableWorkflow}). */
class RagExecutionOrchestratorSnapshotFallbackTest {

    private record OrchestratorHarness(RagExecutionOrchestrator orchestrator, DirectLlmWorkflow directLlm) {}

    private static OrchestratorHarness harness() {
        DirectLlmWorkflow direct = mock(DirectLlmWorkflow.class);
        WorkflowSelector workflowSelector = mock(WorkflowSelector.class);
        QueryUnderstandingPipeline qu = mock(QueryUnderstandingPipeline.class);
        ExecutionContextFactory factory = mock(ExecutionContextFactory.class);
        DeterministicToolStrategy tools = mock(DeterministicToolStrategy.class);
        FunctionCallingPolicyResolver fcPolicy = mock(FunctionCallingPolicyResolver.class);
        FunctionCallingStrategy fcStrategy = mock(FunctionCallingStrategy.class);
        AdvisorPolicyResolver advisorPolicy = mock(AdvisorPolicyResolver.class);
        AdvisorStrategy advisorStrategy = mock(AdvisorStrategy.class);
        ClarificationPolicyResolver clarificationPolicy = mock(ClarificationPolicyResolver.class);
        ClarificationStrategy clarificationStrategy = mock(ClarificationStrategy.class);
        AdaptiveRoutingStrategy routingStrategy = mock(AdaptiveRoutingStrategy.class);
        JudgeStrategy judgeStrategy = mock(JudgeStrategy.class);
        RagExecutionOrchestrator orchestrator =
                new RagExecutionOrchestrator(
                        workflowSelector,
                        direct,
                        qu,
                        factory,
                        tools,
                        fcPolicy,
                        fcStrategy,
                        advisorPolicy,
                        advisorStrategy,
                        clarificationPolicy,
                        clarificationStrategy,
                        routingStrategy,
                        mock(DeterministicToolRoutingStrategy.class),
                        mock(com.uniovi.rag.application.service.runtime.routing.FunctionCallingRoutingStrategy.class),
                        mock(com.uniovi.rag.application.service.runtime.routing.AdvisorRoutingStrategy.class),
                        judgeStrategy,
                        mock(StructuredAnswerPlanService.class),
                        mock(AnswerVerificationService.class),
                        mock(ObjectProvider.class));
        return new OrchestratorHarness(orchestrator, direct);
    }

    @Test
    void selectExecutableWorkflow_fallsBackToDirectLlmWhenSnapshotsMissingForDenseWorkflow() {
        OrchestratorHarness h = harness();

        ExecutionContext ctx = mock(ExecutionContext.class);
        when(ctx.knowledgeSnapshotSelection()).thenReturn(KnowledgeSnapshotSelection.empty());
        when(ctx.correlationId()).thenReturn("corr-x");

        ExecutionWorkflow dense = mock(ExecutionWorkflow.class);
        when(dense.workflowName()).thenReturn("ChunkDenseRagWorkflow");

        assertSame(h.directLlm(), h.orchestrator().selectExecutableWorkflow(ctx, dense));
    }

    @Test
    void selectExecutableWorkflow_keepsDenseWhenSnapshotsPresent() {
        OrchestratorHarness h = harness();

        UUID sid = UUID.randomUUID();
        KnowledgeSnapshotSelection sel =
                new KnowledgeSnapshotSelection(
                        List.of(sid), Optional.of(sid), Optional.empty(), Optional.of("ph"), Optional.empty(), Optional.empty());
        ExecutionContext ctx = mock(ExecutionContext.class);
        when(ctx.knowledgeSnapshotSelection()).thenReturn(sel);

        ExecutionWorkflow dense = mock(ExecutionWorkflow.class);
        when(dense.workflowName()).thenReturn("ChunkDenseRagWorkflow");

        assertSame(dense, h.orchestrator().selectExecutableWorkflow(ctx, dense));
    }

    @Test
    void selectExecutableWorkflow_keepsDirectWhenAlreadyDirect() {
        OrchestratorHarness h = harness();

        ExecutionContext ctx = mock(ExecutionContext.class);
        when(ctx.knowledgeSnapshotSelection()).thenReturn(KnowledgeSnapshotSelection.empty());

        assertSame(h.directLlm(), h.orchestrator().selectExecutableWorkflow(ctx, h.directLlm()));
    }
}
