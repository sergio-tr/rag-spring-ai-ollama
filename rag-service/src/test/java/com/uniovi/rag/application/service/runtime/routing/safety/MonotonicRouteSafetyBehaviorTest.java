package com.uniovi.rag.application.service.runtime.routing.safety;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniovi.rag.application.service.runtime.DeterministicToolTerminalAnswerGuard;
import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.domain.runtime.query.*;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolExecutionResult;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolKind;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolOutcome;
import com.uniovi.rag.tool.metadata.StructuredMinuteMetadataSupport;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** End-to-end monotonic route safety with terminal answer guard for deterministic tool results. */
class MonotonicRouteSafetyBehaviorTest {

    private final RouteCandidateConstraintValidator validator = new RouteCandidateConstraintValidator();
    private final MonotonicRouteSafetyService safety = new MonotonicRouteSafetyService(validator);

    @BeforeEach
    void enableGuard() {
        DeterministicToolTerminalAnswerGuard.setAcceptanceGuardTestOverride(true);
    }

    @AfterEach
    void clearGuard() {
        DeterministicToolTerminalAnswerGuard.setAcceptanceGuardTestOverride(null);
    }

    @Test
    void filterAndListAugustSlashDateAnswer_passesMonotonicSafetyAndTerminates() {
        String query =
                "¿Qué reuniones celebradas en agosto hablaron sobre videovigilancia y tuvieron más de 18 asistentes?";
        String answer =
                "La reunión del 25/08/2026 (ACTA 6) trató videovigilancia y tuvo 19 asistentes.";
        QueryPlan plan = filterListPlan(query);
        DeterministicToolExecutionResult toolResult = toolResult(DeterministicToolKind.FILTER_AND_LIST_TOOL, answer);

        RouteCandidateValidationResult validation = safety.validateToolResult(plan, toolResult);

        assertThat(validation.safe()).isTrue();
        assertThat(DeterministicToolTerminalAnswerGuard.shouldFinishTerminal(plan, toolResult, validation))
                .isTrue();
        assertThat(DeterministicToolTerminalAnswerGuard.shouldMarkDeterministicToolFinal(plan, validation))
                .isTrue();
    }

    @Test
    void filterAndListCompoundMonthQuery_extractsAugustTopicAndAttendeeSignals() {
        String query =
                "¿Qué reuniones celebradas en agosto hablaron sobre videovigilancia y tuvieron más de 18 asistentes?";
        QueryPlan plan = filterListPlan(query);
        var signals = QueryConstraintSignals.fromPlan(plan);

        assertThat(signals.monthNames()).contains("agosto");
        assertThat(signals.topicTokens()).contains("videovigilancia");
        assertThat(signals.filterAndList()).isTrue();
        assertThat(plan.classifierQueryType()).contains(QueryType.FILTER_AND_LIST);
    }

    @Test
    void findParagraphGasLeakNegative_passesMonotonicSafetyAndTerminates() {
        String query = "¿Qué se comentó respecto a la fuga de gas?";
        String answer = "No se encuentra ninguna mención a una fuga de gas en las actas disponibles.";
        QueryPlan plan = findParagraphPlan(query);
        DeterministicToolExecutionResult toolResult = toolResult(DeterministicToolKind.FIND_PARAGRAPH_TOOL, answer);

        RouteCandidateValidationResult validation = safety.validateToolResult(plan, toolResult);

        assertThat(validation.safe()).isTrue();
        assertThat(DeterministicToolTerminalAnswerGuard.shouldFinishTerminal(plan, toolResult, validation))
                .isTrue();
        assertThat(DeterministicToolTerminalAnswerGuard.shouldMarkDeterministicToolFinal(plan, validation))
                .isTrue();
    }

    @Test
    void findParagraphGasLeakQuery_marksAbsenceLikelyWithTopicToken() {
        String query = "¿Qué se comentó respecto a la fuga de gas?";
        QueryPlan plan = findParagraphPlan(query);
        var signals = QueryConstraintSignals.fromPlan(plan);

        assertThat(signals.findParagraphLookup()).isTrue();
        assertThat(signals.absenceLikely()).isTrue();
        assertThat(signals.topicTokens()).anyMatch(t -> t.contains("fuga"));
    }

    @Test
    void findParagraphHedgedGasLeakAnswer_rejectedByMonotonicSafety() {
        String query = "¿Qué se comentó respecto a la fuga de gas?";
        String hedged =
                "Se menciona la posibilidad de instalar cámaras de seguridad relacionadas con la fuga de gas."
                        + " No se proporciona información adicional sobre la fuga de gas en los minutos.";
        QueryPlan plan = findParagraphPlan(query);
        DeterministicToolExecutionResult toolResult = toolResult(DeterministicToolKind.FIND_PARAGRAPH_TOOL, hedged);

        RouteCandidateValidationResult validation = safety.validateToolResult(plan, toolResult);

        assertThat(validation.safe()).isFalse();
        assertThat(validation.rejectionReasons()).anyMatch(r -> r.contains("find_paragraph_hedged"));
    }

    @Test
    void findParagraphLimpiezaAnswer_passesMonotonicSafetyAndTerminates() {
        String query = "¿Qué se dijo en relación a la limpieza de las zonas comunes?";
        String answer =
                "En el acta del 25/02/2025 se plantea la necesidad de mejorar la limpieza en las zonas comunes. Se aprueba la contratación de un nuevo servicio de limpieza con mayor frecuencia.";
        QueryPlan plan = findParagraphPlan(query);
        DeterministicToolExecutionResult toolResult = toolResult(DeterministicToolKind.FIND_PARAGRAPH_TOOL, answer);

        RouteCandidateValidationResult validation = safety.validateToolResult(plan, toolResult);

        assertThat(validation.safe()).isTrue();
        assertThat(DeterministicToolTerminalAnswerGuard.shouldFinishTerminal(plan, toolResult, validation))
                .isTrue();
    }

    @Test
    void filterListTopicPresidentAnswer_passesMonotonicSafetyAndTerminates() {
        String query =
                "Dime qué actas mencionan el ascensor y fueron presididas por Juan Pérez Gutiérrez.";
        String answer =
                "Solo el acta del 24/02/2025 (ACTA 1.pdf) menciona el ascensor y fue presidida por Juan Pérez Gutiérrez.";
        QueryPlan plan = filterListPlan(query);
        DeterministicToolExecutionResult toolResult = toolResult(DeterministicToolKind.FILTER_AND_LIST_TOOL, answer);

        RouteCandidateValidationResult validation = safety.validateToolResult(plan, toolResult);

        assertThat(validation.safe()).isTrue();
        assertThat(DeterministicToolTerminalAnswerGuard.shouldFinishTerminal(plan, toolResult, validation))
                .isTrue();
    }

    @Test
    void filterListTopicPresidentQuery_extractsAscensorAndPresidedBySignals() {
        String query =
                "Dime qué actas mencionan el ascensor y fueron presididas por Juan Pérez Gutiérrez.";
        QueryPlan plan = filterListPlan(query);
        var signals = QueryConstraintSignals.fromPlan(plan);

        assertThat(signals.topicTokens()).contains("ascensor");
        assertThat(signals.presidedByConstraint()).isTrue();
        assertThat(signals.entityTokens()).contains("juan perez gutierrez");
        assertThat(signals.filterAndList()).isTrue();
    }

    @Test
    void countAndExplainTopicOccurrenceAnswer_passesMonotonicSafety() {
        String query = "Cuántas veces aparece la calefacción y en qué contexto fue tratada.";
        String answer =
                "La calefacción se trató una vez, en la reunión del 25/02/2026 (ACTA 5), "
                        + "para revisar el sistema y solicitar presupuestos de modernización.";
        QueryPlan plan = countAndExplainPlan(query);
        DeterministicToolExecutionResult toolResult = toolResult(DeterministicToolKind.COUNT_AND_EXPLAIN_TOOL, answer);

        RouteCandidateValidationResult validation = safety.validateToolResult(plan, toolResult);

        assertThat(validation.safe()).isTrue();
        assertThat(answer).contains("25/02/2026", "presupuesto", "una vez");
    }

    @Test
    void countAndExplainExactAttendeeCorpusNegative_passesMonotonicSafety() {
        String query =
                "¿En qué reuniones hubo exactamente 21 asistentes y qué se decidió en esa reunión?";
        String answer =
                StructuredMinuteMetadataSupport.formatExactAttendeeCountCorpusNegative(21, List.of());
        QueryPlan plan = countAndExplainPlan(query);
        DeterministicToolExecutionResult toolResult = toolResult(DeterministicToolKind.COUNT_AND_EXPLAIN_TOOL, answer);

        RouteCandidateValidationResult validation = safety.validateToolResult(plan, toolResult);

        assertThat(validation.safe()).isTrue();
        assertThat(answer).contains("21");
    }

    @Test
    void countAndExplainExact21AttendeeQuery_marksAbsenceLikely() {
        String query =
                "¿En qué reuniones hubo exactamente 21 asistentes y qué se decidió en esa reunión?";
        QueryConstraintSignals signals = QueryConstraintSignals.fromPlan(countAndExplainPlan(query));
        assertThat(signals.absenceLikely()).isTrue();
    }

    @Test
    void summarizeMeetingYear2030Negative_passesMonotonicSafety() {
        String query = "Resume el acta del año 2030.";
        String answer = StructuredMinuteMetadataSupport.formatYearOnlySummarizeAbsence("2030");
        QueryPlan plan = summarizePlan(query);
        DeterministicToolExecutionResult toolResult = toolResult(DeterministicToolKind.SUMMARIZE_MEETING_TOOL, answer);

        RouteCandidateValidationResult validation = safety.validateToolResult(plan, toolResult);

        assertThat(validation.safe()).isTrue();
        assertThat(answer).contains("2030");
        assertThat(answer.length())
                .isGreaterThanOrEqualTo(StructuredMinuteMetadataSupport.summarizeMeetingEvaluatorMinLength());
    }

    @Test
    void summarizeMeetingYear2030Query_marksAbsenceLikelyWithYear() {
        String query = "Resume el acta del año 2030.";
        QueryConstraintSignals signals = QueryConstraintSignals.fromPlan(summarizePlan(query));
        assertThat(signals.absenceLikely()).isTrue();
        assertThat(signals.years()).contains(2030);
    }

    private static DeterministicToolExecutionResult toolResult(DeterministicToolKind kind, String answer) {
        return new DeterministicToolExecutionResult(
                Optional.of(kind),
                DeterministicToolOutcome.EXECUTED_SUCCESS,
                true,
                answer,
                Map.of(),
                List.of());
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

    private static QueryPlan summarizePlan(String query) {
        return new QueryPlan(
                QueryPlan.VERSION_P6_QU_CORE_V1,
                query,
                query,
                query,
                query,
                QueryType.SUMMARIZE_MEETING.name(),
                Optional.of(QueryType.SUMMARIZE_MEETING),
                ClassifierStatus.OK,
                QueryIntent.SUMMARIZE,
                Map.of(),
                List.of(),
                List.of(),
                EntityExtractionResult.emptyWithNote(""),
                StructuredRewriteResult.identityFallback(query, null),
                ExpectedAnswerShape.SUMMARY,
                AmbiguityAssessment.sufficient(),
                "corr",
                "default",
                List.of());
    }
}
