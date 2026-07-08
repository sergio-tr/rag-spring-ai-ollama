package com.uniovi.rag.application.service.runtime.tool;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DeterministicToolNegativeAnswerDetectorTest {

    @Test
    void detectsFindParagraphNegativeAnswer() {
        assertThat(
                        DeterministicToolNegativeAnswerDetector.isNegativeOrNoData(
                                "No se encuentra ninguna mención a una fuga de gas en las actas disponibles."))
                .isTrue();
    }

    @Test
    void detectsSpanishNegativeAttendeeAnswer() {
        assertThat(
                        DeterministicToolNegativeAnswerDetector.isNegativeOrNoData(
                                "No se encontraron asistentes registrados para la reunión del 25 de febrero de 2026."))
                .isTrue();
    }

    @Test
    void detectsEnglishNoDocumentsAnswer() {
        assertThat(DeterministicToolNegativeAnswerDetector.isNegativeOrNoData("No matching documents were found."))
                .isTrue();
    }

    @Test
    void detectsShortNegativeAnswers() {
        assertThat(DeterministicToolNegativeAnswerDetector.isNegativeOrNoData("no")).isTrue();
        assertThat(DeterministicToolNegativeAnswerDetector.isNegativeOrNoData("n/a")).isTrue();
    }

    @Test
    void detectsSpanishFutureDateDenial() {
        assertThat(
                        DeterministicToolNegativeAnswerDetector.isNegativeOrNoData(
                                "La fecha indicada es futura y aún no ha ocurrido."))
                .isTrue();
    }

    @Test
    void detectsAffirmativeCountWithActa() {
        assertThat(DeterministicToolNegativeAnswerDetector.isAffirmativeCountOrList("Hay 3 actas del comité."))
                .isTrue();
    }

    @Test
    void doesNotFlagGroundedAffirmativeAnswer() {
        assertThat(
                        DeterministicToolNegativeAnswerDetector.isNegativeOrNoData(
                                "El ascensor se menciona en dos actas: ACTA 1.pdf y ACTA 6.pdf."))
                .isFalse();
    }

    @Test
    void detectsExplicitZeroCount() {
        assertThat(DeterministicToolNegativeAnswerDetector.isNegativeOrNoData("0 actas coinciden con el criterio."))
                .isTrue();
    }
}
