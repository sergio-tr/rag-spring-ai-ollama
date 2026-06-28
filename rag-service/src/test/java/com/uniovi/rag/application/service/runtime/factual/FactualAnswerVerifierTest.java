package com.uniovi.rag.application.service.runtime.factual;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniovi.rag.domain.runtime.factual.FactualConstraintType;
import com.uniovi.rag.domain.runtime.policy.AnswerGroundingPolicy;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FactualAnswerVerifierTest {

    @Test
    void passes_whenNegativeAnswerMatchesAbsenceQuestion() {
        FactualQuestionConstraints constraints =
                new FactualQuestionConstraints(
                        AnswerGroundingPolicy.NEGATIVE_EVIDENCE,
                        FactualConstraintType.TOPIC,
                        Optional.empty(),
                        List.of("radiación solar"),
                        Optional.empty(),
                        Optional.empty(),
                        true,
                        true);
        String context = "Acta del 24 de febrero de 2025. Se habló de videovigilancia.";
        String answer = "No hay ninguna acta que mencione la radiación solar.";
        assertThat(FactualAnswerVerifier.verify(constraints, context, answer).passed()).isTrue();
    }

    @Test
    void fails_whenAffirmativeAnswerWithoutTopicInContext() {
        FactualQuestionConstraints constraints =
                new FactualQuestionConstraints(
                        AnswerGroundingPolicy.NEGATIVE_EVIDENCE,
                        FactualConstraintType.TOPIC,
                        Optional.empty(),
                        List.of("radiación solar"),
                        Optional.empty(),
                        Optional.empty(),
                        true,
                        true);
        String context = "Se acordó instalar cámaras de videovigilancia.";
        String answer = "Sí, se habló de videovigilancia en la reunión.";
        FactualVerifierResult result = FactualAnswerVerifier.verify(constraints, context, answer);
        assertThat(result.passed()).isFalse();
        assertThat(result.failures()).isNotEmpty();
    }

    @Test
    void fails_whenAccentNormalizedAffirmativeClaimsAbsentTopic() {
        FactualQuestionConstraints constraints =
                new FactualQuestionConstraints(
                        AnswerGroundingPolicy.NEGATIVE_EVIDENCE,
                        FactualConstraintType.TOPIC,
                        Optional.empty(),
                        List.of("radiación solar"),
                        Optional.empty(),
                        Optional.empty(),
                        true,
                        true);
        String context = "Se acordó instalar cámaras de videovigilancia.";
        String answer = "Se habló de la radiación solar en alguna reunión.";
        FactualVerifierResult result = FactualAnswerVerifier.verify(constraints, context, answer);
        assertThat(result.passed()).isFalse();
        assertThat(result.failures()).contains(FactualVerifierFailureReason.NEGATIVE_FALSE_POSITIVE);
    }

    @Test
    void fails_whenAbsenceQuestionGetsSubstantiveCountAnswer() {
        FactualQuestionConstraints constraints =
                new FactualQuestionConstraints(
                        AnswerGroundingPolicy.NEGATIVE_EVIDENCE,
                        FactualConstraintType.NUMERIC,
                        Optional.empty(),
                        List.of(),
                        Optional.empty(),
                        Optional.of(new FactualQuestionConstraints.NumericConstraint(
                                FactualQuestionConstraints.ComparatorKind.AT_MOST, 10)),
                        true,
                        true);
        String context = "Acta del 25 de agosto de 2026 con 20 asistentes.";
        String answer = "Según las actas proporcionadas, las actas con menos de diez personas son las del 25 de agosto.";
        FactualVerifierResult result = FactualAnswerVerifier.verify(constraints, context, answer);
        assertThat(result.passed()).isFalse();
        assertThat(result.failures()).contains(FactualVerifierFailureReason.NEGATIVE_FALSE_POSITIVE);
    }

    @Test
    void fails_whenAbsenceNumericAnswerAddsDistractorMeeting() {
        FactualQuestionConstraints constraints =
                new FactualQuestionConstraints(
                        AnswerGroundingPolicy.NEGATIVE_EVIDENCE,
                        FactualConstraintType.NUMERIC,
                        Optional.empty(),
                        List.of(),
                        Optional.empty(),
                        Optional.of(new FactualQuestionConstraints.NumericConstraint(
                                FactualQuestionConstraints.ComparatorKind.EXACTLY, 21)),
                        true,
                        true);
        String context = "Reunión del 25 de agosto de 2026 con 20 asistentes.";
        String answer =
                "No se menciona ninguna reunión con exactamente 21 asistentes. La reunión del 25 de agosto de 2025 tuvo 20.";
        FactualVerifierResult result = FactualAnswerVerifier.verify(constraints, context, answer);
        assertThat(result.passed()).isFalse();
        assertThat(result.failures()).contains(FactualVerifierFailureReason.UNRELATED_TOPIC);
    }

    @Test
    void failsNumericMismatch_whenAnswerInventsCount() {
        FactualQuestionConstraints constraints =
                new FactualQuestionConstraints(
                        AnswerGroundingPolicy.NUMERIC_OR_DATE,
                        FactualConstraintType.NUMERIC,
                        Optional.empty(),
                        List.of(),
                        Optional.empty(),
                        Optional.of(new FactualQuestionConstraints.NumericConstraint(
                                FactualQuestionConstraints.ComparatorKind.EXACTLY, 21)),
                        false,
                        true);
        String context = "La reunión tuvo 20 asistentes.";
        String answer = "Hubo exactamente 21 asistentes.";
        assertThat(FactualAnswerVerifier.verify(constraints, context, answer).passed()).isFalse();
    }

    @Test
    void fails_whenEntityNotInContext() {
        FactualQuestionConstraints constraints =
                new FactualQuestionConstraints(
                        AnswerGroundingPolicy.ENTITY_OR_TOPIC,
                        FactualConstraintType.ENTITY,
                        Optional.empty(),
                        List.of(),
                        Optional.of("Jorge Moreno Navarro"),
                        Optional.empty(),
                        false,
                        true);
        String context = "Asistentes: María López y Juan Pérez.";
        String answer = "Sí, Jorge Moreno Navarro figura como asistente.";
        FactualVerifierResult result = FactualAnswerVerifier.verify(constraints, context, answer);
        assertThat(result.passed()).isFalse();
        assertThat(result.failures()).contains(FactualVerifierFailureReason.ENTITY_NOT_IN_CONTEXT);
    }

    @Test
    void fails_whenAnswerUsesUnrelatedTopicNotInContext() {
        FactualQuestionConstraints constraints =
                new FactualQuestionConstraints(
                        AnswerGroundingPolicy.NEGATIVE_EVIDENCE,
                        FactualConstraintType.TOPIC,
                        Optional.empty(),
                        List.of("radiación solar"),
                        Optional.empty(),
                        Optional.empty(),
                        true,
                        true);
        String context = "Se acordó solicitar presupuesto para el ascensor.";
        String answer = "Sí, se habló de videovigilancia en la reunión.";
        FactualVerifierResult result = FactualAnswerVerifier.verify(constraints, context, answer);
        assertThat(result.passed()).isFalse();
    }

    @Test
    void fails_whenGasLeakInquiryGetsVideovigilanciaSubstitution() {
        FactualQuestionConstraints constraints =
                new FactualQuestionConstraints(
                        AnswerGroundingPolicy.NEGATIVE_EVIDENCE,
                        FactualConstraintType.TOPIC,
                        Optional.empty(),
                        List.of("fuga de gas"),
                        Optional.empty(),
                        Optional.empty(),
                        true,
                        false);
        String context =
                "Acta del 24 de febrero de 2025. Se acordó instalar cámaras de videovigilancia en las entradas.";
        String answer =
                "Se discute la posibilidad de instalar un nuevo sistema de videovigilancia en las entradas y garajes.";
        FactualVerifierResult result = FactualAnswerVerifier.verify(constraints, context, answer);
        assertThat(result.passed()).isFalse();
        assertThat(result.failures()).isNotEmpty();
    }

    @Test
    void fails_whenAbsenceInquirySubstitutesInContextDistractorTopic() {
        FactualQuestionConstraints constraints =
                new FactualQuestionConstraints(
                        AnswerGroundingPolicy.NEGATIVE_EVIDENCE,
                        FactualConstraintType.TOPIC,
                        Optional.empty(),
                        List.of("fuga de gas"),
                        Optional.empty(),
                        Optional.empty(),
                        true,
                        false);
        String context = "Se acordó instalar cámaras de videovigilancia en las entradas.";
        String answer = "Sí, se habló de videovigilancia en la reunión.";
        FactualVerifierResult result = FactualAnswerVerifier.verify(constraints, context, answer);
        assertThat(result.passed()).isFalse();
        assertThat(result.failures()).contains(FactualVerifierFailureReason.UNRELATED_TOPIC);
    }

    @Test
    void fails_whenAffirmativeAnswerUsesUnsupportedFutureDate() {
        FactualQuestionConstraints constraints =
                new FactualQuestionConstraints(
                        AnswerGroundingPolicy.NUMERIC_OR_DATE,
                        FactualConstraintType.DATE,
                        Optional.of("2028-08-25"),
                        List.of(),
                        Optional.empty(),
                        Optional.empty(),
                        true,
                        true);
        String context = "Acta del 25 de agosto de 2026. Duración: una hora.";
        String answer = "Sí, la reunión del 25 de agosto de 2028 duró dos horas.";
        FactualVerifierResult result = FactualAnswerVerifier.verify(constraints, context, answer);
        assertThat(result.passed()).isFalse();
        assertThat(result.failures()).contains(FactualVerifierFailureReason.DATE_MISMATCH);
    }
}
