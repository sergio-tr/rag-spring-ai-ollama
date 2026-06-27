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
    void acceptsFindParagraphGasLeakCorpusNegative() {
        QueryPlan plan =
                plan(
                        "¿Qué se comentó respecto a la fuga de gas?",
                        QueryType.FIND_PARAGRAPH);
        RouteCandidateValidationResult result =
                validator.validateToolOrFunctionAnswer(
                        plan,
                        "No se encuentra ninguna mención a una fuga de gas en las actas disponibles.",
                        Optional.of(DeterministicToolKind.FIND_PARAGRAPH_TOOL));

        assertThat(result.safe()).isTrue();
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
    void acceptsFilterListAugustSlashDateAnswer() {
        QueryPlan plan =
                plan(
                        "¿Qué reuniones celebradas en agosto hablaron sobre videovigilancia y tuvieron más de 18 asistentes?",
                        QueryType.FILTER_AND_LIST);
        String answer =
                "La reunión del 25/08/2026 (ACTA 6) trató videovigilancia y tuvo 19 asistentes.";

        RouteCandidateValidationResult result =
                validator.validateToolOrFunctionAnswer(
                        plan, answer, Optional.of(DeterministicToolKind.FILTER_AND_LIST_TOOL));

        assertThat(result.safe()).isTrue();
        assertThat(result.rejectionReasons()).isEmpty();
    }

    @Test
    void acceptsFilterListAugustIsoDateAnswer() {
        QueryPlan plan =
                plan(
                        "¿Qué reuniones celebradas en agosto hablaron sobre videovigilancia y tuvieron más de 18 asistentes?",
                        QueryType.FILTER_AND_LIST);
        String answer =
                "La reunión del 2026-08-25 (ACTA 6) trató videovigilancia y tuvo 19 asistentes.";

        RouteCandidateValidationResult result =
                validator.validateToolOrFunctionAnswer(
                        plan, answer, Optional.of(DeterministicToolKind.FILTER_AND_LIST_TOOL));

        assertThat(result.safe()).isTrue();
    }

    @Test
    void acceptsFilterListStartTimeAnswer() {
        QueryPlan plan =
                plan("¿Qué actas tienen hora de inicio a las 19:00?", QueryType.FILTER_AND_LIST);
        String answer =
                "Hay 3 actas con hora de inicio a las 19:00: ACTA 1 (24/02/2025), ACTA 2 (25/02/2025) y ACTA 5 (25/02/2026). Fuentes: ACTA 1.pdf, ACTA 2.pdf, ACTA 5.pdf.";

        RouteCandidateValidationResult result =
                validator.validateToolOrFunctionAnswer(
                        plan, answer, Optional.of(DeterministicToolKind.FILTER_AND_LIST_TOOL));

        assertThat(result.safe()).isTrue();
    }

    @Test
    void acceptsAscensorCountDocumentsAnswer() {
        QueryPlan plan = plan("¿Cuántas actas mencionan el ascensor?", QueryType.COUNT_DOCUMENTS);
        String answer =
                "El ascensor se menciona en dos actas: ACTA 1.pdf (24/02/2025) y ACTA 6.pdf (25/08/2026).";

        RouteCandidateValidationResult result =
                validator.validateToolOrFunctionAnswer(
                        plan, answer, Optional.of(DeterministicToolKind.COUNT_DOCUMENTS_TOOL));

        assertThat(result.safe()).isTrue();
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

    @Test
    void acceptsSummarizeMeetingAnswerWhenDateEntityUsesSlashFormatInQuery() {
        QueryPlan plan =
                new QueryPlan(
                        QueryPlan.VERSION_P6_QU_CORE_V1,
                        "Resume brevemente el acta del 25/02/2026.",
                        "Resume brevemente el acta del 25/02/2026.",
                        "Resume brevemente el acta del 25/02/2026.",
                        "Resume brevemente el acta del 25/02/2026.",
                        QueryType.SUMMARIZE_MEETING.name(),
                        Optional.of(QueryType.SUMMARIZE_MEETING),
                        ClassifierStatus.OK,
                        QueryIntent.SUMMARIZE,
                        Map.of(),
                        List.of("25/02/2026"),
                        List.of(),
                        EntityExtractionResult.emptyWithNote(""),
                        StructuredRewriteResult.identityDisabled(
                                "Resume brevemente el acta del 25/02/2026.", ""),
                        ExpectedAnswerShape.SUMMARY,
                        new AmbiguityAssessment(AmbiguityStatus.SUFFICIENT, List.of(), List.of()),
                        "corr",
                        "",
                        List.of());
        RouteCandidateValidationResult result =
                validator.validateToolOrFunctionAnswer(
                        plan,
                        "La reunión del 25 de febrero de 2026 tuvo lugar de 19:00 a 20:30, con la asistencia de 17 propietarios.",
                        Optional.of(DeterministicToolKind.SUMMARIZE_MEETING_TOOL));

        assertThat(result.safe()).isTrue();
    }

    @Test
    void acceptsGetDurationStructuredAnswerWithTimesAndMinutes() {
        QueryPlan plan =
                plan(
                        "Duración de la reunión del 25 de febrero de 2026.",
                        QueryType.GET_DURATION);
        String answer =
                "La reunión del 25 de febrero de 2026 comenzó a las 19:00 y terminó a las 20:30 (1 hora y 30 minutos / 90 minutos).";
        RouteCandidateValidationResult result =
                validator.validateToolOrFunctionAnswer(
                        plan, answer, Optional.of(DeterministicToolKind.GET_DURATION_TOOL));

        assertThat(result.safe()).isTrue();
    }

    @Test
    void rejectsGetDurationAnswerMissingDurationTokens() {
        QueryPlan plan =
                plan(
                        "Duración de la reunión del 25 de febrero de 2026.",
                        QueryType.GET_DURATION);
        RouteCandidateValidationResult result =
                validator.validateToolOrFunctionAnswer(
                        plan,
                        "La reunión duró de 19:00 a 20:30.",
                        Optional.of(DeterministicToolKind.GET_DURATION_TOOL));

        assertThat(result.safe()).isFalse();
        assertThat(result.rejectionReasons()).anyMatch(r -> r.contains("month_constraint_missing"));
    }

    @Test
    void rejectsFindParagraphUndatedMixedActaSummary() {
        QueryPlan plan =
                plan(
                        "¿Qué se dijo en relación a la limpieza de las zonas comunes?",
                        QueryType.FIND_PARAGRAPH);
        String mixed =
                "Se aprobó la contratación de un nuevo servicio de limpieza con mayor frecuencia y se acordó colocar señalización adicional y reforzar el control para solucionar los problemas de estacionamiento indebido en las zonas comunes.";
        RouteCandidateValidationResult result =
                validator.validateToolOrFunctionAnswer(
                        plan, mixed, Optional.of(DeterministicToolKind.FIND_PARAGRAPH_TOOL));

        assertThat(result.safe()).isFalse();
        assertThat(result.rejectionReasons()).anyMatch(r -> r.contains("find_paragraph_missing_acta_reference"));
    }

    @Test
    void rejectsFilterListAbstentionWithoutMandatoryFields() {
        QueryPlan plan =
                plan(
                        "Dime qué actas mencionan el ascensor y fueron presididas por Juan Pérez Gutiérrez.",
                        QueryType.FILTER_AND_LIST);
        String abstention =
                "No consta en las fuentes disponibles información suficiente para responder con seguridad.";
        RouteCandidateValidationResult result =
                validator.validateToolOrFunctionAnswer(
                        plan, abstention, Optional.of(DeterministicToolKind.FILTER_AND_LIST_TOOL));

        assertThat(result.safe()).isFalse();
        assertThat(result.rejectionReasons()).contains("filter_list_unsupported_abstention");
    }

    @Test
    void rejectsCountAndExplainLexicalWordCountAnswer() {
        QueryPlan plan =
                plan(
                        "Cuántas veces aparece la calefacción y en qué contexto fue tratada.",
                        QueryType.COUNT_AND_EXPLAIN);
        String lexicalCount =
                "La calefacción aparece 3 veces. (1) La primera mención se refiere a la evaluación del sistema.";
        RouteCandidateValidationResult result =
                validator.validateToolOrFunctionAnswer(
                        plan, lexicalCount, Optional.of(DeterministicToolKind.COUNT_AND_EXPLAIN_TOOL));

        assertThat(result.safe()).isFalse();
    }

    @Test
    void rejectsCountAndExplainClarificationQuestionAsAnswer() {
        QueryPlan plan =
                plan(
                        "¿En qué reuniones hubo exactamente 21 asistentes y qué se decidió en esa reunión?",
                        QueryType.COUNT_AND_EXPLAIN);
        String clarification = "¿A qué acta o reunión te refieres? Indica la fecha o el documento.";
        RouteCandidateValidationResult result =
                validator.validateToolOrFunctionAnswer(
                        plan, clarification, Optional.of(DeterministicToolKind.COUNT_AND_EXPLAIN_TOOL));

        assertThat(result.safe()).isFalse();
    }

    @Test
    void rejectsSummarizeMeetingWrongActaDumpForYearOnlyQuery() {
        QueryPlan plan = plan("Resume el acta del año 2030.", QueryType.SUMMARIZE_MEETING);
        String wrongActaDump =
                "Reunión del 25 de febrero de 2026 | ACTA DE LA REUNIÓN DE LA COMUNIDAD DE VECINOS Fecha: 25 de febrero de 2026";
        RouteCandidateValidationResult result =
                validator.validateToolOrFunctionAnswer(
                        plan, wrongActaDump, Optional.of(DeterministicToolKind.SUMMARIZE_MEETING_TOOL));

        assertThat(result.safe()).isFalse();
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
