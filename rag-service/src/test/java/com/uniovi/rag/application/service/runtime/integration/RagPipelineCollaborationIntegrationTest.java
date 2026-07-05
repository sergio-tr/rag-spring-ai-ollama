package com.uniovi.rag.application.service.runtime.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.uniovi.rag.application.service.runtime.DeterministicToolTerminalAnswerGuard;
import com.uniovi.rag.application.service.runtime.clarification.ClarificationPolicyResolver;
import com.uniovi.rag.application.service.runtime.clarification.ClarificationQuestionGenerator;
import com.uniovi.rag.application.service.runtime.memory.ConversationRecallGuard;
import com.uniovi.rag.application.service.runtime.query.DefaultAmbiguityAssessmentService;
import com.uniovi.rag.application.service.runtime.query.DefaultExpectedAnswerShapeResolver;
import com.uniovi.rag.application.service.runtime.query.DefaultQueryUnderstandingPipeline;
import com.uniovi.rag.application.service.runtime.query.DefaultQueryClassifierAdapter;
import com.uniovi.rag.application.service.runtime.query.DefaultQueryIntentResolver;
import com.uniovi.rag.application.service.runtime.query.expand.QueryExpander;
import com.uniovi.rag.application.service.runtime.query.QueryExpansionStage;
import com.uniovi.rag.application.service.runtime.query.NamedEntityExtractionAdapter;
import com.uniovi.rag.application.service.runtime.query.QueryClassifierAdapter;
import com.uniovi.rag.application.service.runtime.query.StructuredQueryRewriter;
import com.uniovi.rag.application.service.runtime.routing.DeterministicToolRoutingPolicy;
import com.uniovi.rag.application.service.runtime.routing.safety.MonotonicRouteSafetyService;
import com.uniovi.rag.application.service.runtime.routing.safety.RouteCandidateConstraintValidator;
import com.uniovi.rag.application.service.runtime.tool.DefaultDeterministicToolResolver;
import com.uniovi.rag.configuration.RagClassifierProperties;
import com.uniovi.rag.domain.config.capability.CapabilitySet;
import com.uniovi.rag.domain.config.indexing.ReindexImpact;
import com.uniovi.rag.domain.config.prompt.SystemPromptLayers;
import com.uniovi.rag.domain.config.runtime.ConfigProvenance;
import com.uniovi.rag.domain.config.runtime.ResolvedRuntimeConfig;
import com.uniovi.rag.domain.config.validation.CompatibilityResult;
import com.uniovi.rag.domain.knowledge.MaterializationStrategy;
import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.domain.runtime.clarification.ClarificationDecision;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.engine.KnowledgeSnapshotSelection;
import com.uniovi.rag.domain.runtime.engine.RuntimeOperationKind;
import com.uniovi.rag.domain.runtime.memory.ConversationMemoryOutcome;
import com.uniovi.rag.domain.runtime.query.AmbiguityAssessment;
import com.uniovi.rag.domain.runtime.query.AmbiguityStatus;
import com.uniovi.rag.domain.runtime.query.ClassifierStatus;
import com.uniovi.rag.domain.runtime.query.EntityExtractionResult;
import com.uniovi.rag.domain.runtime.query.ExpectedAnswerShape;
import com.uniovi.rag.domain.runtime.query.NormalizedQuery;
import com.uniovi.rag.domain.runtime.query.QueryIntent;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.query.StructuredRewriteResult;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRoutingDecision;
import com.uniovi.rag.application.service.runtime.routing.safety.RouteCandidateValidationResult;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRouteKind;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRoutingOutcome;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolExecutionResult;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolKind;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolOutcome;
import com.uniovi.rag.infrastructure.classifier.ClassifierInferenceResponse;
import com.uniovi.rag.infrastructure.classifier.QueryClassifier;
import com.uniovi.rag.infrastructure.observability.RuntimeObservability;
import com.uniovi.rag.testsupport.ConversationRecallGuardTestSupport;
import io.micrometer.tracing.Tracer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Component-level integration: real collaborators wired without Spring context or live Ollama.
 * Represents Stage C acceptance paths at the collaboration layer (not live harness scripts).
 */
class RagPipelineCollaborationIntegrationTest {

    private static final String GAS_LEAK_QUERY = "¿Qué se comentó respecto a la fuga de gas?";
    private static final String UNDATED_COUNT_QUERY = "¿Cuántos participantes asistieron?";
    private static final String CE02_QUERY =
            "¿En qué reuniones hubo exactamente 21 asistentes y qué se decidió en esa reunión?";
    private static final String BQ02_QUERY =
            "Verifica si se mencionó la limpieza en alguna reunión celebrada en 2026.";

    @BeforeEach
    void enableTerminalGuard() {
        DeterministicToolTerminalAnswerGuard.setAcceptanceGuardTestOverride(true);
    }

    @AfterEach
    void clearTerminalGuard() {
        DeterministicToolTerminalAnswerGuard.setAcceptanceGuardTestOverride(null);
    }

    @Test
    void queryUnderstanding_classifierOverride_routesToFindParagraphTool() {
        QueryClassifier classifier = mock(QueryClassifier.class);
        when(classifier.classifyInference(GAS_LEAK_QUERY, "cls"))
                .thenReturn(new ClassifierInferenceResponse("SUMMARIZE_MEETING", 0.91, "hash", List.of()));

        DefaultQueryUnderstandingPipeline pipeline = pipelineWithClassifier(classifier);
        ExecutionContext ctx = ctx(demoBestRag(), GAS_LEAK_QUERY);

        QueryPlan plan = pipeline.buildPlan(ctx);

        assertThat(plan.classifierQueryType()).contains(QueryType.FIND_PARAGRAPH);
        assertThat(plan.pipelineNotes().stream().anyMatch(n -> n.contains("RULE_OVERRIDE") || n.contains("DETERMINISTIC")))
                .isTrue();

        assertThat(new DeterministicToolRoutingPolicy().resolve(demoBestRag(), plan).primaryRouteKind())
                .isEqualTo(AdaptiveRouteKind.DETERMINISTIC_TOOL_ROUTE);

        var toolDecision = new DefaultDeterministicToolResolver().resolve(ctx, plan);
        assertThat(toolDecision.outcome()).isEqualTo(DeterministicToolOutcome.SELECTED);
        assertThat(toolDecision.selectedToolKind()).contains(DeterministicToolKind.FIND_PARAGRAPH_TOOL);
    }

    @Test
    void monotonicValidator_terminalGuard_preservesSafeDeterministicAnswer() {
        String query =
                "¿Qué reuniones celebradas en agosto hablaron sobre videovigilancia y tuvieron más de 18 asistentes?";
        String answer =
                "La reunión del 25/08/2026 (ACTA 6) trató videovigilancia y tuvo 19 asistentes.";
        QueryPlan plan = filterListPlan(query);
        DeterministicToolExecutionResult toolResult =
                new DeterministicToolExecutionResult(
                        Optional.of(DeterministicToolKind.FILTER_AND_LIST_TOOL),
                        DeterministicToolOutcome.EXECUTED_SUCCESS,
                        true,
                        answer,
                        Map.of(),
                        List.of());

        MonotonicRouteSafetyService safety =
                new MonotonicRouteSafetyService(new RouteCandidateConstraintValidator());
        var validation = safety.validateToolResult(plan, toolResult);

        assertThat(validation.safe()).isTrue();
        assertThat(DeterministicToolTerminalAnswerGuard.shouldFinishTerminal(plan, toolResult, validation))
                .isTrue();
        assertThat(DeterministicToolTerminalAnswerGuard.shouldMarkDeterministicToolFinal(plan, validation))
                .isTrue();
    }

    @Test
    void conversationRecallGuard_doesNotBypassClarificationForUndatedParticipantCount() {
        ConversationRecallGuard recallGuard = ConversationRecallGuardTestSupport.withEmptyHistory();
        ClarificationPolicyResolver clarification =
                new ClarificationPolicyResolver(new ClarificationQuestionGenerator());
        DefaultAmbiguityAssessmentService ambiguity = new DefaultAmbiguityAssessmentService();

        ExecutionContext ctx = ctx(demoBestRag(), UNDATED_COUNT_QUERY);
        assertThat(recallGuard.shouldShortCircuit(ctx)).isFalse();

        var assessment =
                ambiguity.assess(
                        new NormalizedQuery("raw", UNDATED_COUNT_QUERY, List.of()),
                        Optional.of(QueryType.COUNT_DOCUMENTS),
                        QueryType.COUNT_DOCUMENTS.name(),
                        ClassifierStatus.OK,
                        StructuredRewriteResult.identityFallback(UNDATED_COUNT_QUERY, null),
                        EntityExtractionResult.emptyWithNote(null));
        assertThat(assessment.status()).isEqualTo(AmbiguityStatus.MISSING_INFORMATION);

        QueryPlan plan = undatedCountPlan(assessment);
        ClarificationDecision decision = clarification.resolve(ctx, plan);
        assertThat(decision.ask()).isTrue();
    }

    @Test
    void conversationMetaQuery_shortCircuitsBeforeClarificationPolicy() {
        ConversationRecallGuard recallGuard = ConversationRecallGuardTestSupport.withEmptyHistory();
        ExecutionContext ctx = ctx(demoBestRag(), "¿De qué hablamos antes?");

        assertThat(recallGuard.shouldShortCircuit(ctx)).isTrue();
        assertThat(ConversationRecallGuard.noEligibleHistoryResponse()).containsIgnoringCase("no hemos");
    }

    @Test
    void queryPlanSlots_deterministicResolver_selectsCountAndExplainForCe02() {
        QueryClassifier classifier = mock(QueryClassifier.class);
        when(classifier.classifyInference(CE02_QUERY, "cls"))
                .thenReturn(new ClassifierInferenceResponse("FIND_PARAGRAPH", 0.82, "hash", List.of()));

        DefaultQueryUnderstandingPipeline pipeline = pipelineWithClassifier(classifier);
        ExecutionContext ctx = ctx(demoBestRag(), CE02_QUERY);
        QueryPlan plan = pipeline.buildPlan(ctx);

        assertThat(plan.classifierQueryType()).contains(QueryType.COUNT_AND_EXPLAIN);

        assertThat(new DeterministicToolRoutingPolicy().resolve(demoBestRag(), plan).primaryRouteKind())
                .isEqualTo(AdaptiveRouteKind.DETERMINISTIC_TOOL_ROUTE);

        var toolDecision = new DefaultDeterministicToolResolver().resolve(ctx, plan);
        assertThat(toolDecision.outcome()).isEqualTo(DeterministicToolOutcome.SELECTED);
        assertThat(toolDecision.selectedToolKind()).contains(DeterministicToolKind.COUNT_AND_EXPLAIN_TOOL);
    }

    @Test
    void stageC_bq02_classifierOverrideAndRoutingResolvesToBooleanQueryTool() {
        QueryClassifier classifier = mock(QueryClassifier.class);
        when(classifier.classifyInference(BQ02_QUERY, "cls"))
                .thenReturn(new ClassifierInferenceResponse("FIND_PARAGRAPH", 0.88, "hash", List.of()));

        DefaultQueryUnderstandingPipeline pipeline = pipelineWithClassifier(classifier);
        ExecutionContext ctx = ctx(demoBestRag(), BQ02_QUERY);
        QueryPlan plan = pipeline.buildPlan(ctx);

        assertThat(plan.classifierQueryType()).contains(QueryType.BOOLEAN_QUERY);

        AdaptiveRoutingDecision routing = new DeterministicToolRoutingPolicy().resolve(demoBestRag(), plan);
        assertThat(routing.primaryRouteKind()).isEqualTo(AdaptiveRouteKind.DETERMINISTIC_TOOL_ROUTE);

        var toolDecision = new DefaultDeterministicToolResolver().resolve(ctx, plan);
        assertThat(toolDecision.selectedToolKind()).contains(DeterministicToolKind.BOOLEAN_QUERY_TOOL);
    }

    @Test
    void demoBestPreset_profileEnablesStructuredDeterministicToolRouting() {
        RagConfig rag = demoBestRag();
        assertThat(rag.toolsEnabled()).isTrue();

        QueryPlan plan = typedPlan(QueryType.COUNT_DOCUMENTS, "¿Cuántas actas mencionan el ascensor?");
        assertThat(new DeterministicToolRoutingPolicy().resolve(rag, plan).primaryRouteKind())
                .isEqualTo(AdaptiveRouteKind.DETERMINISTIC_TOOL_ROUTE);
    }

    @Test
    void negativeEvidence_hedgedAnswer_blockedByMonotonicAndTerminalGuard() {
        String hedged =
                "Se menciona la posibilidad de instalar cámaras de seguridad relacionadas con la fuga de gas."
                        + " No se proporciona información adicional sobre la fuga de gas en los minutos.";
        QueryPlan plan = findParagraphPlan(GAS_LEAK_QUERY);
        DeterministicToolExecutionResult toolResult =
                new DeterministicToolExecutionResult(
                        Optional.of(DeterministicToolKind.FIND_PARAGRAPH_TOOL),
                        DeterministicToolOutcome.EXECUTED_SUCCESS,
                        true,
                        hedged,
                        Map.of(),
                        List.of());

        MonotonicRouteSafetyService safety =
                new MonotonicRouteSafetyService(new RouteCandidateConstraintValidator());
        RouteCandidateValidationResult validation = safety.validateToolResult(plan, toolResult);

        assertThat(validation.safe()).isFalse();
        assertThat(validation.rejectionReasons()).anyMatch(r -> r.contains("find_paragraph_hedged"));
        assertThat(DeterministicToolTerminalAnswerGuard.shouldFinishTerminal(plan, toolResult, validation))
                .isFalse();
        assertThat(DeterministicToolTerminalAnswerGuard.shouldMarkDeterministicToolFinal(plan, validation))
                .isFalse();
    }

    @Test
    void stageC_ce02_negativeCorpusAnswer_passesMonotonicAndTerminalGuard() {
        String answer =
                "No existen reuniones con exactamente 21 asistentes en las actas disponibles.";
        QueryPlan plan = countAndExplainPlan(CE02_QUERY);
        DeterministicToolExecutionResult toolResult =
                new DeterministicToolExecutionResult(
                        Optional.of(DeterministicToolKind.COUNT_AND_EXPLAIN_TOOL),
                        DeterministicToolOutcome.EXECUTED_SUCCESS,
                        true,
                        answer,
                        Map.of(),
                        List.of());

        MonotonicRouteSafetyService safety =
                new MonotonicRouteSafetyService(new RouteCandidateConstraintValidator());
        RouteCandidateValidationResult validation = safety.validateToolResult(plan, toolResult);

        assertThat(validation.safe()).isTrue();
        assertThat(DeterministicToolTerminalAnswerGuard.shouldFinishTerminal(plan, toolResult, validation))
                .isTrue();
        assertThat(answer.toLowerCase()).doesNotContain("se decidió");
    }

    private static QueryPlan typedPlan(QueryType queryType, String query) {
        return new QueryPlan(
                QueryPlan.VERSION_P6_QU_CORE_V1,
                query,
                query,
                query,
                query,
                queryType.name(),
                Optional.of(queryType),
                ClassifierStatus.OK,
                QueryIntent.COUNT,
                Map.of(),
                List.of(),
                List.of(),
                EntityExtractionResult.emptyWithNote(""),
                StructuredRewriteResult.identityFallback(query, null),
                ExpectedAnswerShape.SCALAR_COUNT,
                AmbiguityAssessment.sufficient(),
                "corr",
                "default",
                List.of());
    }

    private static QueryPlan findParagraphPlan(String query) {
        return new QueryPlan(
                QueryPlan.VERSION_P6_QU_CORE_V1,
                query,
                query,
                query,
                query,
                QueryType.FIND_PARAGRAPH.name(),
                Optional.of(QueryType.FIND_PARAGRAPH),
                ClassifierStatus.OK,
                QueryIntent.FIND,
                Map.of(),
                List.of(),
                List.of(),
                EntityExtractionResult.emptyWithNote(""),
                StructuredRewriteResult.identityFallback(query, null),
                ExpectedAnswerShape.PARAGRAPH,
                AmbiguityAssessment.sufficient(),
                "corr",
                "default",
                List.of());
    }

    private static QueryPlan countAndExplainPlan(String query) {
        return new QueryPlan(
                QueryPlan.VERSION_P6_QU_CORE_V1,
                query,
                query,
                query,
                query,
                QueryType.COUNT_AND_EXPLAIN.name(),
                Optional.of(QueryType.COUNT_AND_EXPLAIN),
                ClassifierStatus.OK,
                QueryIntent.EXPLAIN,
                Map.of(),
                List.of(),
                List.of(),
                EntityExtractionResult.emptyWithNote(""),
                StructuredRewriteResult.identityFallback(query, null),
                ExpectedAnswerShape.PARAGRAPH,
                AmbiguityAssessment.sufficient(),
                "corr",
                "default",
                List.of());
    }

    private static DefaultQueryUnderstandingPipeline pipelineWithClassifier(QueryClassifier classifier) {
        @SuppressWarnings("unchecked")
        ObjectProvider<RuntimeObservability> obs = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<Tracer> tracer = mock(ObjectProvider.class);
        RagClassifierProperties props = new RagClassifierProperties();
        props.setConfidenceThreshold(0.55);
        QueryClassifierAdapter adapter =
                new DefaultQueryClassifierAdapter(classifier, props, obs, tracer);

        NamedEntityExtractionAdapter ner = mock(NamedEntityExtractionAdapter.class);
        when(ner.extract(any(), anyString())).thenReturn(EntityExtractionResult.emptyWithNote(""));

        StructuredQueryRewriter rewriter = mock(StructuredQueryRewriter.class);
        when(rewriter.rewrite(any(), any(), anyString(), any(), any(), any()))
                .thenAnswer(
                        inv ->
                                StructuredRewriteResult.identityFallback(
                                        ((NormalizedQuery) inv.getArgument(1)).normalizedText(), null));

        return new DefaultQueryUnderstandingPipeline(
                adapter,
                ner,
                rewriter,
                new DefaultQueryIntentResolver(),
                new DefaultExpectedAnswerShapeResolver(),
                new DefaultAmbiguityAssessmentService(),
                new QueryExpansionStage(mock(QueryExpander.class)));
    }

    private static QueryPlan filterListPlan(String query) {
        return new QueryPlan(
                QueryPlan.VERSION_P6_QU_CORE_V1,
                query,
                query,
                query,
                query,
                QueryType.FILTER_AND_LIST.name(),
                Optional.of(QueryType.FILTER_AND_LIST),
                ClassifierStatus.OK,
                QueryIntent.LIST,
                Map.of(),
                List.of(),
                List.of(),
                EntityExtractionResult.emptyWithNote(""),
                StructuredRewriteResult.identityFallback(query, null),
                ExpectedAnswerShape.LIST,
                AmbiguityAssessment.sufficient(),
                "corr",
                "default",
                List.of());
    }

    private static QueryPlan undatedCountPlan(AmbiguityAssessment assessment) {
        return new QueryPlan(
                QueryPlan.VERSION_P6_QU_CORE_V1,
                UNDATED_COUNT_QUERY,
                UNDATED_COUNT_QUERY,
                UNDATED_COUNT_QUERY,
                UNDATED_COUNT_QUERY,
                QueryType.COUNT_DOCUMENTS.name(),
                Optional.of(QueryType.COUNT_DOCUMENTS),
                ClassifierStatus.OK,
                QueryIntent.COUNT,
                Map.of(),
                List.of(),
                List.of(),
                EntityExtractionResult.emptyWithNote(null),
                StructuredRewriteResult.identityFallback(UNDATED_COUNT_QUERY, null),
                ExpectedAnswerShape.SCALAR_COUNT,
                assessment,
                "corr",
                "default",
                List.of());
    }

    private static RagConfig demoBestRag() {
        return new RagConfig(
                false,
                false,
                true,
                false,
                false,
                false,
                false,
                true,
                true,
                false,
                false,
                false,
                false,
                false,
                true,
                5,
                0.2,
                "llm",
                "emb",
                "cls",
                "reason",
                false,
                RagConfig.DEFAULT_NAIVE_FULL_CORPUS_MAX_CHARS,
                RagConfig.DEFAULT_ADVANCED_RETRIEVAL_MAX_CONTEXT_CHARS,
                false,
                MaterializationStrategy.CHUNK_LEVEL);
    }

    private static ExecutionContext ctx(RagConfig rag, String query) {
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
        return new ExecutionContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                query,
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
                Optional.empty(),
                query,
                query,
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
}
