package com.uniovi.rag.application.service.runtime.routing.safety;

import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.domain.runtime.query.AmbiguityAssessment;
import com.uniovi.rag.domain.runtime.query.AmbiguityStatus;
import com.uniovi.rag.domain.runtime.query.ClassifierStatus;
import com.uniovi.rag.domain.runtime.query.EntityExtractionResult;
import com.uniovi.rag.domain.runtime.query.ExpectedAnswerShape;
import com.uniovi.rag.domain.runtime.query.QueryIntent;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.query.StructuredRewriteResult;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolKind;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RouteCandidateConstraintValidatorTest {

    private final RouteCandidateConstraintValidator validator = new RouteCandidateConstraintValidator();

    @Test
    void rejectsBooleanAffirmationWithoutTopicInYearScopedQuestion() {
        QueryPlan plan =
                plan(
                        "Verifica si se mencionó la limpieza en alguna reunión celebrada en 2026.",
                        QueryType.BOOLEAN_QUERY);
        RouteCandidateValidationResult result =
                validator.validateToolOrFunctionAnswer(
                        plan,
                        "Sí. Se acuerda contratar a un técnico especializado para regular el uso de las terrazas.",
                        Optional.of(DeterministicToolKind.BOOLEAN_QUERY_TOOL));

        assertThat(result.safe()).isFalse();
        assertThat(result.rejectionReasons()).isNotEmpty();
    }

    @Test
    void rejectsAbsenceQueryWithConcreteCount() {
        QueryPlan plan =
                plan("En cuántas actas participaron menos de diez personas.", QueryType.COUNT_DOCUMENTS);
        RouteCandidateValidationResult result =
                validator.validateToolOrFunctionAnswer(plan, "Una acta participante.", Optional.empty());

        assertThat(result.safe()).isFalse();
    }

    @Test
    void acceptsCountAnswerWithActaReferencesWhenCountPresent() {
        QueryPlan plan = plan("¿Cuántas actas mencionan el ascensor?", QueryType.COUNT_DOCUMENTS);
        RouteCandidateValidationResult result =
                validator.validateToolOrFunctionAnswer(
                        plan,
                        "Dos actas mencionan el ascensor. Estas son: ACTA 1.pdf y ACTA 6.pdf",
                        Optional.of(DeterministicToolKind.COUNT_DOCUMENTS_TOOL));

        assertThat(result.safe()).isTrue();
    }

    @Test
    void rejectsAugustVideovigilanceQueryWhenAnswerUsesFebruary() {
        QueryPlan plan =
                plan(
                        "¿Qué reuniones celebradas en agosto hablaron sobre videovigilancia y tuvieron más de 18 asistentes?",
                        QueryType.FILTER_AND_LIST);
        RouteCandidateValidationResult result =
                validator.validateToolOrFunctionAnswer(
                        plan,
                        "La reunión del 24 de febrero de 2025, con 20 asistentes, discutió videovigilancia.",
                        Optional.of(DeterministicToolKind.FILTER_AND_LIST_TOOL));

        assertThat(result.safe()).isFalse();
    }

    @Test
    void rejectsPhaseCGasLeakHedgedFindParagraphToolAnswer() {
        QueryPlan plan =
                plan(
                        "¿Qué se comentó respecto a la fuga de gas?",
                        QueryType.FIND_PARAGRAPH);
        String answer =
                "Se menciona la posibilidad de instalar cámaras de seguridad en las entradas del edificio para atender"
                        + " inquietudes de los vecinos relacionadas con la fuga de gas. No se proporciona información"
                        + " adicional sobre el comentario específico sobre la fuga de gas en los minutos. Se queda"
                        + " pendiente de estudio la instalación de las cámaras.";
        RouteCandidateValidationResult result =
                validator.validateToolOrFunctionAnswer(
                        plan, answer, Optional.of(DeterministicToolKind.FIND_PARAGRAPH_TOOL));

        assertThat(result.safe()).isFalse();
        assertThat(result.rejectionReasons()).anyMatch(r -> r.contains("find_paragraph_hedged"));
    }

    @Test
    void rejectsPhaseCGasLeakHedgedFindParagraphWithOfrecenComentarios() {
        QueryPlan plan =
                plan(
                        "¿Qué se comentó respecto a la fuga de gas?",
                        QueryType.FIND_PARAGRAPH);
        String answer =
                "Se menciona la posibilidad de instalar cámaras de seguridad en las entradas del edificio como una"
                        + " solución para inquietudes de los vecinos relacionadas con la fuga de gas. No se ofrecen"
                        + " comentarios específicos sobre la fuga de gas en sí, solo se plantea una medida para"
                        + " abordar las preocupaciones expresadas.";
        RouteCandidateValidationResult result =
                validator.validateToolOrFunctionAnswer(
                        plan, answer, Optional.of(DeterministicToolKind.FIND_PARAGRAPH_TOOL));

        assertThat(result.safe()).isFalse();
        assertThat(result.rejectionReasons()).anyMatch(r -> r.contains("find_paragraph_hedged"));
    }

    @Test
    void rejectsPhaseCElevatorFilterListWhenTopicIsOnlyDisclaimed() {
        QueryPlan plan =
                plan(
                        "Dime qué actas mencionan el ascensor y fueron presididas por Juan Pérez Gutiérrez.",
                        QueryType.FILTER_AND_LIST);
        String answer =
                "La acta del \"Regulación del uso de zonas comunes en verano\" fue presidida por Juan Pérez Gutiérrez,"
                        + " aunque no se detalla ninguna otra decisión relacionada con ascensores.";
        RouteCandidateValidationResult result =
                validator.validateToolOrFunctionAnswer(
                        plan, answer, Optional.of(DeterministicToolKind.FILTER_AND_LIST_TOOL));

        assertThat(result.safe()).isFalse();
        assertThat(result.rejectionReasons()).anyMatch(r -> r.contains("filter_list_topic_disclaimed"));
    }

    @Test
    void rejectsPhaseCAugustVideovigilanceFalseAbstentionFromTool() {
        QueryPlan plan =
                plan(
                        "¿Qué reuniones celebradas en agosto hablaron sobre videovigilancia y tuvieron más de 18 asistentes?",
                        QueryType.FILTER_AND_LIST);
        RouteCandidateValidationResult result =
                validator.validateToolOrFunctionAnswer(
                        plan,
                        "No hay reuniones en agosto que hablen sobre videovigilancia y tengan más de 18 asistentes.",
                        Optional.of(DeterministicToolKind.FILTER_AND_LIST_TOOL));

        assertThat(result.safe()).isFalse();
        assertThat(result.rejectionReasons()).contains("filter_list_unsupported_abstention");
    }

    @Test
    void acceptsCleanNegativeFindParagraphAnswer() {
        QueryPlan plan =
                plan(
                        "¿Qué se comentó respecto a la fuga de gas?",
                        QueryType.FIND_PARAGRAPH);
        RouteCandidateValidationResult result =
                validator.validateToolOrFunctionAnswer(
                        plan,
                        "No se comentó respecto a la fuga de gas.",
                        Optional.of(DeterministicToolKind.FIND_PARAGRAPH_TOOL));

        assertThat(result.safe()).isTrue();
    }

    @Test
    void allowsAbsenceLikelyFilterListNegativeAnswer() {
        QueryPlan plan =
                plan(
                        "¿Qué actas registradas en el año 2028 mencionan radiación solar?",
                        QueryType.FILTER_AND_LIST);
        RouteCandidateValidationResult result =
                validator.validateToolOrFunctionAnswer(
                        plan,
                        "No hay actas del año 2028 que mencionen radiación solar.",
                        Optional.of(DeterministicToolKind.FILTER_AND_LIST_TOOL));

        assertThat(result.safe()).isTrue();
    }

    @Test
    void acceptsValidBooleanToolAnswer() {
        QueryPlan plan =
                plan(
                        "Confirma si Jorge Moreno Navarro aparece en el acta del 25 de agosto de 2026.",
                        QueryType.BOOLEAN_QUERY);
        RouteCandidateValidationResult result =
                validator.validateToolOrFunctionAnswer(
                        plan,
                        "Sí, Jorge Moreno Navarro figura como asistente en el acta del 25 de agosto de 2026.",
                        Optional.of(DeterministicToolKind.BOOLEAN_QUERY_TOOL));

        assertThat(result.safe()).isTrue();
    }

    @Test
    void acceptsValidFilterAndListToolAnswer() {
        QueryPlan plan =
                plan(
                        "Dime qué actas mencionan el ascensor y fueron presididas por Juan Pérez Gutiérrez.",
                        QueryType.FILTER_AND_LIST);
        RouteCandidateValidationResult result =
                validator.validateToolOrFunctionAnswer(
                        plan,
                        "El acta del 24 de febrero de 2025 menciona el ascensor y fue presidida por Juan Pérez Gutiérrez.",
                        Optional.of(DeterministicToolKind.FILTER_AND_LIST_TOOL));

        assertThat(result.safe()).isTrue();
    }

    @Test
    void rejectsPhaseCElevatorFilterListWithoutConcreteActaReference() {
        QueryPlan plan =
                plan(
                        "Dime qué actas mencionan el ascensor y fueron presididas por Juan Pérez Gutiérrez.",
                        QueryType.FILTER_AND_LIST);
        String answer =
                "Las actas mencionadas que incluyen el ascensor fueron las propuestas para la renovación del"
                        + " portal del edificio. Estas actas fueron presididas por Juan Pérez Gutiérrez.";
        RouteCandidateValidationResult result =
                validator.validateToolOrFunctionAnswer(
                        plan, answer, Optional.of(DeterministicToolKind.FILTER_AND_LIST_TOOL));

        assertThat(result.safe()).isFalse();
        assertThat(result.rejectionReasons()).contains("filter_list_missing_concrete_acta_reference");
        assertThat(result.rejectionReasons()).contains("filter_list_topic_entity_not_co_bound");
        assertThat(result.rejectionReasons()).contains("filter_list_topic_descriptor_mismatch");
        assertThat(result.rejectionReasons()).contains("function_filter_list_incomplete");
    }

    @Test
    void acceptsTopicOnlyFilterListWhenConcreteActaPresent() {
        QueryPlan plan = plan("¿Qué actas mencionan el ascensor?", QueryType.FILTER_AND_LIST);
        RouteCandidateValidationResult result =
                validator.validateToolOrFunctionAnswer(
                        plan,
                        "El acta del 24 de febrero de 2025 menciona el ascensor.",
                        Optional.of(DeterministicToolKind.FILTER_AND_LIST_TOOL));

        assertThat(result.safe()).isTrue();
    }

    @Test
    void acceptsEntityOnlyFilterListWhenQueryHasNoTopicConstraint() {
        QueryPlan plan =
                plan(
                        "Dime qué actas fueron presididas por Juan Pérez Gutiérrez.",
                        QueryType.FILTER_AND_LIST);
        RouteCandidateValidationResult result =
                validator.validateToolOrFunctionAnswer(
                        plan,
                        "El acta del 24 de febrero de 2025 fue presidida por Juan Pérez Gutiérrez.",
                        Optional.of(DeterministicToolKind.FILTER_AND_LIST_TOOL));

        assertThat(result.safe()).isTrue();
    }

    @Test
    void rejectsFunctionSentinelForFilterListQuery() {
        QueryPlan plan =
                plan(
                        "Dime qué actas mencionan el ascensor y fueron presididas por Juan Pérez Gutiérrez.",
                        QueryType.FILTER_AND_LIST);
        RouteCandidateValidationResult result =
                validator.validateToolOrFunctionAnswer(
                        plan, "TOPIC_NOT_IN_CONTEXT", Optional.of(DeterministicToolKind.FILTER_AND_LIST_TOOL));

        assertThat(result.safe()).isFalse();
        assertThat(result.rejectionReasons())
                .contains("filter_list_unsupported_abstention", "function_sentinel_abstention", "function_filter_list_incomplete");
    }

    @Test
    void rejectsVagueAbsenceForAnswerableFilterListWithoutToolKind() {
        QueryPlan plan =
                plan(
                        "¿Qué reuniones celebradas en agosto hablaron sobre videovigilancia y tuvieron más de 18 asistentes?",
                        QueryType.FILTER_AND_LIST);
        RouteCandidateValidationResult result =
                validator.validateToolOrFunctionAnswer(
                        plan,
                        "No hay información suficiente en las fuentes.",
                        Optional.empty());

        assertThat(result.safe()).isFalse();
        assertThat(result.rejectionReasons())
                .contains("filter_list_unsupported_abstention", "filter_list_vague_absence", "function_filter_list_incomplete");
    }

    @Test
    void rejectsAbstainedRetrievalSentinelForAnswerableFilterList() {
        QueryPlan plan =
                plan(
                        "Dime qué actas mencionan el ascensor y fueron presididas por Juan Pérez Gutiérrez.",
                        QueryType.FILTER_AND_LIST);
        RouteCandidateValidationResult result =
                validator.validateRetrievalAnswer(plan, "TOPIC_NOT_IN_CONTEXT", true);

        assertThat(result.safe()).isFalse();
        assertThat(result.rejectionReasons()).contains("function_sentinel_abstention");
    }

    @Test
    void rejectsAugustFilterListWhenAnswerCitesDifferentMonth() {
        QueryPlan plan =
                plan(
                        "¿Qué reuniones celebradas en agosto hablaron sobre videovigilancia y tuvieron más de 18 asistentes?",
                        QueryType.FILTER_AND_LIST);
        RouteCandidateValidationResult result =
                validator.validateToolOrFunctionAnswer(
                        plan,
                        "La reunión del 24 de febrero de 2025, con 20 asistentes, discutió videovigilancia.",
                        Optional.of(DeterministicToolKind.FILTER_AND_LIST_TOOL));

        assertThat(result.safe()).isFalse();
        assertThat(result.rejectionReasons())
                .anyMatch(r -> r.contains("month_constraint"));
    }

    @Test
    void acceptsGetFieldAgendaAnswerWithoutRepeatingQueryYear() {
        QueryPlan plan =
                plan(
                        "¿Cuáles fueron los puntos del orden del día del 24 de febrero de 2025?",
                        QueryType.GET_FIELD);
        RouteCandidateValidationResult result =
                validator.validateToolOrFunctionAnswer(
                        plan,
                        "- Lectura y aprobación del acta anterior\n- Estado de cuentas y presupuesto anual\n- Reparaciones y mantenimiento",
                        Optional.of(DeterministicToolKind.GET_FIELD_TOOL));

        assertThat(result.safe()).isTrue();
    }

    private static QueryPlan plan(String query, QueryType queryType) {
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
}
