package com.uniovi.rag.application.service.evaluation.metrics.matching;

import com.uniovi.rag.application.service.evaluation.metrics.Answerability;
import com.uniovi.rag.application.service.evaluation.metrics.BenchmarkMvpMetricsCalculator;
import com.uniovi.rag.application.service.evaluation.metrics.DatasetMetricContract;
import com.uniovi.rag.application.service.evaluation.metrics.RagPresetToolMetrics;
import com.uniovi.rag.domain.model.QueryType;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExpectedAnswerMatchCalibratorTest {

    @Test
    void rawContainment_preservedWhenAlreadyContained() {
        ExpectedAnswerMatchResult result =
                ExpectedAnswerMatchCalibrator.calibrate(
                        "Paris is the capital",
                        "The answer is Paris is the capital of France",
                        Answerability.ANSWERABLE,
                        QueryType.GET_FIELD,
                        Map.of());

        assertThat(result.matchedCalibrated()).isTrue();
        assertThat(result.matchType()).isEqualTo(ExpectedAnswerMatchType.RAW_CONTAINS);
        assertThat(BenchmarkMvpMetricsCalculator.containsExpectedAnswer(
                        "Paris is the capital", "The answer is Paris is the capital of France"))
                .isTrue();
    }

    @Test
    void spanishNegativeEquivalence_matchesParaphraseForUnanswerable() {
        ExpectedAnswerMatchResult result =
                ExpectedAnswerMatchCalibrator.calibrate(
                        "No hay mención de cambios de fecha en el acta.",
                        "No se comentó ningún cambio de fecha durante la reunión.",
                        Answerability.UNANSWERABLE,
                        QueryType.GET_FIELD,
                        Map.of(DatasetMetricContract.KEY_ANSWERABILITY, Answerability.UNANSWERABLE.name()));

        assertThat(result.matchedCalibrated()).isTrue();
        assertThat(result.matchType()).isEqualTo(ExpectedAnswerMatchType.NEGATIVE_EQUIVALENCE);
    }

    @Test
    void bareNo_isUnsafeNotHighMatch() {
        ExpectedAnswerMatchResult result =
                ExpectedAnswerMatchCalibrator.calibrate(
                        "No hay datos.",
                        "no",
                        Answerability.UNANSWERABLE,
                        QueryType.BOOLEAN_QUERY,
                        Map.of());

        assertThat(result.matchedCalibrated()).isFalse();
        assertThat(result.matchType()).isEqualTo(ExpectedAnswerMatchType.UNSAFE_TO_JUDGE);
    }

    @Test
    void forcedAbstention_correctOnlyForUnanswerable() {
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("abstentionTriggered", true);
        ctx.put("finalAnswerSource", "FORCED_ABSTENTION");

        ExpectedAnswerMatchResult unanswerable =
                ExpectedAnswerMatchCalibrator.calibrate(
                        "No hay registro de modificaciones contractuales en el acta.",
                        "Insufficient evidence in the available sources.",
                        Answerability.UNANSWERABLE,
                        QueryType.GET_FIELD,
                        ctx);
        assertThat(unanswerable.matchedCalibrated()).isTrue();
        assertThat(unanswerable.matchType()).isEqualTo(ExpectedAnswerMatchType.CORRECT_ABSTENTION);

        ExpectedAnswerMatchResult answerable =
                ExpectedAnswerMatchCalibrator.calibrate(
                        "Dos reuniones.",
                        "No consta en las fuentes disponibles.",
                        Answerability.ANSWERABLE,
                        QueryType.COUNT_DOCUMENTS,
                        ctx);
        assertThat(answerable.matchedCalibrated()).isFalse();
        assertThat(answerable.matchType()).isEqualTo(ExpectedAnswerMatchType.NO_MATCH);
        assertThat(answerable.reason()).isEqualTo("answerable_abstention");
    }

    @Test
    void hallucinatedPositive_notMatchedForUnanswerable() {
        ExpectedAnswerMatchResult result =
                ExpectedAnswerMatchCalibrator.calibrate(
                        "No hay registro de asistentes.",
                        "Sí, asistieron 12 personas al acta correspondiente.",
                        Answerability.UNANSWERABLE,
                        QueryType.COUNT_DOCUMENTS,
                        Map.of());

        assertThat(result.matchedCalibrated()).isFalse();
        assertThat(result.matchType()).isEqualTo(ExpectedAnswerMatchType.NO_MATCH);
    }

    @Test
    void numericValueMatch_matchesSpanishWordCount() {
        ExpectedAnswerMatchResult result =
                ExpectedAnswerMatchCalibrator.calibrate(
                        "tres ocasiones",
                        "Se registraron tres reuniones en el periodo.",
                        Answerability.ANSWERABLE,
                        QueryType.COUNT_DOCUMENTS,
                        Map.of());

        assertThat(result.matchedCalibrated()).isTrue();
        assertThat(result.matchType()).isEqualTo(ExpectedAnswerMatchType.NUMERIC_VALUE_MATCH);
    }

    @Test
    void wrongNumericValue_noMatch() {
        ExpectedAnswerMatchResult result =
                ExpectedAnswerMatchCalibrator.calibrate(
                        "dos ocasiones",
                        "Se registraron tres reuniones en el periodo.",
                        Answerability.ANSWERABLE,
                        QueryType.COUNT_DOCUMENTS,
                        Map.of());

        assertThat(result.matchedCalibrated()).isFalse();
        assertThat(result.matchType()).isEqualTo(ExpectedAnswerMatchType.NO_MATCH);
        assertThat(result.reason()).isEqualTo("numeric_value_mismatch");
    }

    @Test
    void dateValueMatch_matchesIsoOverlap() {
        ExpectedAnswerMatchResult result =
                ExpectedAnswerMatchCalibrator.calibrate(
                        "2024-03-15",
                        "La reunión fue el 15/03/2024.",
                        Answerability.ANSWERABLE,
                        QueryType.GET_FIELD,
                        Map.of());

        assertThat(result.matchedCalibrated()).isTrue();
        assertThat(result.matchType()).isEqualTo(ExpectedAnswerMatchType.DATE_VALUE_MATCH);
    }

    @Test
    void entityMismatch_noMatch() {
        ExpectedAnswerMatchResult result =
                ExpectedAnswerMatchCalibrator.calibrate(
                        "junta directiva; presupuesto anual",
                        "comité técnico; revisión trimestral",
                        Answerability.ANSWERABLE,
                        QueryType.FILTER_AND_LIST,
                        Map.of());

        assertThat(result.matchedCalibrated()).isFalse();
        assertThat(result.matchType()).isEqualTo(ExpectedAnswerMatchType.NO_MATCH);
    }

    @Test
    void structuredToolMatch_usesPersistedCountMatch() {
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put(RagPresetToolMetrics.KEY_TOOL_RESULT_USED_AS_FINAL, true);
        ctx.put("structuredScoreStatus", "COMPUTED");
        ctx.put("countMatch", true);

        ExpectedAnswerMatchResult result =
                ExpectedAnswerMatchCalibrator.calibrate(
                        "tres documentos registrados",
                        "Total: 3 documentos",
                        Answerability.ANSWERABLE,
                        QueryType.COUNT_DOCUMENTS,
                        ctx);

        assertThat(result.matchedCalibrated()).isTrue();
        assertThat(result.matchType()).isEqualTo(ExpectedAnswerMatchType.STRUCTURED_TOOL_MATCH);
    }

    @Test
    void rag001StyleCount_ignoresActaOrdinalsInActual() {
        ExpectedAnswerMatchResult result =
                ExpectedAnswerMatchCalibrator.calibrate(
                        "El tema del ascensor se menciona en dos actas diferentes: 24 de febrero de 2025 y 25 de agosto de 2026",
                        "Dos actas mencionan el ascensor: ACTA 1.pdf y ACTA 6.pdf.",
                        Answerability.ANSWERABLE,
                        QueryType.COUNT_DOCUMENTS,
                        Map.of());

        assertThat(result.matchedCalibrated()).isTrue();
        assertThat(result.matchType()).isEqualTo(ExpectedAnswerMatchType.NUMERIC_VALUE_MATCH);
    }

    @Test
    void rag029StyleDuration_timeRangeEquivalentToHourAndHalf() {
        ExpectedAnswerMatchResult result =
                ExpectedAnswerMatchCalibrator.calibrate(
                        "La reunión del 25 de febrero de 2026 comenzó a las 19:00 y terminó a las 20:30, por lo que duró una hora y media.",
                        "La reunión comenzó a las 19:00 a 20:30.",
                        Answerability.ANSWERABLE,
                        QueryType.GET_DURATION,
                        Map.of());

        assertThat(result.matchedCalibrated()).isTrue();
        assertThat(result.matchType()).isEqualTo(ExpectedAnswerMatchType.DATE_VALUE_MATCH);
        assertThat(result.reason()).isEqualTo("duration_value_equal");
    }

    @Test
    void safeBareNegative_booleanUnanswerableWithTopic() {
        ExpectedAnswerMatchResult result =
                ExpectedAnswerMatchCalibrator.calibrate(
                        "No, la limpieza no se menciona en ninguna de las actas del año 2026.",
                        "no",
                        Answerability.UNANSWERABLE,
                        QueryType.BOOLEAN_QUERY,
                        Map.of());

        assertThat(result.matchedCalibrated()).isTrue();
        assertThat(result.matchType()).isEqualTo(ExpectedAnswerMatchType.NEGATIVE_EQUIVALENCE);
        assertThat(result.reason()).isEqualTo("safe_bare_negative_boolean");
    }

    @Test
    void rag019StyleNegativeParaphrase_matchesComentaronDetalles() {
        ExpectedAnswerMatchResult result =
                ExpectedAnswerMatchCalibrator.calibrate(
                        "No se encuentra ninguna mención a una fuga de gas en las actas disponibles.",
                        "No se comentaron detalles sobre la fuga de gas.",
                        Answerability.UNANSWERABLE,
                        QueryType.FIND_PARAGRAPH,
                        Map.of(DatasetMetricContract.KEY_ANSWERABILITY, Answerability.UNANSWERABLE.name()));

        assertThat(result.matchedCalibrated()).isTrue();
        assertThat(result.matchType()).isEqualTo(ExpectedAnswerMatchType.NEGATIVE_EQUIVALENCE);
    }

    @Test
    void rag019StyleNegativeParaphrase_rejectsWithoutTopic() {
        ExpectedAnswerMatchResult result =
                ExpectedAnswerMatchCalibrator.calibrate(
                        "No se encuentra ninguna mención a una fuga de gas en las actas disponibles.",
                        "No se comentaron detalles.",
                        Answerability.UNANSWERABLE,
                        QueryType.FIND_PARAGRAPH,
                        Map.of(DatasetMetricContract.KEY_ANSWERABILITY, Answerability.UNANSWERABLE.name()));

        assertThat(result.matchedCalibrated()).isFalse();
        assertThat(result.matchType()).isEqualTo(ExpectedAnswerMatchType.NO_MATCH);
        assertThat(result.reason()).isEqualTo("actual_not_negative");
    }

    @Test
    void rag019StyleNegativeParaphrase_rejectsAffirmativeUnsupported() {
        ExpectedAnswerMatchResult result =
                ExpectedAnswerMatchCalibrator.calibrate(
                        "No se encuentra ninguna mención a una fuga de gas en las actas disponibles.",
                        "Sí, se comentó la fuga de gas en el acta correspondiente.",
                        Answerability.UNANSWERABLE,
                        QueryType.FIND_PARAGRAPH,
                        Map.of(DatasetMetricContract.KEY_ANSWERABILITY, Answerability.UNANSWERABLE.name()));

        assertThat(result.matchedCalibrated()).isFalse();
        assertThat(result.matchType()).isEqualTo(ExpectedAnswerMatchType.NO_MATCH);
    }

    @Test
    void rag019StyleNegativeParaphrase_matchesMencionaNadaRespecto() {
        ExpectedAnswerMatchResult result =
                ExpectedAnswerMatchCalibrator.calibrate(
                        "No se encuentra ninguna mención a una fuga de gas en las actas disponibles.",
                        "No se menciona nada respecto a la fuga de gas.",
                        Answerability.UNANSWERABLE,
                        QueryType.FIND_PARAGRAPH,
                        Map.of(DatasetMetricContract.KEY_ANSWERABILITY, Answerability.UNANSWERABLE.name()));

        assertThat(result.matchedCalibrated()).isTrue();
        assertThat(result.matchType()).isEqualTo(ExpectedAnswerMatchType.NEGATIVE_EQUIVALENCE);
    }

    @Test
    void mergeInto_populatesExportFields() {
        ExpectedAnswerMatchResult result =
                ExpectedAnswerMatchResult.match(
                        ExpectedAnswerMatchType.NORMALIZED_CONTAINS,
                        ExpectedAnswerMatchConfidence.HIGH,
                        "normalized_substring_contains");
        Map<String, Object> out = new LinkedHashMap<>();
        result.mergeInto(out, false);

        assertThat(out)
                .containsEntry(ExpectedAnswerMatchResult.KEY_CONTAINED_RAW, false)
                .containsEntry(ExpectedAnswerMatchResult.KEY_MATCHED, true)
                .containsEntry(ExpectedAnswerMatchResult.KEY_MATCH_TYPE, "NORMALIZED_CONTAINS")
                .containsEntry(ExpectedAnswerMatchResult.KEY_MATCH_CONFIDENCE, "HIGH")
                .containsEntry(ExpectedAnswerMatchResult.KEY_MATCH_REASON, "normalized_substring_contains")
                .containsEntry(ExpectedAnswerMatchResult.KEY_MATCH_VERSION, "1");
    }
}
