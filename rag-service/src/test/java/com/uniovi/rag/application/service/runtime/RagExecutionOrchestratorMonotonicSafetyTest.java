package com.uniovi.rag.application.service.runtime;
import com.uniovi.rag.testsupport.ConversationRecallGuardTestSupport;

import com.uniovi.rag.application.service.runtime.advisor.AdvisorPolicyResolver;
import com.uniovi.rag.application.service.runtime.advisor.AdvisorStrategy;
import com.uniovi.rag.application.service.runtime.clarification.ClarificationPolicyResolver;
import com.uniovi.rag.application.service.runtime.clarification.ClarificationStrategy;
import com.uniovi.rag.application.service.runtime.functioncalling.FunctionCallingPolicyResolver;
import com.uniovi.rag.application.service.runtime.functioncalling.FunctionCallingStrategy;
import com.uniovi.rag.application.service.runtime.routing.DeterministicToolRoutingPolicy;
import com.uniovi.rag.application.service.runtime.routing.DeterministicToolRoutingStrategy;
import com.uniovi.rag.application.service.runtime.routing.FunctionCallingRoutingPolicy;
import com.uniovi.rag.application.service.runtime.routing.FunctionCallingRoutingStrategy;
import com.uniovi.rag.application.service.runtime.routing.RouteExecutionGateBuilder;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRouteKind;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRoutingExecutionResult;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRoutingOutcome;
import com.uniovi.rag.domain.runtime.routing.RouteExecutionGate;
import com.uniovi.rag.application.service.runtime.judge.JudgeStrategy;
import com.uniovi.rag.application.service.runtime.query.QueryUnderstandingPipeline;
import com.uniovi.rag.application.service.runtime.reasoning.AnswerVerificationService;
import com.uniovi.rag.application.service.runtime.reasoning.StructuredAnswerPlanService;
import com.uniovi.rag.application.service.runtime.routing.AdaptiveRoutingStrategy;
import com.uniovi.rag.application.service.runtime.routing.AdvisorRoutingStrategy;
import com.uniovi.rag.domain.runtime.functioncalling.FunctionCallingDecision;
import com.uniovi.rag.domain.runtime.functioncalling.FunctionCallingExecutionResult;
import com.uniovi.rag.domain.runtime.functioncalling.FunctionCallingMode;
import com.uniovi.rag.domain.runtime.functioncalling.FunctionCallingOutcome;
import com.uniovi.rag.application.service.evaluation.preset.CampaignParentOutcome;
import com.uniovi.rag.application.service.evaluation.preset.LabBenchmarkExecutionContext;
import com.uniovi.rag.application.service.runtime.routing.safety.IntegratedParentCampaignOutcomeResolver;
import com.uniovi.rag.application.service.runtime.routing.safety.ParentFinalAnswerSources;
import com.uniovi.rag.application.service.runtime.routing.safety.MonotonicRouteSafetyService;
import com.uniovi.rag.application.service.runtime.routing.safety.MonotonicSafetyTelemetrySupport;
import com.uniovi.rag.application.service.runtime.routing.safety.RouteCandidateConstraintValidator;
import com.uniovi.rag.application.service.runtime.routing.safety.RouteCandidateValidationResult;
import com.uniovi.rag.application.service.runtime.ChatExecutionTelemetryMapper;
import com.uniovi.rag.application.service.runtime.tool.DeterministicToolStrategy;
import com.uniovi.rag.domain.config.capability.CapabilitySet;
import com.uniovi.rag.domain.config.indexing.ReindexImpact;
import com.uniovi.rag.domain.config.prompt.SystemPromptLayers;
import com.uniovi.rag.domain.config.runtime.ConfigProvenance;
import com.uniovi.rag.domain.config.runtime.ResolvedRuntimeConfig;
import com.uniovi.rag.domain.config.validation.CompatibilityResult;
import com.uniovi.rag.domain.knowledge.MaterializationStrategy;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.domain.runtime.engine.RagExecutionResult;
import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.domain.runtime.clarification.ClarificationDecision;
import com.uniovi.rag.domain.runtime.clarification.ClarificationOutcome;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.engine.ExecutionStageTrace;
import com.uniovi.rag.domain.runtime.engine.ExecutionTrace;
import com.uniovi.rag.domain.runtime.engine.KnowledgeSnapshotSelection;
import com.uniovi.rag.domain.runtime.engine.RuntimeOperationKind;
import com.uniovi.rag.domain.runtime.judge.JudgeExecutionResult;
import com.uniovi.rag.domain.runtime.judge.JudgeOutcome;
import com.uniovi.rag.domain.runtime.memory.ConversationMemoryOutcome;
import com.uniovi.rag.domain.runtime.query.AmbiguityAssessment;
import com.uniovi.rag.domain.runtime.query.AmbiguityStatus;
import com.uniovi.rag.domain.runtime.query.ClassifierStatus;
import com.uniovi.rag.domain.runtime.query.EntityExtractionResult;
import com.uniovi.rag.domain.runtime.query.ExpectedAnswerShape;
import com.uniovi.rag.domain.runtime.query.QueryIntent;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.query.StructuredRewriteResult;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolExecutionResult;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolKind;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolOutcome;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RagExecutionOrchestratorMonotonicSafetyTest {

    private static final DeterministicToolRoutingStrategy DETERMINISTIC_TOOL_ROUTING =
            new DeterministicToolRoutingStrategy(
                    new DeterministicToolRoutingPolicy(), new RouteExecutionGateBuilder());
    private static final FunctionCallingRoutingStrategy FUNCTION_CALLING_ROUTING =
            new FunctionCallingRoutingStrategy(
                    new FunctionCallingRoutingPolicy(), new RouteExecutionGateBuilder());
    private static final String RAG_059_P7_ANSWER =
            "La reunión celebrada el 25 de agosto de 2025 habló sobre videovigilancia y tuvo más de 18 asistentes.";

    @AfterEach
    void tearDownLabBenchmarkContext() {
        LabBenchmarkExecutionContext.clear();
    }

    @Test
    void p7_rejectsPhaseCGasLeakToolAnswerWithRealSafetyGateAndFallsBack() {
        String query = "¿Qué se comentó respecto a la fuga de gas?";
        QueryPlan gasLeakPlan = queryPlan(query, QueryType.FIND_PARAGRAPH);
        ExecutionContext ctx = ctx(ragP7());
        ExecutionWorkflow workflow = mock(ExecutionWorkflow.class);
        when(workflow.workflowName()).thenReturn("ChunkDenseMetadataWorkflow");
        when(workflow.execute(any()))
                .thenReturn(
                        RagExecutionResult.withPlaceholderTrace(
                                "No se comentó respecto a la fuga de gas.",
                                "ChunkDenseMetadataWorkflow",
                                true,
                                false,
                                ctx.knowledgeSnapshotSelection().orderedSnapshotIds(),
                                "none",
                                List.of()));

        WorkflowSelector workflowSelector = mock(WorkflowSelector.class);
        when(workflowSelector.select(any())).thenReturn(workflow);

        String unsafeToolAnswer =
                "Se menciona la posibilidad de instalar cámaras de seguridad en las entradas del edificio para atender"
                        + " inquietudes de los vecinos relacionadas con la fuga de gas. No se proporciona información"
                        + " adicional sobre el comentario específico sobre la fuga de gas en los minutos. Se queda"
                        + " pendiente de estudio la instalación de las cámaras.";
        DeterministicToolStrategy tools = mock(DeterministicToolStrategy.class);
        when(tools.tryExecute(any(), any()))
                .thenReturn(
                        new DeterministicToolExecutionResult(
                                Optional.of(DeterministicToolKind.FIND_PARAGRAPH_TOOL),
                                DeterministicToolOutcome.EXECUTED_SUCCESS,
                                true,
                                unsafeToolAnswer,
                                Map.of(),
                                List.of()));

        MonotonicRouteSafetyService safety =
                new MonotonicRouteSafetyService(new RouteCandidateConstraintValidator());
        RagExecutionOrchestrator orchestrator =
                orchestratorWithPlan(workflowSelector, tools, safety, gasLeakPlan, false, false);

        var out = orchestrator.execute(ctx);
        assertThat(out.answerText()).isEqualTo("No se comentó respecto a la fuga de gas.");
        assertThat(out.executionTrace().routingFallbackApplied()).isTrue();
        assertThat(out.answerText()).doesNotContain("cámaras de seguridad");
    }

    @Test
    void p7_rejectsRag058UnsafeFilterListToolAndFallsBackToParentWorkflow() {
        String query =
                "Dime qué actas mencionan el ascensor y fueron presididas por Juan Pérez Gutiérrez.";
        QueryPlan rag058Plan = queryPlan(query, QueryType.FILTER_AND_LIST);
        ExecutionContext ctx = ctx(ragP7());
        ExecutionWorkflow workflow = mock(ExecutionWorkflow.class);
        when(workflow.workflowName()).thenReturn("ChunkDenseMetadataWorkflow");
        when(workflow.execute(any()))
                .thenReturn(
                        RagExecutionResult.withPlaceholderTrace(
                                "El acta de la reunión del 24 de febrero de 2025 menciona el ascensor.",
                                "ChunkDenseMetadataWorkflow",
                                true,
                                false,
                                ctx.knowledgeSnapshotSelection().orderedSnapshotIds(),
                                "none",
                                List.of()));

        WorkflowSelector workflowSelector = mock(WorkflowSelector.class);
        when(workflowSelector.select(any())).thenReturn(workflow);

        String unsafeToolAnswer =
                "Las actas mencionadas que incluyen el ascensor fueron las propuestas para la renovación del"
                        + " portal del edificio. Estas actas fueron presididas por Juan Pérez Gutiérrez.";
        DeterministicToolStrategy tools = mock(DeterministicToolStrategy.class);
        when(tools.tryExecute(any(), any()))
                .thenReturn(
                        new DeterministicToolExecutionResult(
                                Optional.of(DeterministicToolKind.FILTER_AND_LIST_TOOL),
                                DeterministicToolOutcome.EXECUTED_SUCCESS,
                                true,
                                unsafeToolAnswer,
                                Map.of(),
                                List.of()));

        MonotonicRouteSafetyService safety =
                new MonotonicRouteSafetyService(new RouteCandidateConstraintValidator());
        RagExecutionOrchestrator orchestrator =
                orchestratorWithPlan(workflowSelector, tools, safety, rag058Plan, false, false);

        var out = orchestrator.execute(ctx);
        assertThat(out.answerText())
                .isEqualTo("El acta de la reunión del 24 de febrero de 2025 menciona el ascensor.");
        assertThat(out.executionTrace().routingFallbackApplied()).isTrue();
        assertThat(out.answerText()).doesNotContain("renovación del portal");
    }

    @Test
    void p7_rejectsUnsafeToolAnswerAndFallsBackToWorkflow() {
        QueryPlan plan = plan();
        ExecutionContext ctx = ctx(ragP7());
        ExecutionWorkflow workflow = mock(ExecutionWorkflow.class);
        when(workflow.workflowName()).thenReturn("ChunkDenseRagWorkflow");
        when(workflow.execute(any()))
                .thenReturn(
                        RagExecutionResult.withPlaceholderTrace(
                                "retrieval-safe-answer",
                                "ChunkDenseRagWorkflow",
                                true,
                                false,
                                ctx.knowledgeSnapshotSelection().orderedSnapshotIds(),
                                "none",
                                List.of()));

        WorkflowSelector workflowSelector = mock(WorkflowSelector.class);
        when(workflowSelector.select(any())).thenReturn(workflow);

        DeterministicToolStrategy tools = mock(DeterministicToolStrategy.class);
        when(tools.tryExecute(any(), any()))
                .thenReturn(
                        new DeterministicToolExecutionResult(
                                Optional.of(DeterministicToolKind.BOOLEAN_QUERY_TOOL),
                                DeterministicToolOutcome.EXECUTED_SUCCESS,
                                true,
                                "Sí. Se acuerda contratar limpieza en terrazas.",
                                Map.of(),
                                List.of()));

        MonotonicRouteSafetyService safety = mock(MonotonicRouteSafetyService.class);
        when(safety.validateToolResult(any(), any()))
                .thenReturn(RouteCandidateValidationResult.rejected("boolean_affirmation_without_topic:limpieza"));
        when(safety.validateRetrievalAnswer(any(), any(), any(Boolean.class)))
                .thenReturn(RouteCandidateValidationResult.accepted(0.9, "TOPIC_COVERED"));

        RagExecutionOrchestrator orchestrator = orchestrator(workflowSelector, tools, safety, false, false);

        var out = orchestrator.execute(ctx);
        assertThat(out.answerText()).isEqualTo("Retrieval-safe-answer");
        assertThat(out.executionTrace().routingFallbackApplied()).isTrue();
    }

    @Test
    void p9_rejectsUnsafeFunctionAnswerAndFallsBackToWorkflow() {
        QueryPlan plan = plan();
        ExecutionContext ctx = ctx(ragP9());
        ExecutionWorkflow workflow = mock(ExecutionWorkflow.class);
        when(workflow.workflowName()).thenReturn("ChunkDenseMetadataWorkflow");
        when(workflow.execute(any()))
                .thenReturn(
                        RagExecutionResult.withPlaceholderTrace(
                                "retrieval-parent-answer",
                                "ChunkDenseMetadataWorkflow",
                                true,
                                false,
                                ctx.knowledgeSnapshotSelection().orderedSnapshotIds(),
                                "none",
                                List.of()));

        WorkflowSelector workflowSelector = mock(WorkflowSelector.class);
        when(workflowSelector.select(any())).thenReturn(workflow);

        FunctionCallingPolicyResolver fcPolicy = mock(FunctionCallingPolicyResolver.class);
        FunctionCallingStrategy fcStrategy = mock(FunctionCallingStrategy.class);
        FunctionCallingDecision decision =
                new FunctionCallingDecision(
                        FunctionCallingMode.ENABLED,
                        FunctionCallingOutcome.NOT_APPLICABLE,
                        true,
                        List.of(DeterministicToolKind.BOOLEAN_QUERY_TOOL),
                        List.of("exposed"),
                        Optional.empty(),
                        plan.rewrittenQueryText(),
                        Map.of());
        when(fcPolicy.resolve(any(), any())).thenReturn(Optional.of(decision));
        when(fcStrategy.tryExecute(any(), any(), any()))
                .thenReturn(
                        new FunctionCallingExecutionResult(
                                FunctionCallingOutcome.EXECUTED_SUCCESS,
                                true,
                                Optional.of(DeterministicToolKind.BOOLEAN_QUERY_TOOL),
                                "Sí, se mencionó limpieza en 2026.",
                                Map.of(),
                                List.of(),
                                true,
                                List.of()));

        MonotonicRouteSafetyService safety = mock(MonotonicRouteSafetyService.class);
        when(safety.validateFunctionResult(any(), any()))
                .thenReturn(RouteCandidateValidationResult.rejected("boolean_affirmation_without_topic:limpieza"));
        when(safety.validateRetrievalAnswer(any(), any(), any(Boolean.class)))
                .thenReturn(RouteCandidateValidationResult.accepted(0.85, "TOPIC_COVERED"));

        RagExecutionOrchestrator orchestrator =
                orchestratorP9(workflowSelector, fcPolicy, fcStrategy, safety);

        var out = orchestrator.execute(ctx);
        assertThat(out.answerText()).isEqualTo("Retrieval-parent-answer");
        assertThat(out.executionTrace().routingFallbackApplied()).isTrue();
    }

    @Test
    void p15_rejectsUnsafeFunctionForRag058AndFallsBackToRetrieval() {
        String query =
                "Dime qué actas mencionan el ascensor y fueron presididas por Juan Pérez Gutiérrez.";
        QueryPlan rag058Plan = queryPlan(query, QueryType.FILTER_AND_LIST);
        ExecutionContext ctx = ctx(ragP15());
        ExecutionWorkflow workflow = mock(ExecutionWorkflow.class);
        when(workflow.workflowName()).thenReturn("ChunkDenseRagWorkflow");
        when(workflow.execute(any()))
                .thenReturn(
                        RagExecutionResult.withPlaceholderTrace(
                                "El acta del 24 de febrero de 2025 menciona el ascensor y fue presidida por Juan Pérez Gutiérrez.",
                                "ChunkDenseRagWorkflow",
                                true,
                                false,
                                ctx.knowledgeSnapshotSelection().orderedSnapshotIds(),
                                "none",
                                List.of()));

        WorkflowSelector workflowSelector = mock(WorkflowSelector.class);
        when(workflowSelector.select(any())).thenReturn(workflow);

        DeterministicToolStrategy tools = mock(DeterministicToolStrategy.class);
        when(tools.tryExecute(any(), any()))
                .thenReturn(
                        DeterministicToolExecutionResult.skipped(
                                DeterministicToolOutcome.NOT_APPLICABLE, List.of("not_applicable"), Optional.empty()));

        FunctionCallingPolicyResolver fcPolicy = mock(FunctionCallingPolicyResolver.class);
        FunctionCallingStrategy fcStrategy = mock(FunctionCallingStrategy.class);
        FunctionCallingDecision decision =
                new FunctionCallingDecision(
                        FunctionCallingMode.ENABLED,
                        FunctionCallingOutcome.NOT_APPLICABLE,
                        true,
                        List.of(DeterministicToolKind.FILTER_AND_LIST_TOOL),
                        List.of("exposed"),
                        Optional.empty(),
                        rag058Plan.rewrittenQueryText(),
                        Map.of());
        when(fcPolicy.resolve(any(), any())).thenReturn(Optional.of(decision));
        when(fcStrategy.tryExecute(any(), any(), any()))
                .thenReturn(
                        new FunctionCallingExecutionResult(
                                FunctionCallingOutcome.EXECUTED_SUCCESS,
                                true,
                                Optional.of(DeterministicToolKind.FILTER_AND_LIST_TOOL),
                                "TOPIC_NOT_IN_CONTEXT",
                                Map.of(),
                                List.of(),
                                true,
                                List.of()));

        MonotonicRouteSafetyService safety =
                new MonotonicRouteSafetyService(new RouteCandidateConstraintValidator());
        RagExecutionOrchestrator orchestrator =
                orchestratorP15Integrated(workflowSelector, tools, fcPolicy, fcStrategy, safety, rag058Plan);

        var out = orchestrator.execute(ctx);
        assertThat(out.answerText()).contains("ascensor");
        assertThat(out.answerText()).doesNotContain("TOPIC_NOT_IN_CONTEXT");
        assertThat(out.executionTrace().routingFallbackApplied()).isTrue();
        Map<String, Object> telemetry = ChatExecutionTelemetryMapper.fromTrace(out.executionTrace());
        assertThat(telemetry.get("functionCandidateRejected")).isEqualTo(true);
        assertThat(telemetry.get("selectedCandidateSource")).isEqualTo("PARENT_P7");
        assertThat(telemetry.get("selectedParentPreset")).isEqualTo("P7");
        assertThat(telemetry.get("parentFinalAnswerPreserved")).isEqualTo(true);
        assertThat(telemetry.get("parentPresetCode")).isEqualTo("P7");
        assertThat(telemetry.get("candidateRejectionReasons").toString())
                .contains("function_filter_list_incomplete");
        assertThat(telemetry.get("finalAnswerSource")).isEqualTo(ParentFinalAnswerSources.PARENT_P7_FINAL);
    }

    @Test
    void p15_rejectsUnsafeFunctionForRag059AndFallsBackToRetrieval() {
        String query =
                "¿Qué reuniones celebradas en agosto hablaron sobre videovigilancia y tuvieron más de 18 asistentes?";
        QueryPlan rag059Plan = queryPlan(query, QueryType.FILTER_AND_LIST);
        ExecutionContext ctx = ctx(ragP15());
        ExecutionWorkflow workflow = mock(ExecutionWorkflow.class);
        when(workflow.workflowName()).thenReturn("ChunkDenseRagWorkflow");
        when(workflow.execute(any()))
                .thenReturn(
                        RagExecutionResult.withPlaceholderTrace(
                                "La reunión celebrada el 25 de agosto de 2025 habló sobre videovigilancia y tuvo más de 18 asistentes.",
                                "ChunkDenseRagWorkflow",
                                true,
                                false,
                                ctx.knowledgeSnapshotSelection().orderedSnapshotIds(),
                                "none",
                                List.of()));

        WorkflowSelector workflowSelector = mock(WorkflowSelector.class);
        when(workflowSelector.select(any())).thenReturn(workflow);

        DeterministicToolStrategy tools = mock(DeterministicToolStrategy.class);
        when(tools.tryExecute(any(), any()))
                .thenReturn(
                        new DeterministicToolExecutionResult(
                                Optional.of(DeterministicToolKind.FILTER_AND_LIST_TOOL),
                                DeterministicToolOutcome.EXECUTED_SUCCESS,
                                true,
                                "No hay información suficiente en las fuentes.",
                                Map.of(),
                                List.of()));

        FunctionCallingPolicyResolver fcPolicy = mock(FunctionCallingPolicyResolver.class);
        FunctionCallingStrategy fcStrategy = mock(FunctionCallingStrategy.class);
        FunctionCallingDecision decision =
                new FunctionCallingDecision(
                        FunctionCallingMode.ENABLED,
                        FunctionCallingOutcome.NOT_APPLICABLE,
                        true,
                        List.of(DeterministicToolKind.FILTER_AND_LIST_TOOL),
                        List.of("exposed"),
                        Optional.empty(),
                        rag059Plan.rewrittenQueryText(),
                        Map.of());
        when(fcPolicy.resolve(any(), any())).thenReturn(Optional.of(decision));
        when(fcStrategy.tryExecute(any(), any(), any()))
                .thenReturn(
                        new FunctionCallingExecutionResult(
                                FunctionCallingOutcome.EXECUTED_SUCCESS,
                                true,
                                Optional.of(DeterministicToolKind.FILTER_AND_LIST_TOOL),
                                "No hay información suficiente en las fuentes.",
                                Map.of(),
                                List.of(),
                                true,
                                List.of()));

        MonotonicRouteSafetyService safety =
                new MonotonicRouteSafetyService(new RouteCandidateConstraintValidator());
        RagExecutionOrchestrator orchestrator =
                orchestratorP15Integrated(workflowSelector, tools, fcPolicy, fcStrategy, safety, rag059Plan);

        var out = orchestrator.execute(ctx);
        assertThat(out.answerText()).contains("videovigilancia");
        assertThat(out.answerText()).doesNotContain("No hay información suficiente");
        assertThat(out.executionTrace().routingFallbackApplied()).isTrue();
        Map<String, Object> telemetry = ChatExecutionTelemetryMapper.fromTrace(out.executionTrace());
        assertThat(telemetry.get("functionCandidateRejected")).isEqualTo(true);
        assertThat(telemetry.get("selectedCandidateSource")).isEqualTo("PARENT_P7");
        assertThat(telemetry.get("selectedParentPreset")).isEqualTo("P7");
        assertThat(telemetry.get("parentFinalAnswerPreserved")).isEqualTo(true);
        assertThat(telemetry.get("parentPresetCode")).isEqualTo("P7");
        assertThat(telemetry.get("candidateRejectionReasons").toString())
                .contains("function_abstention_despite_supported_parent");
        assertThat(telemetry.get("finalAnswerSource")).isEqualTo(ParentFinalAnswerSources.PARENT_P7_FINAL);
    }

    @Test
    void p15_prefersSafeParentP7OverSafeFunctionPreservingParentFinal() {
        String query = "¿Qué se dijo en relación a la limpieza de las zonas comunes?";
        QueryPlan plan = queryPlan(query, QueryType.FIND_PARAGRAPH);
        ExecutionContext ctx = ctx(ragP15());
        String parentToolAnswer =
                "En el acta del 12 de enero de 2026, se plantea la necesidad de mejorar la limpieza en las zonas comunes. Se aprueba la contratación de un nuevo servicio de limpieza con mayor frecuencia.";
        String safeFunctionAnswer =
                "En el acta del 12 de enero de 2026 se menciona la limpieza de zonas comunes de forma general sin más detalle.";
        ExecutionWorkflow workflow = mock(ExecutionWorkflow.class);
        when(workflow.workflowName()).thenReturn("ChunkDenseRagWorkflow");
        when(workflow.execute(any()))
                .thenReturn(
                        RagExecutionResult.withPlaceholderTrace(
                                "workflow paraphrase would differ",
                                "ChunkDenseRagWorkflow",
                                true,
                                false,
                                ctx.knowledgeSnapshotSelection().orderedSnapshotIds(),
                                "none",
                                List.of()));

        WorkflowSelector workflowSelector = mock(WorkflowSelector.class);
        when(workflowSelector.select(any())).thenReturn(workflow);

        DeterministicToolStrategy tools = mock(DeterministicToolStrategy.class);
        when(tools.tryExecute(any(), any()))
                .thenReturn(
                        DeterministicToolExecutionResult.skipped(
                                DeterministicToolOutcome.NOT_APPLICABLE, List.of("p15_probe"), Optional.empty()))
                .thenReturn(
                        new DeterministicToolExecutionResult(
                                Optional.of(DeterministicToolKind.FIND_PARAGRAPH_TOOL),
                                DeterministicToolOutcome.EXECUTED_SUCCESS,
                                true,
                                parentToolAnswer,
                                Map.of(),
                                List.of()));

        FunctionCallingPolicyResolver fcPolicy = mock(FunctionCallingPolicyResolver.class);
        FunctionCallingStrategy fcStrategy = mock(FunctionCallingStrategy.class);
        FunctionCallingDecision decision =
                new FunctionCallingDecision(
                        FunctionCallingMode.ENABLED,
                        FunctionCallingOutcome.NOT_APPLICABLE,
                        true,
                        List.of(DeterministicToolKind.FIND_PARAGRAPH_TOOL),
                        List.of("exposed"),
                        Optional.empty(),
                        plan.rewrittenQueryText(),
                        Map.of());
        when(fcPolicy.resolve(any(), any())).thenReturn(Optional.of(decision));
        when(fcStrategy.tryExecute(any(), any(), any()))
                .thenReturn(
                        new FunctionCallingExecutionResult(
                                FunctionCallingOutcome.EXECUTED_SUCCESS,
                                true,
                                Optional.of(DeterministicToolKind.FIND_PARAGRAPH_TOOL),
                                safeFunctionAnswer,
                                Map.of(),
                                List.of(),
                                true,
                                List.of()));

        MonotonicRouteSafetyService safety =
                new MonotonicRouteSafetyService(new RouteCandidateConstraintValidator());
        RagExecutionOrchestrator orchestrator =
                orchestratorP15Integrated(workflowSelector, tools, fcPolicy, fcStrategy, safety, plan);

        var out = orchestrator.execute(ctx);
        assertThat(out.answerText()).isEqualTo(parentToolAnswer);
        assertThat(out.usedTool()).isTrue();
        Map<String, Object> telemetry = ChatExecutionTelemetryMapper.fromTrace(out.executionTrace());
        assertThat(telemetry.get("selectedCandidateSource")).isEqualTo("PARENT_P7");
        assertThat(telemetry.get("parentFallbackUsed")).isEqualTo(true);
        assertThat(telemetry.get("parentFinalAnswerPreserved")).isEqualTo(true);
        assertThat(telemetry.get("functionCandidateRejected")).isEqualTo(true);
        assertThat(telemetry.get("candidateRejectionReasons").toString())
                .contains("function_superseded_by_supported_parent");
        assertThat(telemetry.get("finalAnswerSource")).isEqualTo(ParentFinalAnswerSources.PARENT_P7_FINAL);
    }

    @Test
    void p15_preservesExactParentP7ToolFinalWhenFunctionRejected() {
        String query =
                "¿Qué reuniones celebradas en agosto hablaron sobre videovigilancia y tuvieron más de 18 asistentes?";
        QueryPlan plan = queryPlan(query, QueryType.FILTER_AND_LIST);
        ExecutionContext ctx = ctx(ragP15());
        String exactParentToolAnswer =
                "La reunión del 25 de agosto de 2026 trató sobre videovigilancia y tuvo más de 18 asistentes.";
        String degradedWorkflowAnswer =
                "La reunión celebrada el 25 de agosto de 2025 habló sobre videovigilancia y tuvo más de 18 asistentes.";
        ExecutionWorkflow workflow = mock(ExecutionWorkflow.class);
        when(workflow.workflowName()).thenReturn("ChunkDenseRagWorkflow");
        when(workflow.execute(any()))
                .thenReturn(
                        RagExecutionResult.withPlaceholderTrace(
                                degradedWorkflowAnswer,
                                "ChunkDenseRagWorkflow",
                                true,
                                false,
                                ctx.knowledgeSnapshotSelection().orderedSnapshotIds(),
                                "none",
                                List.of()));

        WorkflowSelector workflowSelector = mock(WorkflowSelector.class);
        when(workflowSelector.select(any())).thenReturn(workflow);

        DeterministicToolStrategy tools = mock(DeterministicToolStrategy.class);
        when(tools.tryExecute(any(), any()))
                .thenReturn(
                        DeterministicToolExecutionResult.skipped(
                                DeterministicToolOutcome.NOT_APPLICABLE, List.of("p15_probe"), Optional.empty()))
                .thenReturn(
                        new DeterministicToolExecutionResult(
                                Optional.of(DeterministicToolKind.FILTER_AND_LIST_TOOL),
                                DeterministicToolOutcome.EXECUTED_SUCCESS,
                                true,
                                exactParentToolAnswer,
                                Map.of(),
                                List.of()));

        FunctionCallingPolicyResolver fcPolicy = mock(FunctionCallingPolicyResolver.class);
        FunctionCallingStrategy fcStrategy = mock(FunctionCallingStrategy.class);
        FunctionCallingDecision decision =
                new FunctionCallingDecision(
                        FunctionCallingMode.ENABLED,
                        FunctionCallingOutcome.NOT_APPLICABLE,
                        true,
                        List.of(DeterministicToolKind.FILTER_AND_LIST_TOOL),
                        List.of("exposed"),
                        Optional.empty(),
                        plan.rewrittenQueryText(),
                        Map.of());
        when(fcPolicy.resolve(any(), any())).thenReturn(Optional.of(decision));
        when(fcStrategy.tryExecute(any(), any(), any()))
                .thenReturn(
                        new FunctionCallingExecutionResult(
                                FunctionCallingOutcome.EXECUTED_SUCCESS,
                                true,
                                Optional.of(DeterministicToolKind.FILTER_AND_LIST_TOOL),
                                "No hay información suficiente en las fuentes.",
                                Map.of(),
                                List.of(),
                                true,
                                List.of()));

        MonotonicRouteSafetyService safety =
                new MonotonicRouteSafetyService(new RouteCandidateConstraintValidator());
        RagExecutionOrchestrator orchestrator =
                orchestratorP15Integrated(workflowSelector, tools, fcPolicy, fcStrategy, safety, plan);

        var out = orchestrator.execute(ctx);
        assertThat(out.answerText()).isEqualTo(exactParentToolAnswer);
        assertThat(out.answerText()).doesNotContain("2025");
        assertThat(out.usedTool()).isTrue();
        Map<String, Object> telemetry = ChatExecutionTelemetryMapper.fromTrace(out.executionTrace());
        assertThat(telemetry.get("selectedCandidateSource")).isEqualTo("PARENT_P7");
        assertThat(telemetry.get("parentFallbackUsed")).isEqualTo(true);
        assertThat(telemetry.get("parentFinalAnswerPreserved")).isEqualTo(true);
        assertThat(telemetry.get("functionCandidateRejected")).isEqualTo(true);
    }

    @Test
    void p15_allowsConstraintCompleteFunctionFinal() {
        String query =
                "Dime qué actas mencionan el ascensor y fueron presididas por Juan Pérez Gutiérrez.";
        QueryPlan plan = queryPlan(query, QueryType.FILTER_AND_LIST);
        ExecutionContext ctx = ctx(ragP15());
        ExecutionWorkflow workflow = mock(ExecutionWorkflow.class);
        when(workflow.workflowName()).thenReturn("ChunkDenseRagWorkflow");
        when(workflow.execute(any()))
                .thenReturn(
                        RagExecutionResult.withPlaceholderTrace(
                                "TOPIC_NOT_IN_CONTEXT",
                                "ChunkDenseRagWorkflow",
                                true,
                                false,
                                ctx.knowledgeSnapshotSelection().orderedSnapshotIds(),
                                "none",
                                List.of()));

        WorkflowSelector workflowSelector = mock(WorkflowSelector.class);
        when(workflowSelector.select(any())).thenReturn(workflow);

        String safeFunctionAnswer =
                "El acta del 24 de febrero de 2025 menciona el ascensor y fue presidida por Juan Pérez Gutiérrez.";
        DeterministicToolStrategy tools = mock(DeterministicToolStrategy.class);
        when(tools.tryExecute(any(), any()))
                .thenReturn(
                        DeterministicToolExecutionResult.skipped(
                                DeterministicToolOutcome.NOT_APPLICABLE, List.of("not_applicable"), Optional.empty()));

        FunctionCallingPolicyResolver fcPolicy = mock(FunctionCallingPolicyResolver.class);
        FunctionCallingStrategy fcStrategy = mock(FunctionCallingStrategy.class);
        FunctionCallingDecision decision =
                new FunctionCallingDecision(
                        FunctionCallingMode.ENABLED,
                        FunctionCallingOutcome.NOT_APPLICABLE,
                        true,
                        List.of(DeterministicToolKind.FILTER_AND_LIST_TOOL),
                        List.of("exposed"),
                        Optional.empty(),
                        plan.rewrittenQueryText(),
                        Map.of());
        when(fcPolicy.resolve(any(), any())).thenReturn(Optional.of(decision));
        when(fcStrategy.tryExecute(any(), any(), any()))
                .thenReturn(
                        new FunctionCallingExecutionResult(
                                FunctionCallingOutcome.EXECUTED_SUCCESS,
                                true,
                                Optional.of(DeterministicToolKind.FILTER_AND_LIST_TOOL),
                                safeFunctionAnswer,
                                Map.of(),
                                List.of(),
                                true,
                                List.of()));

        MonotonicRouteSafetyService safety =
                new MonotonicRouteSafetyService(new RouteCandidateConstraintValidator());
        RagExecutionOrchestrator orchestrator =
                orchestratorP15Integrated(workflowSelector, tools, fcPolicy, fcStrategy, safety, plan);

        var out = orchestrator.execute(ctx);
        assertThat(out.answerText()).isEqualTo(safeFunctionAnswer);
        Map<String, Object> telemetry = ChatExecutionTelemetryMapper.fromTrace(out.executionTrace());
        assertThat(telemetry.get("selectedCandidateSource")).isEqualTo("FUNCTION");
        assertThat(telemetry.get("functionCandidateRejected")).isEqualTo(false);
        assertThat(telemetry.get("finalAnswerSource")).isEqualTo("FUNCTION_FINAL");
    }

    @Test
    void p15_allowsConstraintCompleteToolFinal() {
        String query =
                "Dime qué actas mencionan el ascensor y fueron presididas por Juan Pérez Gutiérrez.";
        QueryPlan plan = queryPlan(query, QueryType.FILTER_AND_LIST);
        ExecutionContext ctx = ctx(ragP15());
        ExecutionWorkflow workflow = mock(ExecutionWorkflow.class);
        when(workflow.workflowName()).thenReturn("ChunkDenseRagWorkflow");
        when(workflow.execute(any()))
                .thenReturn(
                        RagExecutionResult.withPlaceholderTrace(
                                "TOPIC_NOT_IN_CONTEXT",
                                "ChunkDenseRagWorkflow",
                                true,
                                false,
                                ctx.knowledgeSnapshotSelection().orderedSnapshotIds(),
                                "none",
                                List.of()));

        WorkflowSelector workflowSelector = mock(WorkflowSelector.class);
        when(workflowSelector.select(any())).thenReturn(workflow);

        String safeToolAnswer =
                "El acta del 24 de febrero de 2025 menciona el ascensor y fue presidida por Juan Pérez Gutiérrez.";
        DeterministicToolStrategy tools = mock(DeterministicToolStrategy.class);
        when(tools.tryExecute(any(), any()))
                .thenReturn(
                        new DeterministicToolExecutionResult(
                                Optional.of(DeterministicToolKind.FILTER_AND_LIST_TOOL),
                                DeterministicToolOutcome.EXECUTED_SUCCESS,
                                true,
                                safeToolAnswer,
                                Map.of(),
                                List.of()));

        FunctionCallingPolicyResolver fcPolicy = mock(FunctionCallingPolicyResolver.class);
        when(fcPolicy.resolve(any(), any())).thenReturn(Optional.empty());

        MonotonicRouteSafetyService safety =
                new MonotonicRouteSafetyService(new RouteCandidateConstraintValidator());
        RagExecutionOrchestrator orchestrator =
                orchestratorP15Integrated(
                        workflowSelector, tools, fcPolicy, mock(FunctionCallingStrategy.class), safety, plan);

        var out = orchestrator.execute(ctx);
        assertThat(out.answerText()).isEqualTo(safeToolAnswer);
        Map<String, Object> telemetry = ChatExecutionTelemetryMapper.fromTrace(out.executionTrace());
        assertThat(telemetry.get("selectedCandidateSource")).isEqualTo("TOOL");
        assertThat(telemetry.get("toolCandidateRejected")).isEqualTo(false);
        assertThat(telemetry.get("finalAnswerSource")).isEqualTo("TOOL_FINAL");
    }

    @Test
    void p15_exportsMonotonicTelemetryOnFunctionRejection() {
        String query =
                "Dime qué actas mencionan el ascensor y fueron presididas por Juan Pérez Gutiérrez.";
        QueryPlan plan = queryPlan(query, QueryType.FILTER_AND_LIST);
        ExecutionContext ctx = ctx(ragP15());
        ExecutionWorkflow workflow = mock(ExecutionWorkflow.class);
        when(workflow.workflowName()).thenReturn("ChunkDenseRagWorkflow");
        when(workflow.execute(any()))
                .thenReturn(
                        RagExecutionResult.withPlaceholderTrace(
                                "El acta del 24 de febrero de 2025 menciona el ascensor y fue presidida por Juan Pérez Gutiérrez.",
                                "ChunkDenseRagWorkflow",
                                true,
                                false,
                                ctx.knowledgeSnapshotSelection().orderedSnapshotIds(),
                                "none",
                                List.of()));

        WorkflowSelector workflowSelector = mock(WorkflowSelector.class);
        when(workflowSelector.select(any())).thenReturn(workflow);

        DeterministicToolStrategy tools = mock(DeterministicToolStrategy.class);
        when(tools.tryExecute(any(), any()))
                .thenReturn(
                        DeterministicToolExecutionResult.skipped(
                                DeterministicToolOutcome.NOT_APPLICABLE, List.of("not_applicable"), Optional.empty()));

        FunctionCallingPolicyResolver fcPolicy = mock(FunctionCallingPolicyResolver.class);
        FunctionCallingStrategy fcStrategy = mock(FunctionCallingStrategy.class);
        FunctionCallingDecision decision =
                new FunctionCallingDecision(
                        FunctionCallingMode.ENABLED,
                        FunctionCallingOutcome.NOT_APPLICABLE,
                        true,
                        List.of(DeterministicToolKind.FILTER_AND_LIST_TOOL),
                        List.of("exposed"),
                        Optional.empty(),
                        plan.rewrittenQueryText(),
                        Map.of());
        when(fcPolicy.resolve(any(), any())).thenReturn(Optional.of(decision));
        when(fcStrategy.tryExecute(any(), any(), any()))
                .thenReturn(
                        new FunctionCallingExecutionResult(
                                FunctionCallingOutcome.EXECUTED_SUCCESS,
                                true,
                                Optional.of(DeterministicToolKind.FILTER_AND_LIST_TOOL),
                                "TOPIC_NOT_IN_CONTEXT",
                                Map.of(),
                                List.of(),
                                true,
                                List.of()));

        MonotonicRouteSafetyService safety =
                new MonotonicRouteSafetyService(new RouteCandidateConstraintValidator());
        RagExecutionOrchestrator orchestrator =
                orchestratorP15Integrated(workflowSelector, tools, fcPolicy, fcStrategy, safety, plan);

        var out = orchestrator.execute(ctx);
        assertThat(out.executionTrace().stages())
                .anyMatch(stage -> MonotonicSafetyTelemetrySupport.STAGE_NAME.equals(stage.stageName()));
        Map<String, Object> telemetry = ChatExecutionTelemetryMapper.fromTrace(out.executionTrace());
        assertThat(telemetry.get("candidateToolConsidered")).isEqualTo(true);
        assertThat(telemetry.get("candidateFunctionConsidered")).isEqualTo(true);
        assertThat(telemetry.get("candidateRetrievalConsidered")).isEqualTo(true);
        assertThat(telemetry.get("functionCandidateRejected")).isEqualTo(true);
        assertThat(telemetry.get("candidateRejectionReasons")).isNotNull();
        assertThat(telemetry.get("selectedCandidateSource")).isEqualTo("PARENT_P7");
        assertThat(telemetry.get("selectedParentPreset")).isEqualTo("P7");
        assertThat(telemetry.get("parentCandidateConsidered")).isEqualTo(true);
        assertThat(telemetry.get("parentFallbackUsed")).isEqualTo(true);
        assertThat(telemetry.get("monotonicRegressionPrevented")).isEqualTo(true);
    }

    @Test
    void p15_prefersP7ParentOverWeakIntegratedRetrievalWhenFunctionRejected() {
        String query =
                "Dime qué actas mencionan el ascensor y fueron presididas por Juan Pérez Gutiérrez.";
        QueryPlan plan = queryPlan(query, QueryType.FILTER_AND_LIST);
        ExecutionContext ctx = ctx(ragP15());
        String weakIntegrated =
                "El acta del 24 de febrero de 2025 menciona el ascensor.";
        String strongParent =
                "El acta del 24 de febrero de 2025 menciona el ascensor y fue presidida por Juan Pérez Gutiérrez.";
        ExecutionWorkflow workflow = mock(ExecutionWorkflow.class);
        when(workflow.workflowName()).thenReturn("ChunkDenseRagWorkflow");
        when(workflow.execute(any()))
                .thenReturn(
                        RagExecutionResult.withPlaceholderTrace(
                                weakIntegrated,
                                "ChunkDenseRagWorkflow",
                                true,
                                false,
                                ctx.knowledgeSnapshotSelection().orderedSnapshotIds(),
                                "none",
                                List.of()))
                .thenReturn(
                        RagExecutionResult.withPlaceholderTrace(
                                strongParent,
                                "ChunkDenseRagWorkflow",
                                true,
                                false,
                                ctx.knowledgeSnapshotSelection().orderedSnapshotIds(),
                                "none",
                                List.of()));

        WorkflowSelector workflowSelector = mock(WorkflowSelector.class);
        when(workflowSelector.select(any())).thenReturn(workflow);

        DeterministicToolStrategy tools = mock(DeterministicToolStrategy.class);
        when(tools.tryExecute(any(), any()))
                .thenReturn(
                        DeterministicToolExecutionResult.skipped(
                                DeterministicToolOutcome.NOT_APPLICABLE, List.of("not_applicable"), Optional.empty()))
                .thenReturn(
                        new DeterministicToolExecutionResult(
                                Optional.of(DeterministicToolKind.FILTER_AND_LIST_TOOL),
                                DeterministicToolOutcome.EXECUTED_SUCCESS,
                                true,
                                "TOPIC_NOT_IN_CONTEXT",
                                Map.of(),
                                List.of()));

        FunctionCallingPolicyResolver fcPolicy = mock(FunctionCallingPolicyResolver.class);
        FunctionCallingStrategy fcStrategy = mock(FunctionCallingStrategy.class);
        FunctionCallingDecision decision =
                new FunctionCallingDecision(
                        FunctionCallingMode.ENABLED,
                        FunctionCallingOutcome.NOT_APPLICABLE,
                        true,
                        List.of(DeterministicToolKind.FILTER_AND_LIST_TOOL),
                        List.of("exposed"),
                        Optional.empty(),
                        plan.rewrittenQueryText(),
                        Map.of());
        when(fcPolicy.resolve(any(), any())).thenReturn(Optional.of(decision));
        when(fcStrategy.tryExecute(any(), any(), any()))
                .thenReturn(
                        new FunctionCallingExecutionResult(
                                FunctionCallingOutcome.EXECUTED_SUCCESS,
                                true,
                                Optional.of(DeterministicToolKind.FILTER_AND_LIST_TOOL),
                                "TOPIC_NOT_IN_CONTEXT",
                                Map.of(),
                                List.of(),
                                true,
                                List.of()));

        MonotonicRouteSafetyService safety =
                new MonotonicRouteSafetyService(new RouteCandidateConstraintValidator());
        RagExecutionOrchestrator orchestrator =
                orchestratorP15Integrated(workflowSelector, tools, fcPolicy, fcStrategy, safety, plan);

        var out = orchestrator.execute(ctx);
        assertThat(out.answerText()).isEqualTo(strongParent);
        Map<String, Object> telemetry = ChatExecutionTelemetryMapper.fromTrace(out.executionTrace());
        assertThat(telemetry.get("selectedCandidateSource")).isEqualTo("PARENT_P7");
        assertThat(telemetry.get("selectedParentPreset")).isEqualTo("P7");
    }

    @Test
    void p15_prefersRetrievalWhenToolRejected() {
        QueryPlan plan = plan();
        ExecutionContext ctx = ctx(ragP15());
        String safeRetrieval = "Sí, se mencionó limpieza en alguna reunión celebrada en 2026.";
        ExecutionWorkflow workflow = mock(ExecutionWorkflow.class);
        when(workflow.workflowName()).thenReturn("ChunkDenseRagWorkflow");
        when(workflow.execute(any()))
                .thenReturn(
                        RagExecutionResult.withPlaceholderTrace(
                                safeRetrieval,
                                "ChunkDenseRagWorkflow",
                                true,
                                false,
                                ctx.knowledgeSnapshotSelection().orderedSnapshotIds(),
                                "none",
                                List.of()));

        WorkflowSelector workflowSelector = mock(WorkflowSelector.class);
        when(workflowSelector.select(any())).thenReturn(workflow);

        DeterministicToolStrategy tools = mock(DeterministicToolStrategy.class);
        when(tools.tryExecute(any(), any()))
                .thenReturn(
                        new DeterministicToolExecutionResult(
                                Optional.of(DeterministicToolKind.COUNT_DOCUMENTS_TOOL),
                                DeterministicToolOutcome.EXECUTED_SUCCESS,
                                true,
                                "bad-tool",
                                Map.of(),
                                List.of()));

        FunctionCallingPolicyResolver fcPolicy = mock(FunctionCallingPolicyResolver.class);
        when(fcPolicy.resolve(any(), any())).thenReturn(Optional.empty());

        MonotonicRouteSafetyService safety =
                new MonotonicRouteSafetyService(new RouteCandidateConstraintValidator());
        RagExecutionOrchestrator orchestrator =
                orchestratorP15Integrated(
                        workflowSelector,
                        tools,
                        fcPolicy,
                        mock(FunctionCallingStrategy.class),
                        safety,
                        plan);

        var out = orchestrator.execute(ctx);
        assertThat(out.answerText()).isEqualTo(safeRetrieval);
        Map<String, Object> telemetry = ChatExecutionTelemetryMapper.fromTrace(out.executionTrace());
        assertThat(telemetry.get("selectedCandidateSource")).isEqualTo("RETRIEVAL");
        assertThat(telemetry.get("toolCandidateRejected")).isEqualTo(true);
    }

    @Test
    void p15_doesNotSelectUnsafeP7Parent() {
        String query =
                "Dime qué actas mencionan el ascensor y fueron presididas por Juan Pérez Gutiérrez.";
        QueryPlan plan = queryPlan(query, QueryType.FILTER_AND_LIST);
        ExecutionContext ctx = ctx(ragP15());
        String safeRetrieval =
                "El acta del 24 de febrero de 2025 menciona el ascensor y fue presidida por Juan Pérez Gutiérrez.";
        ExecutionWorkflow workflow = mock(ExecutionWorkflow.class);
        when(workflow.workflowName()).thenReturn("ChunkDenseRagWorkflow");
        when(workflow.execute(any()))
                .thenReturn(
                        RagExecutionResult.withPlaceholderTrace(
                                safeRetrieval,
                                "ChunkDenseRagWorkflow",
                                true,
                                false,
                                ctx.knowledgeSnapshotSelection().orderedSnapshotIds(),
                                "none",
                                List.of()))
                .thenReturn(
                        RagExecutionResult.withPlaceholderTrace(
                                "TOPIC_NOT_IN_CONTEXT",
                                "ChunkDenseRagWorkflow",
                                true,
                                false,
                                ctx.knowledgeSnapshotSelection().orderedSnapshotIds(),
                                "none",
                                List.of()));

        WorkflowSelector workflowSelector = mock(WorkflowSelector.class);
        when(workflowSelector.select(any())).thenReturn(workflow);

        DeterministicToolStrategy tools = mock(DeterministicToolStrategy.class);
        when(tools.tryExecute(any(), any()))
                .thenReturn(
                        DeterministicToolExecutionResult.skipped(
                                DeterministicToolOutcome.NOT_APPLICABLE, List.of("not_applicable"), Optional.empty()))
                .thenReturn(
                        new DeterministicToolExecutionResult(
                                Optional.of(DeterministicToolKind.FILTER_AND_LIST_TOOL),
                                DeterministicToolOutcome.EXECUTED_SUCCESS,
                                true,
                                "TOPIC_NOT_IN_CONTEXT",
                                Map.of(),
                                List.of()));

        FunctionCallingPolicyResolver fcPolicy = mock(FunctionCallingPolicyResolver.class);
        FunctionCallingStrategy fcStrategy = mock(FunctionCallingStrategy.class);
        FunctionCallingDecision decision =
                new FunctionCallingDecision(
                        FunctionCallingMode.ENABLED,
                        FunctionCallingOutcome.NOT_APPLICABLE,
                        true,
                        List.of(DeterministicToolKind.FILTER_AND_LIST_TOOL),
                        List.of("exposed"),
                        Optional.empty(),
                        plan.rewrittenQueryText(),
                        Map.of());
        when(fcPolicy.resolve(any(), any())).thenReturn(Optional.of(decision));
        when(fcStrategy.tryExecute(any(), any(), any()))
                .thenReturn(
                        new FunctionCallingExecutionResult(
                                FunctionCallingOutcome.EXECUTED_SUCCESS,
                                true,
                                Optional.of(DeterministicToolKind.FILTER_AND_LIST_TOOL),
                                "TOPIC_NOT_IN_CONTEXT",
                                Map.of(),
                                List.of(),
                                true,
                                List.of()));

        MonotonicRouteSafetyService safety =
                new MonotonicRouteSafetyService(new RouteCandidateConstraintValidator());
        RagExecutionOrchestrator orchestrator =
                orchestratorP15Integrated(workflowSelector, tools, fcPolicy, fcStrategy, safety, plan);

        var out = orchestrator.execute(ctx);
        assertThat(out.answerText()).isEqualTo(safeRetrieval);
        Map<String, Object> telemetry = ChatExecutionTelemetryMapper.fromTrace(out.executionTrace());
        assertThat(telemetry.get("parentCandidateConsidered")).isEqualTo(true);
        assertThat(telemetry.get("selectedCandidateSource")).isEqualTo("RETRIEVAL");
        assertThat(telemetry.get("rejectedCandidateSources").toString()).contains("PARENT_P7");
    }

    @Test
    void p15_fallsBackToP6WhenP7UnsafeAndP6Valid() {
        String query =
                "Dime qué actas mencionan el ascensor y fueron presididas por Juan Pérez Gutiérrez.";
        QueryPlan plan = queryPlan(query, QueryType.FILTER_AND_LIST);
        ExecutionContext ctx = ctx(ragP15());
        String p6Answer =
                "El acta del 24 de febrero de 2025 menciona el ascensor y fue presidida por Juan Pérez Gutiérrez.";
        ExecutionWorkflow workflow = mock(ExecutionWorkflow.class);
        when(workflow.workflowName()).thenReturn("ChunkDenseRagWorkflow");
        when(workflow.execute(any()))
                .thenReturn(
                        RagExecutionResult.withPlaceholderTrace(
                                "weak integrated retrieval",
                                "ChunkDenseRagWorkflow",
                                true,
                                false,
                                ctx.knowledgeSnapshotSelection().orderedSnapshotIds(),
                                "none",
                                List.of()))
                .thenReturn(
                        RagExecutionResult.withPlaceholderTrace(
                                "TOPIC_NOT_IN_CONTEXT",
                                "ChunkDenseRagWorkflow",
                                true,
                                false,
                                ctx.knowledgeSnapshotSelection().orderedSnapshotIds(),
                                "none",
                                List.of()))
                .thenReturn(
                        RagExecutionResult.withPlaceholderTrace(
                                p6Answer,
                                "ChunkDenseRagWorkflow",
                                true,
                                false,
                                ctx.knowledgeSnapshotSelection().orderedSnapshotIds(),
                                "none",
                                List.of()));

        WorkflowSelector workflowSelector = mock(WorkflowSelector.class);
        when(workflowSelector.select(any())).thenReturn(workflow);

        DeterministicToolStrategy tools = mock(DeterministicToolStrategy.class);
        when(tools.tryExecute(any(), any()))
                .thenReturn(
                        DeterministicToolExecutionResult.skipped(
                                DeterministicToolOutcome.NOT_APPLICABLE, List.of("not_applicable"), Optional.empty()))
                .thenReturn(
                        new DeterministicToolExecutionResult(
                                Optional.of(DeterministicToolKind.FILTER_AND_LIST_TOOL),
                                DeterministicToolOutcome.EXECUTED_SUCCESS,
                                true,
                                "TOPIC_NOT_IN_CONTEXT",
                                Map.of(),
                                List.of()));

        FunctionCallingPolicyResolver fcPolicy = mock(FunctionCallingPolicyResolver.class);
        FunctionCallingStrategy fcStrategy = mock(FunctionCallingStrategy.class);
        FunctionCallingDecision decision =
                new FunctionCallingDecision(
                        FunctionCallingMode.ENABLED,
                        FunctionCallingOutcome.NOT_APPLICABLE,
                        true,
                        List.of(DeterministicToolKind.FILTER_AND_LIST_TOOL),
                        List.of("exposed"),
                        Optional.empty(),
                        plan.rewrittenQueryText(),
                        Map.of());
        when(fcPolicy.resolve(any(), any())).thenReturn(Optional.of(decision));
        when(fcStrategy.tryExecute(any(), any(), any()))
                .thenReturn(
                        new FunctionCallingExecutionResult(
                                FunctionCallingOutcome.EXECUTED_SUCCESS,
                                true,
                                Optional.of(DeterministicToolKind.FILTER_AND_LIST_TOOL),
                                "TOPIC_NOT_IN_CONTEXT",
                                Map.of(),
                                List.of(),
                                true,
                                List.of()));

        MonotonicRouteSafetyService safety =
                new MonotonicRouteSafetyService(new RouteCandidateConstraintValidator());
        RagExecutionOrchestrator orchestrator =
                orchestratorP15Integrated(workflowSelector, tools, fcPolicy, fcStrategy, safety, plan);

        var out = orchestrator.execute(ctx);
        assertThat(out.answerText()).isEqualTo(p6Answer);
        Map<String, Object> telemetry = ChatExecutionTelemetryMapper.fromTrace(out.executionTrace());
        assertThat(telemetry.get("selectedCandidateSource")).isEqualTo("PARENT_P6");
        assertThat(telemetry.get("selectedParentPreset")).isEqualTo("P6");
        assertThat(telemetry.get("parentPresetCode")).isEqualTo("P6");
        assertThat(telemetry.get("parentFinalAnswerPreserved")).isEqualTo(true);
        assertThat(telemetry.get("parentCandidateConsidered")).isEqualTo(true);
        assertThat(telemetry.get("rejectedCandidateSources").toString()).contains("PARENT_P7");
    }

    @Test
    void p15_abstainsWhenAllCandidatesUnsafe() {
        String query =
                "Dime qué actas mencionan el ascensor y fueron presididas por Juan Pérez Gutiérrez.";
        QueryPlan plan = queryPlan(query, QueryType.FILTER_AND_LIST);
        ExecutionContext ctx = ctx(ragP15());
        ExecutionWorkflow workflow = mock(ExecutionWorkflow.class);
        when(workflow.workflowName()).thenReturn("ChunkDenseRagWorkflow");
        when(workflow.execute(any()))
                .thenReturn(
                        RagExecutionResult.withPlaceholderTrace(
                                "TOPIC_NOT_IN_CONTEXT",
                                "ChunkDenseRagWorkflow",
                                true,
                                false,
                                ctx.knowledgeSnapshotSelection().orderedSnapshotIds(),
                                "none",
                                List.of()));

        WorkflowSelector workflowSelector = mock(WorkflowSelector.class);
        when(workflowSelector.select(any())).thenReturn(workflow);

        DeterministicToolStrategy tools = mock(DeterministicToolStrategy.class);
        when(tools.tryExecute(any(), any()))
                .thenReturn(
                        DeterministicToolExecutionResult.skipped(
                                DeterministicToolOutcome.NOT_APPLICABLE, List.of("not_applicable"), Optional.empty()))
                .thenReturn(
                        new DeterministicToolExecutionResult(
                                Optional.of(DeterministicToolKind.FILTER_AND_LIST_TOOL),
                                DeterministicToolOutcome.EXECUTED_SUCCESS,
                                true,
                                "TOPIC_NOT_IN_CONTEXT",
                                Map.of(),
                                List.of()));

        FunctionCallingPolicyResolver fcPolicy = mock(FunctionCallingPolicyResolver.class);
        FunctionCallingStrategy fcStrategy = mock(FunctionCallingStrategy.class);
        FunctionCallingDecision decision =
                new FunctionCallingDecision(
                        FunctionCallingMode.ENABLED,
                        FunctionCallingOutcome.NOT_APPLICABLE,
                        true,
                        List.of(DeterministicToolKind.FILTER_AND_LIST_TOOL),
                        List.of("exposed"),
                        Optional.empty(),
                        plan.rewrittenQueryText(),
                        Map.of());
        when(fcPolicy.resolve(any(), any())).thenReturn(Optional.of(decision));
        when(fcStrategy.tryExecute(any(), any(), any()))
                .thenReturn(
                        new FunctionCallingExecutionResult(
                                FunctionCallingOutcome.EXECUTED_SUCCESS,
                                true,
                                Optional.of(DeterministicToolKind.FILTER_AND_LIST_TOOL),
                                "TOPIC_NOT_IN_CONTEXT",
                                Map.of(),
                                List.of(),
                                true,
                                List.of()));

        MonotonicRouteSafetyService safety =
                new MonotonicRouteSafetyService(new RouteCandidateConstraintValidator());
        RagExecutionOrchestrator orchestrator =
                orchestratorP15Integrated(workflowSelector, tools, fcPolicy, fcStrategy, safety, plan);

        var out = orchestrator.execute(ctx);
        assertThat(out.answerText()).doesNotContain("TOPIC_NOT_IN_CONTEXT");
        Map<String, Object> telemetry = ChatExecutionTelemetryMapper.fromTrace(out.executionTrace());
        assertThat(telemetry.get("selectedCandidateSource")).isEqualTo("ABSTENTION");
        assertThat(telemetry.get("parentCandidateConsidered")).isEqualTo(true);
        assertThat(telemetry.get("rejectedCandidateSources").toString()).contains("PARENT_P7");
        assertThat(telemetry.get("rejectedCandidateSources").toString()).contains("PARENT_P6");
    }

    @Test
    void p7_baselineFloor_prefersSafeCampaignP6ParentOverWeakerNativeTool() {
        Runnable closeItem = LabBenchmarkExecutionContext.openBenchmarkItemScope("RAG-029");
        try {
            String p6Answer = "La duración de la reunión fue de 1 hora y 30 minutos.";
            LabBenchmarkExecutionContext.recordCampaignParentOutcome(
                    "P6",
                    "RAG-029",
                    new CampaignParentOutcome(
                            p6Answer,
                            "ChunkDenseRagWorkflow",
                            true,
                            "RETRIEVAL_WORKFLOW_ROUTE",
                            false,
                            false,
                            "none",
                            "GENERATED",
                            "COMPLETE",
                            "SAFE",
                            "true"));

            String query = "Duración de la reunión del 25 de febrero de 2026.";
            QueryPlan plan = queryPlan(query, QueryType.GET_DURATION);
            ExecutionContext ctx = ctx(ragP7());
            ExecutionWorkflow workflow = mock(ExecutionWorkflow.class);
            when(workflow.workflowName()).thenReturn("ChunkDenseMetadataWorkflow");
            when(workflow.execute(any()))
                    .thenReturn(
                            RagExecutionResult.withPlaceholderTrace(
                                    "La duración fue de 2 horas.",
                                    "ChunkDenseMetadataWorkflow",
                                    true,
                                    false,
                                    ctx.knowledgeSnapshotSelection().orderedSnapshotIds(),
                                    "none",
                                    List.of()));

            WorkflowSelector workflowSelector = mock(WorkflowSelector.class);
            when(workflowSelector.select(any())).thenReturn(workflow);

            DeterministicToolStrategy tools = mock(DeterministicToolStrategy.class);
            when(tools.tryExecute(any(), any()))
                    .thenReturn(
                            new DeterministicToolExecutionResult(
                                    Optional.of(DeterministicToolKind.GET_DURATION_TOOL),
                                    DeterministicToolOutcome.EXECUTED_SUCCESS,
                                    true,
                                    "La duración fue de 2 horas.",
                                    Map.of(),
                                    List.of()));

            MonotonicRouteSafetyService safety =
                    new MonotonicRouteSafetyService(new RouteCandidateConstraintValidator());
            RagExecutionOrchestrator orchestrator =
                    orchestratorP7WithCampaignResolver(workflowSelector, tools, safety, plan);

            var out = orchestrator.execute(ctx);
            assertThat(out.answerText()).isEqualTo(p6Answer);
            Map<String, Object> telemetry = ChatExecutionTelemetryMapper.fromTrace(out.executionTrace());
            assertThat(telemetry.get("selectedCandidateSource")).isEqualTo("PARENT_P6");
            assertThat(telemetry.get("parentCampaignOutcomeReused")).isEqualTo(true);
            assertThat(telemetry.get("monotonicFloorApplied")).isEqualTo(true);
            assertThat(telemetry.get("monotonicFloorPreventedRegression")).isEqualTo(true);
            assertThat(telemetry.get("baselineCandidateSelected")).isEqualTo(true);
            assertThat(telemetry.get("baselineCandidateSource")).isEqualTo("PARENT_P6");
            assertThat(telemetry.get("baselineCandidatePresetCode")).isEqualTo("P6");
            assertThat(telemetry.get("parentFinalAnswerPreserved")).isEqualTo(true);
            assertThat(telemetry.get("parentFinalAnswerHash")).isEqualTo(telemetry.get("selectedFinalAnswerHash"));
        } finally {
            closeItem.run();
        }
    }

    @Test
    void p15_baselineFloor_prefersSafeCampaignP7ParentOverAbstentionWhenFunctionNotRejected() {
        Runnable closeItem = LabBenchmarkExecutionContext.openBenchmarkItemScope("RAG-029");
        try {
            String parentAnswer = "La duración de la reunión fue de 1 hora y 30 minutos.";
            LabBenchmarkExecutionContext.recordCampaignParentOutcome(
                    "P7",
                    "RAG-029",
                    new CampaignParentOutcome(
                            parentAnswer,
                            "ChunkDenseRagWorkflow",
                            true,
                            "RETRIEVAL_WORKFLOW_ROUTE",
                            false,
                            false,
                            "none",
                            "GENERATED",
                            "COMPLETE",
                            "SAFE",
                            "true"));

            String query = "Duración de la reunión del 25 de febrero de 2026.";
            QueryPlan plan = queryPlan(query, QueryType.GET_DURATION);
            ExecutionContext ctx = ctx(ragP15());
            ExecutionWorkflow workflow = mock(ExecutionWorkflow.class);
            when(workflow.workflowName()).thenReturn("ChunkDenseRagWorkflow");
            when(workflow.execute(any()))
                    .thenReturn(
                            RagExecutionResult.withPlaceholderTrace(
                                    "TOPIC_NOT_IN_CONTEXT",
                                    "ChunkDenseRagWorkflow",
                                    true,
                                    false,
                                    ctx.knowledgeSnapshotSelection().orderedSnapshotIds(),
                                    "none",
                                    List.of()));

            WorkflowSelector workflowSelector = mock(WorkflowSelector.class);
            when(workflowSelector.select(any())).thenReturn(workflow);

            DeterministicToolStrategy tools = mock(DeterministicToolStrategy.class);
            when(tools.tryExecute(any(), any()))
                    .thenReturn(
                            DeterministicToolExecutionResult.skipped(
                                    DeterministicToolOutcome.NOT_APPLICABLE, List.of("p15_probe"), Optional.empty()))
                    .thenReturn(
                            DeterministicToolExecutionResult.skipped(
                                    DeterministicToolOutcome.NOT_APPLICABLE, List.of("parent_probe"), Optional.empty()));

            FunctionCallingPolicyResolver fcPolicy = mock(FunctionCallingPolicyResolver.class);
            when(fcPolicy.resolve(any(), any())).thenReturn(Optional.empty());

            MonotonicRouteSafetyService safety =
                    new MonotonicRouteSafetyService(new RouteCandidateConstraintValidator());
            RagExecutionOrchestrator orchestrator =
                    orchestratorP15IntegratedWithCampaignResolver(
                            workflowSelector,
                            tools,
                            fcPolicy,
                            mock(FunctionCallingStrategy.class),
                            safety,
                            plan);

            var out = orchestrator.execute(ctx);
            assertThat(out.answerText()).isEqualTo(parentAnswer);
            Map<String, Object> telemetry = ChatExecutionTelemetryMapper.fromTrace(out.executionTrace());
            assertThat(telemetry.get("selectedCandidateSource")).isEqualTo("PARENT_P7");
            assertThat(telemetry.get("parentCampaignOutcomeReused")).isEqualTo(true);
            assertThat(telemetry.get("baselineFloorApplied")).isEqualTo(true);
            assertThat(telemetry.get("monotonicFloorApplied")).isEqualTo(true);
            assertThat(telemetry.get("baselineCandidateSelected")).isEqualTo(true);
            assertThat(telemetry.get("baselineCandidateSource")).isEqualTo("PARENT_P7");
            assertThat(telemetry.get("baselineCandidatePresetCode")).isEqualTo("P7");
            assertThat(telemetry.get("monotonicFloorPreventedRegression")).isEqualTo(true);
            assertThat(telemetry.get("parentFinalAnswerPreserved")).isEqualTo(true);
            assertThat(telemetry.get("parentFinalAnswerHash")).isEqualTo(telemetry.get("selectedFinalAnswerHash"));
            assertThat(telemetry.get("baselineFloorReason").toString())
                    .containsAnyOf("parent_p7_over_", "baseline_floor_kept_parent", "baseline_floor_parent_selected");
        } finally {
            closeItem.run();
        }
    }

    @Test
    void p15_campaignReplay_preservesToolFinalTelemetryForMatcherExport() {
        Runnable closeItem = LabBenchmarkExecutionContext.openBenchmarkItemScope("RAG-005");
        try {
            String parentAnswer = "Una. La acta 1.pdf tuvo menos de diez participantes.";
            LabBenchmarkExecutionContext.recordCampaignParentOutcome(
                    "P7",
                    "RAG-005",
                    new CampaignParentOutcome(
                            parentAnswer,
                            "deterministic-tool",
                            false,
                            "DETERMINISTIC_TOOL_ROUTE",
                            false,
                            true,
                            "count_documents",
                            "TOOL_FINAL",
                            "COMPLETE",
                            "SAFE",
                            "true"));

            String query = "¿Cuántas actas tuvieron menos de diez participantes?";
            QueryPlan plan = queryPlan(query, QueryType.COUNT_DOCUMENTS);
            ExecutionContext ctx = ctx(ragP15());
            ExecutionWorkflow workflow = mock(ExecutionWorkflow.class);
            when(workflow.workflowName()).thenReturn("ChunkDenseRagWorkflow");
            when(workflow.execute(any()))
                    .thenReturn(
                            RagExecutionResult.withPlaceholderTrace(
                                    "wrong retrieval",
                                    "ChunkDenseRagWorkflow",
                                    true,
                                    false,
                                    ctx.knowledgeSnapshotSelection().orderedSnapshotIds(),
                                    "none",
                                    List.of()));

            WorkflowSelector workflowSelector = mock(WorkflowSelector.class);
            when(workflowSelector.select(any())).thenReturn(workflow);

            DeterministicToolStrategy tools = mock(DeterministicToolStrategy.class);
            when(tools.tryExecute(any(), any()))
                    .thenReturn(
                            DeterministicToolExecutionResult.skipped(
                                    DeterministicToolOutcome.NOT_APPLICABLE, List.of("p15_probe"), Optional.empty()))
                    .thenReturn(
                            DeterministicToolExecutionResult.skipped(
                                    DeterministicToolOutcome.NOT_APPLICABLE, List.of("parent_probe"), Optional.empty()));

            FunctionCallingPolicyResolver fcPolicy = mock(FunctionCallingPolicyResolver.class);
            FunctionCallingStrategy fcStrategy = mock(FunctionCallingStrategy.class);
            FunctionCallingDecision decision =
                    new FunctionCallingDecision(
                            FunctionCallingMode.ENABLED,
                            FunctionCallingOutcome.NOT_APPLICABLE,
                            true,
                            List.of(DeterministicToolKind.COUNT_DOCUMENTS_TOOL),
                            List.of("exposed"),
                            Optional.empty(),
                            plan.rewrittenQueryText(),
                            Map.of());
            when(fcPolicy.resolve(any(), any())).thenReturn(Optional.of(decision));
            when(fcStrategy.tryExecute(any(), any(), any()))
                    .thenReturn(
                            new FunctionCallingExecutionResult(
                                    FunctionCallingOutcome.EXECUTED_SUCCESS,
                                    true,
                                    Optional.of(DeterministicToolKind.COUNT_DOCUMENTS_TOOL),
                                    "No hay información suficiente en las fuentes.",
                                    Map.of(),
                                    List.of(),
                                    true,
                                    List.of()));

            MonotonicRouteSafetyService safety =
                    new MonotonicRouteSafetyService(new RouteCandidateConstraintValidator());
            RagExecutionOrchestrator orchestrator =
                    orchestratorP15IntegratedWithCampaignResolver(
                            workflowSelector, tools, fcPolicy, fcStrategy, safety, plan);

            var out = orchestrator.execute(ctx);
            assertThat(out.answerText()).isEqualTo(parentAnswer);
            Map<String, Object> telemetry = ChatExecutionTelemetryMapper.fromTrace(out.executionTrace());
            assertThat(telemetry.get("selectedCandidateSource")).isEqualTo("PARENT_P7");
            assertThat(telemetry.get("parentCampaignOutcomeReused")).isEqualTo(true);
            assertThat(telemetry.get("toolResultUsedAsFinal")).isEqualTo(true);
            assertThat(telemetry.get("toolExecuted")).isEqualTo(true);
        } finally {
            closeItem.run();
        }
    }

    @Test
    void p15_campaignReplay_preservesExactP7MatcherVisibleAnswerForRag059() {
        Runnable closeItem = LabBenchmarkExecutionContext.openBenchmarkItemScope("RAG-059");
        try {
            LabBenchmarkExecutionContext.recordCampaignParentOutcome(
                    "P7",
                    "RAG-059",
                    new CampaignParentOutcome(
                            RAG_059_P7_ANSWER,
                            "deterministic-tool",
                            false,
                            "DETERMINISTIC_TOOL_ROUTE",
                            false,
                            true,
                            "filter_and_list",
                            "TOOL_FINAL",
                            "COMPLETE",
                            "SAFE",
                            "true"));

            String query =
                    "¿Qué reuniones celebradas en agosto hablaron sobre videovigilancia y tuvieron más de 18 asistentes?";
            QueryPlan plan = queryPlan(query, QueryType.FILTER_AND_LIST);
            ExecutionContext ctx = ctx(ragP15());
            ExecutionWorkflow workflow = mock(ExecutionWorkflow.class);
            when(workflow.workflowName()).thenReturn("ChunkDenseRagWorkflow");
            when(workflow.execute(any()))
                    .thenReturn(
                            RagExecutionResult.withPlaceholderTrace(
                                    "degraded workflow answer",
                                    "ChunkDenseRagWorkflow",
                                    true,
                                    false,
                                    ctx.knowledgeSnapshotSelection().orderedSnapshotIds(),
                                    "none",
                                    List.of()));

            WorkflowSelector workflowSelector = mock(WorkflowSelector.class);
            when(workflowSelector.select(any())).thenReturn(workflow);

            DeterministicToolStrategy tools = mock(DeterministicToolStrategy.class);
            when(tools.tryExecute(any(), any()))
                    .thenReturn(
                            DeterministicToolExecutionResult.skipped(
                                    DeterministicToolOutcome.NOT_APPLICABLE, List.of("p15_probe"), Optional.empty()))
                    .thenReturn(
                            DeterministicToolExecutionResult.skipped(
                                    DeterministicToolOutcome.NOT_APPLICABLE, List.of("parent_probe"), Optional.empty()));

            FunctionCallingPolicyResolver fcPolicy = mock(FunctionCallingPolicyResolver.class);
            FunctionCallingStrategy fcStrategy = mock(FunctionCallingStrategy.class);
            FunctionCallingDecision decision =
                    new FunctionCallingDecision(
                            FunctionCallingMode.ENABLED,
                            FunctionCallingOutcome.NOT_APPLICABLE,
                            true,
                            List.of(DeterministicToolKind.FILTER_AND_LIST_TOOL),
                            List.of("exposed"),
                            Optional.empty(),
                            plan.rewrittenQueryText(),
                            Map.of());
            when(fcPolicy.resolve(any(), any())).thenReturn(Optional.of(decision));
            when(fcStrategy.tryExecute(any(), any(), any()))
                    .thenReturn(
                            new FunctionCallingExecutionResult(
                                    FunctionCallingOutcome.EXECUTED_SUCCESS,
                                    true,
                                    Optional.of(DeterministicToolKind.FILTER_AND_LIST_TOOL),
                                    "No hay información suficiente en las fuentes.",
                                    Map.of(),
                                    List.of(),
                                    true,
                                    List.of()));

            MonotonicRouteSafetyService safety =
                    new MonotonicRouteSafetyService(new RouteCandidateConstraintValidator());
            RagExecutionOrchestrator orchestrator =
                    orchestratorP15IntegratedWithCampaignResolver(
                            workflowSelector, tools, fcPolicy, fcStrategy, safety, plan);

            var out = orchestrator.execute(ctx);
            assertThat(out.answerText()).isEqualTo(RAG_059_P7_ANSWER);
            Map<String, Object> telemetry = ChatExecutionTelemetryMapper.fromTrace(out.executionTrace());
            assertThat(telemetry.get("selectedCandidateSource")).isEqualTo("PARENT_P7");
            assertThat(telemetry.get("parentFinalAnswerPreserved")).isEqualTo(true);
            assertThat(telemetry.get("parentCampaignOutcomeReused")).isEqualTo(true);
            assertThat(telemetry.get("parentFinalAnswerHash"))
                    .isEqualTo(telemetry.get("selectedFinalAnswerHash"));
            assertThat(telemetry.get("toolResultUsedAsFinal")).isEqualTo(true);
            assertThat(telemetry.get("finalAnswerSource")).isEqualTo("TOOL_FINAL");
        } finally {
            closeItem.run();
        }
    }

    @Test
    void p15_campaignReplay_preservesExactP6MatcherVisibleAnswer() {
        Runnable closeItem = LabBenchmarkExecutionContext.openBenchmarkItemScope("RAG-016");
        try {
            String parentAnswer =
                    "Se acordó mejorar la limpieza de zonas comunes y se aprobó contratar un nuevo servicio.";
            LabBenchmarkExecutionContext.recordCampaignParentOutcome(
                    "P6",
                    "RAG-016",
                    new CampaignParentOutcome(
                            parentAnswer,
                            "ChunkDenseRagWorkflow",
                            true,
                            "RETRIEVAL_WORKFLOW_ROUTE",
                            false,
                            false,
                            "none",
                            "GENERATED",
                            "COMPLETE",
                            "SAFE",
                            "true"));

            String query = "¿Qué se dijo sobre la limpieza de zonas comunes?";
            QueryPlan plan = queryPlan(query, QueryType.FIND_PARAGRAPH);
            ExecutionContext ctx = ctx(ragP15());
            ExecutionWorkflow workflow = mock(ExecutionWorkflow.class);
            when(workflow.workflowName()).thenReturn("ChunkDenseRagWorkflow");
            when(workflow.execute(any()))
                    .thenReturn(
                            RagExecutionResult.withPlaceholderTrace(
                                    "TOPIC_NOT_IN_CONTEXT",
                                    "ChunkDenseRagWorkflow",
                                    true,
                                    false,
                                    ctx.knowledgeSnapshotSelection().orderedSnapshotIds(),
                                    "none",
                                    List.of()));

            WorkflowSelector workflowSelector = mock(WorkflowSelector.class);
            when(workflowSelector.select(any())).thenReturn(workflow);

            DeterministicToolStrategy tools = mock(DeterministicToolStrategy.class);
            when(tools.tryExecute(any(), any()))
                    .thenReturn(
                            DeterministicToolExecutionResult.skipped(
                                    DeterministicToolOutcome.NOT_APPLICABLE, List.of("p15_probe"), Optional.empty()))
                    .thenReturn(
                            DeterministicToolExecutionResult.skipped(
                                    DeterministicToolOutcome.NOT_APPLICABLE, List.of("parent_probe"), Optional.empty()));

            FunctionCallingPolicyResolver fcPolicy = mock(FunctionCallingPolicyResolver.class);
            FunctionCallingStrategy fcStrategy = mock(FunctionCallingStrategy.class);
            FunctionCallingDecision decision =
                    new FunctionCallingDecision(
                            FunctionCallingMode.ENABLED,
                            FunctionCallingOutcome.NOT_APPLICABLE,
                            true,
                            List.of(DeterministicToolKind.FIND_PARAGRAPH_TOOL),
                            List.of("exposed"),
                            Optional.empty(),
                            plan.rewrittenQueryText(),
                            Map.of());
            when(fcPolicy.resolve(any(), any())).thenReturn(Optional.of(decision));
            when(fcStrategy.tryExecute(any(), any(), any()))
                    .thenReturn(
                            new FunctionCallingExecutionResult(
                                    FunctionCallingOutcome.EXECUTED_SUCCESS,
                                    true,
                                    Optional.of(DeterministicToolKind.FIND_PARAGRAPH_TOOL),
                                    "No hay información suficiente en las fuentes.",
                                    Map.of(),
                                    List.of(),
                                    true,
                                    List.of()));

            MonotonicRouteSafetyService safety =
                    new MonotonicRouteSafetyService(new RouteCandidateConstraintValidator());
            RagExecutionOrchestrator orchestrator =
                    orchestratorP15IntegratedWithCampaignResolver(
                            workflowSelector, tools, fcPolicy, fcStrategy, safety, plan);

            var out = orchestrator.execute(ctx);
            assertThat(out.answerText()).isEqualTo(parentAnswer);
            Map<String, Object> telemetry = ChatExecutionTelemetryMapper.fromTrace(out.executionTrace());
            assertThat(telemetry.get("selectedCandidateSource")).isEqualTo("PARENT_P6");
            assertThat(telemetry.get("parentFinalAnswerPreserved")).isEqualTo(true);
            assertThat(telemetry.get("finalAnswerSource")).isEqualTo(ParentFinalAnswerSources.PARENT_P6_FINAL);
        } finally {
            closeItem.run();
        }
    }

    @Test
    void p15_labBenchmark_rejectsParentWhenCampaignOutcomeMissing() {
        Runnable closeItem = LabBenchmarkExecutionContext.openBenchmarkItemScope("RAG-059");
        try {
            String query =
                    "¿Qué reuniones celebradas en agosto hablaron sobre videovigilancia y tuvieron más de 18 asistentes?";
            QueryPlan plan = queryPlan(query, QueryType.FILTER_AND_LIST);
            ExecutionContext ctx = ctx(ragP15());
            String retrievalAnswer =
                    "La reunión celebrada el 25 de agosto de 2025 habló sobre videovigilancia y tuvo más de 18 asistentes.";
            ExecutionWorkflow workflow = mock(ExecutionWorkflow.class);
            when(workflow.workflowName()).thenReturn("ChunkDenseRagWorkflow");
            when(workflow.execute(any()))
                    .thenReturn(
                            RagExecutionResult.withPlaceholderTrace(
                                    retrievalAnswer,
                                    "ChunkDenseRagWorkflow",
                                    true,
                                    false,
                                    ctx.knowledgeSnapshotSelection().orderedSnapshotIds(),
                                    "none",
                                    List.of()));

            WorkflowSelector workflowSelector = mock(WorkflowSelector.class);
            when(workflowSelector.select(any())).thenReturn(workflow);

            DeterministicToolStrategy tools = mock(DeterministicToolStrategy.class);
            when(tools.tryExecute(any(), any()))
                    .thenReturn(
                            DeterministicToolExecutionResult.skipped(
                                    DeterministicToolOutcome.NOT_APPLICABLE, List.of("p15_probe"), Optional.empty()))
                    .thenReturn(
                            DeterministicToolExecutionResult.skipped(
                                    DeterministicToolOutcome.NOT_APPLICABLE, List.of("parent_probe"), Optional.empty()));

            FunctionCallingPolicyResolver fcPolicy = mock(FunctionCallingPolicyResolver.class);
            FunctionCallingStrategy fcStrategy = mock(FunctionCallingStrategy.class);
            FunctionCallingDecision decision =
                    new FunctionCallingDecision(
                            FunctionCallingMode.ENABLED,
                            FunctionCallingOutcome.NOT_APPLICABLE,
                            true,
                            List.of(DeterministicToolKind.FILTER_AND_LIST_TOOL),
                            List.of("exposed"),
                            Optional.empty(),
                            plan.rewrittenQueryText(),
                            Map.of());
            when(fcPolicy.resolve(any(), any())).thenReturn(Optional.of(decision));
            when(fcStrategy.tryExecute(any(), any(), any()))
                    .thenReturn(
                            new FunctionCallingExecutionResult(
                                    FunctionCallingOutcome.EXECUTED_SUCCESS,
                                    true,
                                    Optional.of(DeterministicToolKind.FILTER_AND_LIST_TOOL),
                                    "No hay información suficiente en las fuentes.",
                                    Map.of(),
                                    List.of(),
                                    true,
                                    List.of()));

            MonotonicRouteSafetyService safety =
                    new MonotonicRouteSafetyService(new RouteCandidateConstraintValidator());
            RagExecutionOrchestrator orchestrator =
                    orchestratorP15IntegratedWithCampaignResolver(
                            workflowSelector, tools, fcPolicy, fcStrategy, safety, plan);

            var out = orchestrator.execute(ctx);
            Map<String, Object> telemetry = ChatExecutionTelemetryMapper.fromTrace(out.executionTrace());
            assertThat(telemetry.get("selectedCandidateSource")).isNotEqualTo("PARENT_P7");
            assertThat(telemetry.get("parentCampaignOutcomeMissing")).isEqualTo(true);
            assertThat(telemetry.get("parentFinalAnswerPreserved")).isNotEqualTo(true);
            assertThat(telemetry.get("candidateRejectionReasons").toString())
                    .contains(CampaignParentOutcome.MISSING_PARENT_REJECTION);
        } finally {
            closeItem.run();
        }
    }

    private static RagExecutionOrchestrator orchestratorP9(
            WorkflowSelector workflowSelector,
            FunctionCallingPolicyResolver fcPolicy,
            FunctionCallingStrategy fcStrategy,
            MonotonicRouteSafetyService safety) {
        QueryUnderstandingPipeline qu = mock(QueryUnderstandingPipeline.class);
        ExecutionContextFactory factory = mock(ExecutionContextFactory.class);
        when(qu.buildPlan(any())).thenReturn(plan());
        when(factory.attachQueryPlan(any(), any())).thenAnswer(inv -> inv.getArgument(0));
        when(factory.attachStructuredAnswerPlan(any(), any())).thenAnswer(inv -> inv.getArgument(0));

        ClarificationPolicyResolver clarificationPolicyResolver = mock(ClarificationPolicyResolver.class);
        when(clarificationPolicyResolver.resolve(any(), any()))
                .thenReturn(new ClarificationDecision(false, ClarificationOutcome.NOT_NEEDED, null, ""));

        JudgeStrategy judgeStrategy = mock(JudgeStrategy.class);
        when(judgeStrategy.execute(any(), any(), any(), anyString(), any(), anyString(), any()))
                .thenAnswer(
                        inv ->
                                new JudgeExecutionResult(
                                        false,
                                        JudgeOutcome.NOT_ATTEMPTED,
                                        false,
                                        false,
                                        false,
                                        inv.getArgument(5),
                                        false,
                                        List.of()));

        return new RagExecutionOrchestrator(
                workflowSelector,
                mock(DirectLlmWorkflow.class),
                qu,
                factory,
                mock(DeterministicToolStrategy.class),
                fcPolicy,
                fcStrategy,
                mock(AdvisorPolicyResolver.class),
                mock(AdvisorStrategy.class),
                clarificationPolicyResolver,
                mock(ClarificationStrategy.class),
                mock(AdaptiveRoutingStrategy.class),
                DETERMINISTIC_TOOL_ROUTING,
                FUNCTION_CALLING_ROUTING,
                mock(AdvisorRoutingStrategy.class),
                judgeStrategy,
                mock(StructuredAnswerPlanService.class),
                mock(AnswerVerificationService.class),
                mock(ObjectProvider.class),
                safety,
                mock(ObjectProvider.class), mock(ObjectProvider.class), ConversationRecallGuardTestSupport.neverShortCircuit());
    }

    private static RagExecutionOrchestrator orchestratorP15IntegratedWithCampaignResolver(
            WorkflowSelector workflowSelector,
            DeterministicToolStrategy tools,
            FunctionCallingPolicyResolver fcPolicy,
            FunctionCallingStrategy fcStrategy,
            MonotonicRouteSafetyService safety,
            QueryPlan queryPlan) {
        ObjectProvider<IntegratedParentCampaignOutcomeResolver> campaignResolverProvider =
                mock(ObjectProvider.class);
        when(campaignResolverProvider.getIfAvailable())
                .thenReturn(new IntegratedParentCampaignOutcomeResolver());
        return orchestratorP15Integrated(
                workflowSelector,
                tools,
                fcPolicy,
                fcStrategy,
                safety,
                queryPlan,
                campaignResolverProvider);
    }

    private static RagExecutionOrchestrator orchestratorP15Integrated(
            WorkflowSelector workflowSelector,
            DeterministicToolStrategy tools,
            FunctionCallingPolicyResolver fcPolicy,
            FunctionCallingStrategy fcStrategy,
            MonotonicRouteSafetyService safety,
            QueryPlan queryPlan) {
        ObjectProvider<IntegratedParentCampaignOutcomeResolver> emptyCampaignResolver =
                mock(ObjectProvider.class);
        when(emptyCampaignResolver.getIfAvailable()).thenReturn(null);
        return orchestratorP15Integrated(
                workflowSelector,
                tools,
                fcPolicy,
                fcStrategy,
                safety,
                queryPlan,
                emptyCampaignResolver);
    }

    private static RagExecutionOrchestrator orchestratorP15Integrated(
            WorkflowSelector workflowSelector,
            DeterministicToolStrategy tools,
            FunctionCallingPolicyResolver fcPolicy,
            FunctionCallingStrategy fcStrategy,
            MonotonicRouteSafetyService safety,
            QueryPlan queryPlan,
            ObjectProvider<IntegratedParentCampaignOutcomeResolver> campaignResolverProvider) {
        QueryUnderstandingPipeline qu = mock(QueryUnderstandingPipeline.class);
        ExecutionContextFactory factory = mock(ExecutionContextFactory.class);
        when(qu.buildPlan(any())).thenReturn(queryPlan);
        when(factory.attachQueryPlan(any(), any())).thenAnswer(inv -> inv.getArgument(0));
        when(factory.attachStructuredAnswerPlan(any(), any())).thenAnswer(inv -> inv.getArgument(0));

        ClarificationPolicyResolver clarificationPolicyResolver = mock(ClarificationPolicyResolver.class);
        when(clarificationPolicyResolver.resolve(any(), any()))
                .thenReturn(new ClarificationDecision(false, ClarificationOutcome.NOT_NEEDED, null, ""));

        JudgeStrategy judgeStrategy = mock(JudgeStrategy.class);
        when(judgeStrategy.execute(any(), any(), any(), anyString(), any(), anyString(), any()))
                .thenAnswer(
                        inv ->
                                new JudgeExecutionResult(
                                        false,
                                        JudgeOutcome.NOT_ATTEMPTED,
                                        false,
                                        false,
                                        false,
                                        inv.getArgument(5),
                                        false,
                                        List.of()));

        AdaptiveRoutingStrategy adaptiveRoutingStrategy = mock(AdaptiveRoutingStrategy.class);
        when(adaptiveRoutingStrategy.execute(any(), any()))
                .thenReturn(
                        new AdaptiveRoutingExecutionResult(
                                AdaptiveRoutingOutcome.PRIMARY_ROUTE_SELECTED,
                                true,
                                AdaptiveRouteKind.FUNCTION_CALLING_ROUTE,
                                false,
                                Optional.empty(),
                                false,
                                new RouteExecutionGate(
                                        AdaptiveRouteKind.FUNCTION_CALLING_ROUTE,
                                        false,
                                        false,
                                        true,
                                        false,
                                        true,
                                        Optional.of(AdaptiveRouteKind.RETRIEVAL_WORKFLOW_ROUTE),
                                        false),
                                List.of()));

        DeterministicToolRoutingStrategy deterministicToolRoutingStrategy =
                mock(DeterministicToolRoutingStrategy.class);
        when(deterministicToolRoutingStrategy.execute(any(), any()))
                .thenReturn(
                        new AdaptiveRoutingExecutionResult(
                                AdaptiveRoutingOutcome.PRIMARY_ROUTE_SELECTED_WITH_WORKFLOW_FALLBACK,
                                true,
                                AdaptiveRouteKind.DETERMINISTIC_TOOL_ROUTE,
                                false,
                                Optional.empty(),
                                false,
                                new RouteExecutionGate(
                                        AdaptiveRouteKind.DETERMINISTIC_TOOL_ROUTE,
                                        false,
                                        false,
                                        false,
                                        true,
                                        true,
                                        Optional.of(AdaptiveRouteKind.RETRIEVAL_WORKFLOW_ROUTE),
                                        false),
                                List.of()));

        StructuredAnswerPlanService structuredAnswerPlanService = mock(StructuredAnswerPlanService.class);
        when(structuredAnswerPlanService.plan(any(), any()))
                .thenReturn(
                        new StructuredAnswerPlanService.PlanResult(Optional.empty(), List.of()));

        return new RagExecutionOrchestrator(
                workflowSelector,
                mock(DirectLlmWorkflow.class),
                qu,
                factory,
                tools,
                fcPolicy,
                fcStrategy,
                mock(AdvisorPolicyResolver.class),
                mock(AdvisorStrategy.class),
                clarificationPolicyResolver,
                mock(ClarificationStrategy.class),
                adaptiveRoutingStrategy,
                deterministicToolRoutingStrategy,
                mock(FunctionCallingRoutingStrategy.class),
                mock(AdvisorRoutingStrategy.class),
                judgeStrategy,
                structuredAnswerPlanService,
                mock(AnswerVerificationService.class),
                mock(ObjectProvider.class),
                safety,
                mock(ObjectProvider.class),
                campaignResolverProvider,
                ConversationRecallGuardTestSupport.neverShortCircuit());
    }

    private static RagExecutionOrchestrator orchestratorP7WithCampaignResolver(
            WorkflowSelector workflowSelector,
            DeterministicToolStrategy tools,
            MonotonicRouteSafetyService safety,
            QueryPlan queryPlan) {
        ObjectProvider<IntegratedParentCampaignOutcomeResolver> campaignResolverProvider =
                mock(ObjectProvider.class);
        when(campaignResolverProvider.getIfAvailable())
                .thenReturn(new IntegratedParentCampaignOutcomeResolver());
        return orchestratorWithPlanAndCampaignResolver(
                workflowSelector, tools, safety, queryPlan, false, false, campaignResolverProvider);
    }

    private static RagExecutionOrchestrator orchestratorWithPlanAndCampaignResolver(
            WorkflowSelector workflowSelector,
            DeterministicToolStrategy tools,
            MonotonicRouteSafetyService safety,
            QueryPlan queryPlan,
            boolean integratedAdaptive,
            boolean mockAdaptiveRouting,
            ObjectProvider<IntegratedParentCampaignOutcomeResolver> campaignResolverProvider) {
        QueryUnderstandingPipeline qu = mock(QueryUnderstandingPipeline.class);
        ExecutionContextFactory factory = mock(ExecutionContextFactory.class);
        when(qu.buildPlan(any())).thenReturn(queryPlan);
        when(factory.attachQueryPlan(any(), any())).thenAnswer(inv -> inv.getArgument(0));
        when(factory.attachStructuredAnswerPlan(any(), any())).thenAnswer(inv -> inv.getArgument(0));

        ClarificationPolicyResolver clarificationPolicyResolver = mock(ClarificationPolicyResolver.class);
        when(clarificationPolicyResolver.resolve(any(), any()))
                .thenReturn(new ClarificationDecision(false, ClarificationOutcome.NOT_NEEDED, null, ""));

        JudgeStrategy judgeStrategy = mock(JudgeStrategy.class);
        when(judgeStrategy.execute(any(), any(), any(), anyString(), any(), anyString(), any()))
                .thenAnswer(
                        inv ->
                                new JudgeExecutionResult(
                                        false,
                                        JudgeOutcome.NOT_ATTEMPTED,
                                        false,
                                        false,
                                        false,
                                        inv.getArgument(5),
                                        false,
                                        List.of()));

        AdaptiveRoutingStrategy adaptiveRoutingStrategy = mock(AdaptiveRoutingStrategy.class);
        if (mockAdaptiveRouting) {
            when(adaptiveRoutingStrategy.execute(any(), any()))
                    .thenReturn(
                            new AdaptiveRoutingExecutionResult(
                                    AdaptiveRoutingOutcome.PRIMARY_ROUTE_SELECTED_WITH_WORKFLOW_FALLBACK,
                                    true,
                                    AdaptiveRouteKind.RETRIEVAL_WORKFLOW_ROUTE,
                                    false,
                                    Optional.empty(),
                                    false,
                                    new RouteExecutionGate(
                                            AdaptiveRouteKind.RETRIEVAL_WORKFLOW_ROUTE,
                                            false,
                                            true,
                                            false,
                                            false,
                                            true,
                                            Optional.of(AdaptiveRouteKind.RETRIEVAL_WORKFLOW_ROUTE),
                                            false),
                                    List.of()));
        }

        return new RagExecutionOrchestrator(
                workflowSelector,
                mock(DirectLlmWorkflow.class),
                qu,
                factory,
                tools,
                mock(FunctionCallingPolicyResolver.class),
                mock(FunctionCallingStrategy.class),
                mock(AdvisorPolicyResolver.class),
                mock(AdvisorStrategy.class),
                clarificationPolicyResolver,
                mock(ClarificationStrategy.class),
                integratedAdaptive ? adaptiveRoutingStrategy : mock(AdaptiveRoutingStrategy.class),
                DETERMINISTIC_TOOL_ROUTING,
                mock(FunctionCallingRoutingStrategy.class),
                mock(AdvisorRoutingStrategy.class),
                judgeStrategy,
                mock(StructuredAnswerPlanService.class),
                mock(AnswerVerificationService.class),
                mock(ObjectProvider.class),
                safety,
                mock(ObjectProvider.class),
                campaignResolverProvider,
                ConversationRecallGuardTestSupport.neverShortCircuit());
    }

    private static RagExecutionOrchestrator orchestratorWithPlan(
            WorkflowSelector workflowSelector,
            DeterministicToolStrategy tools,
            MonotonicRouteSafetyService safety,
            QueryPlan queryPlan,
            boolean integratedAdaptive,
            boolean mockAdaptiveRouting) {
        QueryUnderstandingPipeline qu = mock(QueryUnderstandingPipeline.class);
        ExecutionContextFactory factory = mock(ExecutionContextFactory.class);
        when(qu.buildPlan(any())).thenReturn(queryPlan);
        when(factory.attachQueryPlan(any(), any())).thenAnswer(inv -> inv.getArgument(0));
        when(factory.attachStructuredAnswerPlan(any(), any())).thenAnswer(inv -> inv.getArgument(0));

        ClarificationPolicyResolver clarificationPolicyResolver = mock(ClarificationPolicyResolver.class);
        when(clarificationPolicyResolver.resolve(any(), any()))
                .thenReturn(new ClarificationDecision(false, ClarificationOutcome.NOT_NEEDED, null, ""));

        JudgeStrategy judgeStrategy = mock(JudgeStrategy.class);
        when(judgeStrategy.execute(any(), any(), any(), anyString(), any(), anyString(), any()))
                .thenAnswer(
                        inv ->
                                new JudgeExecutionResult(
                                        false,
                                        JudgeOutcome.NOT_ATTEMPTED,
                                        false,
                                        false,
                                        false,
                                        inv.getArgument(5),
                                        false,
                                        List.of()));

        AdaptiveRoutingStrategy adaptiveRoutingStrategy = mock(AdaptiveRoutingStrategy.class);
        if (mockAdaptiveRouting) {
            when(adaptiveRoutingStrategy.execute(any(), any()))
                    .thenReturn(
                            new AdaptiveRoutingExecutionResult(
                                    AdaptiveRoutingOutcome.PRIMARY_ROUTE_SELECTED_WITH_WORKFLOW_FALLBACK,
                                    true,
                                    AdaptiveRouteKind.RETRIEVAL_WORKFLOW_ROUTE,
                                    false,
                                    Optional.empty(),
                                    false,
                                    new RouteExecutionGate(
                                            AdaptiveRouteKind.RETRIEVAL_WORKFLOW_ROUTE,
                                            false,
                                            true,
                                            false,
                                            false,
                                            true,
                                            Optional.of(AdaptiveRouteKind.RETRIEVAL_WORKFLOW_ROUTE),
                                            false),
                                    List.of()));
        }

        return new RagExecutionOrchestrator(
                workflowSelector,
                mock(DirectLlmWorkflow.class),
                qu,
                factory,
                tools,
                mock(FunctionCallingPolicyResolver.class),
                mock(FunctionCallingStrategy.class),
                mock(AdvisorPolicyResolver.class),
                mock(AdvisorStrategy.class),
                clarificationPolicyResolver,
                mock(ClarificationStrategy.class),
                integratedAdaptive ? adaptiveRoutingStrategy : mock(AdaptiveRoutingStrategy.class),
                DETERMINISTIC_TOOL_ROUTING,
                mock(FunctionCallingRoutingStrategy.class),
                mock(AdvisorRoutingStrategy.class),
                judgeStrategy,
                mock(StructuredAnswerPlanService.class),
                mock(AnswerVerificationService.class),
                mock(ObjectProvider.class),
                safety,
                mock(ObjectProvider.class), mock(ObjectProvider.class), ConversationRecallGuardTestSupport.neverShortCircuit());
    }

    private static RagExecutionOrchestrator orchestrator(
            WorkflowSelector workflowSelector,
            DeterministicToolStrategy tools,
            MonotonicRouteSafetyService safety,
            boolean integratedAdaptive,
            boolean mockAdaptiveRouting) {
        QueryUnderstandingPipeline qu = mock(QueryUnderstandingPipeline.class);
        ExecutionContextFactory factory = mock(ExecutionContextFactory.class);
        when(qu.buildPlan(any())).thenReturn(plan());
        when(factory.attachQueryPlan(any(), any())).thenAnswer(inv -> inv.getArgument(0));
        when(factory.attachStructuredAnswerPlan(any(), any())).thenAnswer(inv -> inv.getArgument(0));

        ClarificationPolicyResolver clarificationPolicyResolver = mock(ClarificationPolicyResolver.class);
        when(clarificationPolicyResolver.resolve(any(), any()))
                .thenReturn(new ClarificationDecision(false, ClarificationOutcome.NOT_NEEDED, null, ""));

        JudgeStrategy judgeStrategy = mock(JudgeStrategy.class);
        when(judgeStrategy.execute(any(), any(), any(), anyString(), any(), anyString(), any()))
                .thenAnswer(
                        inv ->
                                new JudgeExecutionResult(
                                        false,
                                        JudgeOutcome.NOT_ATTEMPTED,
                                        false,
                                        false,
                                        false,
                                        inv.getArgument(5),
                                        false,
                                        List.of()));

        AdaptiveRoutingStrategy adaptiveRoutingStrategy = mock(AdaptiveRoutingStrategy.class);
        if (mockAdaptiveRouting) {
            when(adaptiveRoutingStrategy.execute(any(), any()))
                    .thenReturn(
                            new AdaptiveRoutingExecutionResult(
                                    AdaptiveRoutingOutcome.PRIMARY_ROUTE_SELECTED_WITH_WORKFLOW_FALLBACK,
                                    true,
                                    AdaptiveRouteKind.RETRIEVAL_WORKFLOW_ROUTE,
                                    false,
                                    Optional.empty(),
                                    false,
                                    new RouteExecutionGate(
                                            AdaptiveRouteKind.RETRIEVAL_WORKFLOW_ROUTE,
                                            false,
                                            true,
                                            false,
                                            false,
                                            true,
                                            Optional.of(AdaptiveRouteKind.RETRIEVAL_WORKFLOW_ROUTE),
                                            false),
                                    List.of()));
        }

        return new RagExecutionOrchestrator(
                workflowSelector,
                mock(DirectLlmWorkflow.class),
                qu,
                factory,
                tools,
                mock(FunctionCallingPolicyResolver.class),
                mock(FunctionCallingStrategy.class),
                mock(AdvisorPolicyResolver.class),
                mock(AdvisorStrategy.class),
                clarificationPolicyResolver,
                mock(ClarificationStrategy.class),
                integratedAdaptive ? adaptiveRoutingStrategy : mock(AdaptiveRoutingStrategy.class),
                DETERMINISTIC_TOOL_ROUTING,
                mock(FunctionCallingRoutingStrategy.class),
                mock(AdvisorRoutingStrategy.class),
                judgeStrategy,
                mock(StructuredAnswerPlanService.class),
                mock(AnswerVerificationService.class),
                mock(ObjectProvider.class),
                safety,
                mock(ObjectProvider.class), mock(ObjectProvider.class), ConversationRecallGuardTestSupport.neverShortCircuit());
    }

    private static RagConfig ragP9() {
        return new RagConfig(
                true,
                true,
                true,
                true,
                false,
                true,
                true,
                true,
                true,
                false,
                false,
                false,
                false,
                false,
                false,
                12,
                0.6,
                "llm",
                "emb",
                "cls",
                "SIMPLE",
                false,
                32_000,
                24_000,
                false,
                MaterializationStrategy.HYBRID);
    }

    private static RagConfig ragP7() {
        return new RagConfig(
                false,
                false,
                true,
                true,
                false,
                false,
                false,
                false,
                true,
                false,
                false,
                false,
                false,
                false,
                true,
                10,
                0.7,
                "llm",
                "emb",
                "cls",
                "SIMPLE",
                false,
                32_000,
                24_000,
                false,
                MaterializationStrategy.CHUNK_LEVEL);
    }

    private static RagConfig ragP15() {
        return new RagConfig(
                false,
                false,
                true,
                true,
                false,
                true,
                true,
                true,
                true,
                false,
                false,
                false,
                true,
                false,
                false,
                12,
                0.6,
                "llm",
                "emb",
                "cls",
                "SIMPLE",
                false,
                32_000,
                24_000,
                false,
                MaterializationStrategy.HYBRID);
    }

    private static ExecutionContext ctx(RagConfig rag) {
        UUID id = UUID.randomUUID();
        UUID snapshotId = UUID.randomUUID();
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
        KnowledgeSnapshotSelection snapshots =
                new KnowledgeSnapshotSelection(
                        List.of(snapshotId),
                        Optional.of(snapshotId),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty());
        return new ExecutionContext(
                id,
                id,
                id,
                "q",
                RuntimeOperationKind.CHAT_MESSAGE,
                resolved,
                "sys",
                snapshots,
                Optional.empty(),
                Optional.empty(),
                "corr",
                List.of("all"),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                "q",
                "q",
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

    private static QueryPlan queryPlan(String query, QueryType queryType) {
        return new QueryPlan(
                QueryPlan.VERSION_P6_QU_CORE_V1,
                query,
                query,
                query,
                query,
                queryType.name(),
                Optional.of(queryType),
                ClassifierStatus.OK,
                QueryIntent.UNKNOWN,
                Map.of(),
                List.of(),
                List.of(),
                EntityExtractionResult.emptyWithNote(""),
                StructuredRewriteResult.identityDisabled(query, ""),
                ExpectedAnswerShape.UNKNOWN,
                new AmbiguityAssessment(AmbiguityStatus.SUFFICIENT, List.of(), List.of()),
                "corr",
                "",
                List.of());
    }

    private static QueryPlan plan() {
        return new QueryPlan(
                QueryPlan.VERSION_P6_QU_CORE_V1,
                "Verifica si se mencionó la limpieza en alguna reunión celebrada en 2026.",
                "Verifica si se mencionó la limpieza en alguna reunión celebrada en 2026.",
                "norm",
                "Verifica si se mencionó la limpieza en alguna reunión celebrada en 2026.",
                "BOOLEAN_QUERY",
                Optional.empty(),
                ClassifierStatus.OK,
                QueryIntent.UNKNOWN,
                Map.of(),
                List.of(),
                List.of(),
                EntityExtractionResult.emptyWithNote(""),
                StructuredRewriteResult.identityDisabled("norm", ""),
                ExpectedAnswerShape.UNKNOWN,
                new AmbiguityAssessment(AmbiguityStatus.SUFFICIENT, List.of(), List.of()),
                "corr",
                "",
                List.of());
    }
}
