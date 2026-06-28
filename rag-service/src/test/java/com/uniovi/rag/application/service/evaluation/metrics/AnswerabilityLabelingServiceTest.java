package com.uniovi.rag.application.service.evaluation.metrics;

import com.uniovi.rag.application.evaluation.workbook.EvaluationReferenceBundleLoader;
import com.uniovi.rag.application.evaluation.workbook.EvaluationWorkbookParser;
import com.uniovi.rag.domain.evaluation.workbook.RagPresetQuestion;
import com.uniovi.rag.domain.model.QueryType;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AnswerabilityLabelingServiceTest {

    @Test
    void negativeSpanishPatterns_inferUnanswerable() {
        assertLabel("No hay actas sobre ese tema.", null, Answerability.UNANSWERABLE, AnswerabilityLabelRules.NEG_NO_HAY);
        assertLabel("No existen registros de esa reunión.", null, Answerability.UNANSWERABLE, AnswerabilityLabelRules.NEG_NO_EXISTE);
        assertLabel("Ninguna acta menciona ese punto.", null, Answerability.UNANSWERABLE, AnswerabilityLabelRules.NEG_NINGUNA);
        assertLabel("No se encuentra información al respecto.", null, Answerability.UNANSWERABLE, AnswerabilityLabelRules.NEG_NO_SE_ENCUENTRA);
        assertLabel("No, no consta en las actas.", null, Answerability.UNANSWERABLE, AnswerabilityLabelRules.NEG_EXPLICIT_NO);
    }

    @Test
    void positiveExpectedAnswers_inferAnswerable() {
        assertLabel("Asistieron 12 personas a la reunión.", QueryType.COUNT_DOCUMENTS, Answerability.ANSWERABLE, AnswerabilityLabelRules.POS_FACTUAL);
        assertLabel("La reunión se celebró el 12 de marzo de 2024.", QueryType.GET_FIELD, Answerability.ANSWERABLE, AnswerabilityLabelRules.POS_DATE);
        assertLabel(
                "Se acordó revisar el presupuesto en la próxima sesión plenaria del edificio.",
                QueryType.SUMMARIZE_TOPIC,
                Answerability.ANSWERABLE,
                AnswerabilityLabelRules.POS_DESCRIPTIVE);
    }

    @Test
    void mixedClauses_requireReview() {
        AnswerabilityLabelResult result =
                AnswerabilityLabelingService.label(
                        "No se menciona el tema; sí se decidió en el acta correspondiente.",
                        QueryType.BOOLEAN_QUERY,
                        false,
                        false,
                        false,
                        false);
        assertThat(result.label()).isEqualTo(Answerability.NEEDS_REVIEW);
        assertThat(result.source()).isEqualTo(AnswerabilitySource.REVIEW_REQUIRED);
        assertThat(result.ruleId()).isEqualTo(AnswerabilityLabelRules.REVIEW_MIXED_CLAUSE);
    }

    @Test
    void partialCompareAnswer_requiresReview() {
        AnswerabilityLabelResult result =
                AnswerabilityLabelingService.label(
                        "Marzo: 2 actas. No se encuentran actas de abril.",
                        QueryType.COMPARE,
                        false,
                        false,
                        false,
                        false);
        assertThat(result.label()).isEqualTo(Answerability.NEEDS_REVIEW);
        assertThat(result.ruleId()).isEqualTo(AnswerabilityLabelRules.REVIEW_PARTIAL_COMPARE);
    }

    @Test
    void datasetColumnOverridesInference() {
        AnswerabilityLabelResult result =
                AnswerabilityLabelingService.label(
                        "No hay actas.",
                        QueryType.BOOLEAN_QUERY,
                        false,
                        true,
                        false,
                        false);
        assertThat(result.label()).isEqualTo(Answerability.ANSWERABLE);
        assertThat(result.source()).isEqualTo(AnswerabilitySource.DATASET_COLUMN);
        assertThat(result.ruleId()).isEqualTo(AnswerabilityLabelRules.DATASET_ANSWERABLE);
    }

    @Test
    void emptyExpectedAnswer_staysUnknownWithoutFabrication() {
        AnswerabilityLabelResult result =
                AnswerabilityLabelingService.label("", QueryType.BOOLEAN_QUERY, false, false, false, false);
        assertThat(result.label()).isEqualTo(Answerability.NEEDS_REVIEW);
        assertThat(result.source()).isEqualTo(AnswerabilitySource.REVIEW_REQUIRED);
    }

    @Test
    void referenceWorkbook_labelDistribution() {
        EvaluationReferenceBundleLoader loader = new EvaluationReferenceBundleLoader(new EvaluationWorkbookParser());
        List<RagPresetQuestion> questions = loader.getSnapshot().workbook().ragPresetQuestionsEnriched();
        Map<Answerability, Long> counts = new EnumMap<>(Answerability.class);
        Map<AnswerabilitySource, Long> sources = new EnumMap<>(AnswerabilitySource.class);
        for (RagPresetQuestion question : questions) {
            AnswerabilityLabelResult label = AnswerabilityLabelingService.label(question);
            counts.merge(label.label(), 1L, Long::sum);
            sources.merge(label.source(), 1L, Long::sum);
            assertThat(label.source()).isNotNull();
            if (label.label() != Answerability.UNKNOWN && label.label() != Answerability.AMBIGUOUS) {
                assertThat(label.ruleId()).isNotBlank();
            }
        }
        assertThat(counts.getOrDefault(Answerability.ANSWERABLE, 0L)).isGreaterThan(20);
        assertThat(counts.getOrDefault(Answerability.UNANSWERABLE, 0L)).isGreaterThan(10);
        assertThat(sources.getOrDefault(AnswerabilitySource.DATASET_COLUMN, 0L)).isGreaterThan(50);
    }

    private static void assertLabel(
            String expectedAnswer, QueryType queryType, Answerability label, String ruleId) {
        AnswerabilityLabelResult result =
                AnswerabilityLabelingService.label(expectedAnswer, queryType, false, false, false, false);
        assertThat(result.label()).isEqualTo(label);
        assertThat(result.source()).isEqualTo(AnswerabilitySource.INFERRED_FROM_EXPECTED_ANSWER);
        assertThat(result.ruleId()).isEqualTo(ruleId);
    }
}
