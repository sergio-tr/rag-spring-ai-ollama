package com.uniovi.rag.application.service.runtime.routing;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniovi.rag.domain.model.QueryType;
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
import org.junit.jupiter.api.Test;

class TerminalGetFieldRoutingSupportTest {

    @Test
    void terminalGetFieldWithDate_skipsWorkflowFallback() {
        QueryPlan plan =
                plan(
                        "¿Quiénes asistieron al acta del 25/02/2026?",
                        QueryType.GET_FIELD,
                        Map.of("field", "attendees"),
                        List.of("2026-02-25"));
        DeterministicToolExecutionResult toolResult =
                new DeterministicToolExecutionResult(
                        Optional.of(DeterministicToolKind.GET_FIELD_TOOL),
                        DeterministicToolOutcome.EXECUTED_SUCCESS,
                        true,
                        "En el acta del 25 de febrero de 2026, los participantes fueron: Ana (17 en total).",
                        Map.of(),
                        List.of());

        assertThat(TerminalGetFieldRoutingSupport.isTerminalMetadataGetField(plan)).isTrue();
        assertThat(TerminalGetFieldRoutingSupport.shouldTerminateWithoutWorkflowFallback(plan, toolResult))
                .isTrue();
    }

    @Test
    void getFieldWithoutDate_doesNotSkipWorkflowFallback() {
        QueryPlan plan =
                plan(
                        "¿Quiénes asistieron?",
                        QueryType.GET_FIELD,
                        Map.of("field", "attendees"),
                        List.of());
        DeterministicToolExecutionResult toolResult =
                new DeterministicToolExecutionResult(
                        Optional.of(DeterministicToolKind.GET_FIELD_TOOL),
                        DeterministicToolOutcome.EXECUTED_SUCCESS,
                        true,
                        "Participaron varias personas.",
                        Map.of(),
                        List.of());

        assertThat(TerminalGetFieldRoutingSupport.shouldTerminateWithoutWorkflowFallback(plan, toolResult))
                .isFalse();
    }

    private static QueryPlan plan(
            String query, QueryType queryType, Map<String, String> slots, List<String> dates) {
        EntityExtractionResult entities =
                new EntityExtractionResult(
                        List.of(),
                        dates,
                        List.of(),
                        List.of(),
                        List.of(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        List.of());
        return new QueryPlan(
                QueryPlan.VERSION_P6_QU_CORE_V1,
                query,
                query,
                query,
                query,
                queryType.name(),
                Optional.of(queryType),
                ClassifierStatus.OK,
                QueryIntent.EXTRACT_FIELD,
                slots,
                List.of(),
                List.of(),
                entities,
                StructuredRewriteResult.identityDisabled(query, ""),
                ExpectedAnswerShape.FIELD_VALUE,
                new AmbiguityAssessment(AmbiguityStatus.SUFFICIENT, List.of(), List.of()),
                "corr",
                "",
                List.of());
    }
}
