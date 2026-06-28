package com.uniovi.rag.application.service.evaluation.metrics;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AnswerabilityNegativeSignalsTest {

    @Test
    void topicAnchoredNegative_matchesComentaronDetallesWithTopic() {
        assertThat(
                        AnswerabilityNegativeSignals.hasTopicAnchoredNegativeParaphrase(
                                "No se encuentra ninguna mención a una fuga de gas en las actas disponibles.",
                                "No se comentaron detalles sobre la fuga de gas."))
                .isTrue();
    }

    @Test
    void topicAnchoredNegative_rejectsWithoutTopic() {
        assertThat(
                        AnswerabilityNegativeSignals.hasTopicAnchoredNegativeParaphrase(
                                "No se encuentra ninguna mención a una fuga de gas en las actas disponibles.",
                                "No se comentaron detalles."))
                .isFalse();
    }

    @Test
    void topicAnchoredNegative_rejectsAffirmativeClaim() {
        assertThat(
                        AnswerabilityNegativeSignals.hasTopicAnchoredNegativeParaphrase(
                                "No se encuentra ninguna mención a una fuga de gas en las actas disponibles.",
                                "Sí, se comentó la fuga de gas en el acta correspondiente."))
                .isFalse();
    }
}
