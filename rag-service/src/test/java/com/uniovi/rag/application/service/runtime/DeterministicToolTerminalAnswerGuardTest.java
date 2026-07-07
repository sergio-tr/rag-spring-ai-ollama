package com.uniovi.rag.application.service.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniovi.rag.application.service.runtime.routing.safety.RouteCandidateValidationResult;
import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.domain.runtime.judge.JudgeCandidateSource;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class DeterministicToolTerminalAnswerGuardTest {

    @AfterEach
    void resetGuardOverride() {
        DeterministicToolTerminalAnswerGuard.setAcceptanceGuardTestOverride(null);
    }

    @Test
    void guardDisabled_onlyGetFieldWithDateTerminates() {
        DeterministicToolTerminalAnswerGuard.setAcceptanceGuardTestOverride(false);
        QueryPlan getField =
                plan("¿Cuántos asistieron el 25/02/2026?", QueryType.GET_FIELD, Map.of("field", "attendeesCount"));
        DeterministicToolExecutionResult tool =
                successTool(
                        DeterministicToolKind.GET_FIELD_TOOL,
                        "En el acta del 25/02/2026 asistieron 17 propietarios.");

        assertThat(
                        DeterministicToolTerminalAnswerGuard.shouldFinishTerminal(
                                getField, tool, safeValidation()))
                .isTrue();
        assertThat(
                        DeterministicToolTerminalAnswerGuard.shouldFinishTerminal(
                                plan("¿Cuántas actas?", QueryType.COUNT_DOCUMENTS),
                                successTool(
                                        DeterministicToolKind.COUNT_DOCUMENTS_TOOL, "2 actas."),
                                safeValidation()))
                .isFalse();
    }

    @Test
    void guardEnabled_tierANegativeToolDefersToRetrievalFallback() {
        DeterministicToolTerminalAnswerGuard.setAcceptanceGuardTestOverride(true);
        QueryPlan countPlan = plan("Número de actas en 2028.", QueryType.COUNT_DOCUMENTS);
        DeterministicToolExecutionResult tool =
                successTool(
                        DeterministicToolKind.COUNT_DOCUMENTS_TOOL,
                        "No existen actas correspondientes al año 2028 en el corpus.");

        assertThat(
                        DeterministicToolTerminalAnswerGuard.shouldFinishTerminal(
                                countPlan, tool, safeValidation()))
                .isFalse();
        assertThat(
                        DeterministicToolTerminalAnswerGuard.shouldMarkDeterministicToolFinal(
                                countPlan, safeValidation()))
                .isTrue();
    }

    @Test
    void guardEnabled_getDurationIsTierAEligible() {
        DeterministicToolTerminalAnswerGuard.setAcceptanceGuardTestOverride(true);
        QueryPlan durationPlan =
                plan("Duración de la reunión del 25 de febrero de 2026.", QueryType.GET_DURATION);
        DeterministicToolExecutionResult tool =
                successTool(
                        DeterministicToolKind.GET_DURATION_TOOL,
                        "La reunión del 25 de febrero de 2026 comenzó a las 19:00 y terminó a las 20:30 (1 hora y 30 minutos / 90 minutos).");

        assertThat(
                        DeterministicToolTerminalAnswerGuard.shouldFinishTerminal(
                                durationPlan, tool, safeValidation()))
                .isTrue();
        assertThat(
                        DeterministicToolTerminalAnswerGuard.shouldMarkDeterministicToolFinal(
                                durationPlan, safeValidation()))
                .isTrue();
    }

    @Test
    void guardEnabled_findParagraphNegativeDefersToRetrievalFallback() {
        DeterministicToolTerminalAnswerGuard.setAcceptanceGuardTestOverride(true);
        QueryPlan fp02 =
                plan(
                        "¿Qué se comentó respecto a la fuga de gas?",
                        QueryType.FIND_PARAGRAPH,
                        Map.of(),
                        QueryIntent.FIND,
                        ExpectedAnswerShape.PARAGRAPH);
        DeterministicToolExecutionResult tool =
                successTool(
                        DeterministicToolKind.FIND_PARAGRAPH_TOOL,
                        "No se encuentra ninguna mención a una fuga de gas en las actas disponibles.");

        assertThat(
                        DeterministicToolTerminalAnswerGuard.shouldFinishTerminal(
                                fp02, tool, safeValidation()))
                .isFalse();
        assertThat(
                        DeterministicToolTerminalAnswerGuard.shouldMarkDeterministicToolFinal(
                                fp02, safeValidation()))
                .isTrue();
    }

    @Test
    void guardEnabled_unsafeValidationDoesNotTerminate() {
        DeterministicToolTerminalAnswerGuard.setAcceptanceGuardTestOverride(true);
        QueryPlan countPlan = plan("¿Cuántas actas?", QueryType.COUNT_DOCUMENTS);
        DeterministicToolExecutionResult tool =
                successTool(DeterministicToolKind.COUNT_DOCUMENTS_TOOL, "2");

        assertThat(
                        DeterministicToolTerminalAnswerGuard.shouldFinishTerminal(
                                countPlan, tool, unsafeValidation()))
                .isFalse();
    }

    @Test
    void judgePreserveWhenGuardEnabledForDeterministicTool() {
        DeterministicToolTerminalAnswerGuard.setAcceptanceGuardTestOverride(true);
        QueryPlan plan = plan("¿Cuántas actas?", QueryType.COUNT_DOCUMENTS);

        assertThat(
                        DeterministicToolTerminalAnswerGuard.shouldPreserveDeterministicToolAnswer(
                                plan,
                                JudgeCandidateSource.DETERMINISTIC_TOOL,
                                "No existen actas correspondientes al año 2028 en el corpus."))
                .isTrue();
    }

    private static RouteCandidateValidationResult safeValidation() {
        return RouteCandidateValidationResult.accepted(1.0, "COMPLETE");
    }

    private static RouteCandidateValidationResult unsafeValidation() {
        return RouteCandidateValidationResult.rejected("unsafe");
    }

    private static DeterministicToolExecutionResult successTool(DeterministicToolKind kind, String answer) {
        return new DeterministicToolExecutionResult(
                Optional.of(kind),
                DeterministicToolOutcome.EXECUTED_SUCCESS,
                true,
                answer,
                Map.of(),
                List.of());
    }

    private static QueryPlan plan(String query, QueryType queryType) {
        return plan(query, queryType, Map.of(), QueryIntent.COUNT, ExpectedAnswerShape.SCALAR_COUNT);
    }

    private static QueryPlan plan(
            String query,
            QueryType queryType,
            Map<String, String> slots,
            QueryIntent intent,
            ExpectedAnswerShape shape) {
        return new QueryPlan(
                QueryPlan.VERSION_P6_QU_CORE_V1,
                query,
                query,
                query,
                query,
                queryType.name(),
                Optional.of(queryType),
                ClassifierStatus.OK,
                intent,
                slots,
                List.of(),
                List.of(),
                EntityExtractionResult.emptyWithNote(""),
                StructuredRewriteResult.identityDisabled(query, ""),
                shape,
                new AmbiguityAssessment(AmbiguityStatus.SUFFICIENT, List.of(), List.of()),
                "corr",
                "",
                List.of());
    }

    private static QueryPlan plan(String query, QueryType queryType, Map<String, String> slots) {
        return plan(query, queryType, slots, QueryIntent.COUNT, ExpectedAnswerShape.SCALAR_COUNT);
    }
}
