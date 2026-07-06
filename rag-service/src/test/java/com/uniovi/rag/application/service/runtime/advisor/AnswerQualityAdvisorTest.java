package com.uniovi.rag.application.service.runtime.advisor;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniovi.rag.application.service.runtime.routing.safety.RouteCandidateConstraintValidator;
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
import com.uniovi.rag.domain.runtime.tool.DeterministicToolKind;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AnswerQualityAdvisorTest {

    private AnswerQualityAdvisor advisor;

    @BeforeEach
    void setUp() {
        advisor = new AnswerQualityAdvisor(new RouteCandidateConstraintValidator());
    }

    @Test
    void rejectsIncompleteParticipantList() {
        QueryPlan plan =
                plan(
                        "¿Quiénes asistieron al acta del 25/02/2026?",
                        QueryType.GET_FIELD,
                        List.of("2026-02-25"));
        String answer = "Participaron Ana Sánchez Herrera.";

        var assessment =
                advisor.assess(null, plan, answer, JudgeCandidateSource.WORKFLOW, Optional.empty());

        assertThat(assessment.acceptable()).isFalse();
        assertThat(assessment.incompleteParticipantList()).isTrue();
    }

    @Test
    void detectsFalseAbstentionOnFilterList() {
        QueryPlan plan =
                plan("¿Cuántas actas mencionan el ascensor?", QueryType.COUNT_DOCUMENTS, List.of());
        String answer = "No consta en las actas proporcionadas.";

        var assessment =
                advisor.assess(null, plan, answer, JudgeCandidateSource.WORKFLOW, Optional.empty());

        assertThat(assessment.acceptable()).isFalse();
        assertThat(assessment.falseAbstention()).isTrue();
    }

    @Test
    void rejectsUnsupportedPositiveAnswer() {
        QueryPlan plan =
                plan(
                        "¿Qué se comentó respecto a la radiación solar?",
                        QueryType.FIND_PARAGRAPH,
                        List.of());
        String context = "Acta del 24 de febrero de 2025. Se habló de videovigilancia.";
        String answer = "Se habló de la radiación solar en alguna reunión.";

        var assessment =
                advisor.assess(null, plan, answer, JudgeCandidateSource.WORKFLOW, Optional.empty());

        assertThat(assessment.acceptable()).isFalse();
        assertThat(assessment.unsupportedPositive()).isTrue();
    }

    @Test
    void rejectsWrongDateAnswer() {
        QueryPlan plan =
                plan(
                        "Resume brevemente el acta del 25/02/2026.",
                        QueryType.SUMMARIZE_MEETING,
                        List.of("2026-02-25"));
        String context = "Fecha: 25 de febrero de 2026. Presidente Jorge Moreno.";
        String answer = "La reunión del 25 de febrero de 2025 trató el presupuesto.";

        var assessment =
                advisor.assess(null, plan, answer, JudgeCandidateSource.WORKFLOW, Optional.empty());

        assertThat(assessment.acceptable()).isFalse();
        assertThat(assessment.wrongDate()).isTrue();
    }

    @Test
    void preservesCorrectToolAnswer() {
        QueryPlan plan = plan("¿Cuántas actas mencionan el ascensor?", QueryType.COUNT_DOCUMENTS, List.of());
        String answer = "Dos actas mencionan el ascensor. Estas son: ACTA 1.pdf y ACTA 6.pdf";

        var assessment =
                advisor.assess(
                        null,
                        plan,
                        answer,
                        JudgeCandidateSource.DETERMINISTIC_TOOL,
                        Optional.of(DeterministicToolKind.COUNT_DOCUMENTS_TOOL));

        assertThat(assessment.acceptable()).isTrue();
        assertThat(assessment.preserveWithoutLlmJudge()).isTrue();
    }

    @Test
    void preservesDeterministicParticipantCountWithoutFactualAbstention() {
        QueryPlan plan =
                plan(
                        "¿Cuántos participantes hubo en el acta del 24/02/2025?",
                        QueryType.GET_FIELD,
                        List.of("2025-02-24"));
        String answer = "En el acta del 24 de febrero de 2025 (ACTA 1.pdf) asistieron 20 participantes.";

        var assessment =
                advisor.assess(
                        null,
                        plan,
                        answer,
                        JudgeCandidateSource.DETERMINISTIC_TOOL,
                        Optional.of(DeterministicToolKind.GET_FIELD_TOOL));

        assertThat(assessment.acceptable()).isTrue();
        assertThat(assessment.falseAbstention()).isFalse();
        assertThat(assessment.preserveWithoutLlmJudge()).isTrue();
    }

    @Test
    void rejectsPrefixOnlyBasedAnswer() {
        QueryPlan plan = plan("en qué actas se habla sobre cámaras", QueryType.FILTER_AND_LIST, List.of());

        var assessment =
                advisor.assess(null, plan, "Based", JudgeCandidateSource.WORKFLOW, Optional.empty());

        assertThat(assessment.acceptable()).isFalse();
        assertThat(assessment.reasons()).contains("prefix_only_answer");
    }

    private static QueryPlan plan(String query, QueryType queryType, List<String> dates) {
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
                QueryIntent.UNKNOWN,
                Map.of(),
                List.of(),
                List.of(),
                entities,
                StructuredRewriteResult.identityDisabled(query, ""),
                ExpectedAnswerShape.UNKNOWN,
                new AmbiguityAssessment(AmbiguityStatus.SUFFICIENT, List.of(), List.of()),
                "corr",
                "",
                List.of());
    }
}
