package com.uniovi.rag.application.service.runtime.factual;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniovi.rag.domain.runtime.factual.FactualConstraintType;
import com.uniovi.rag.domain.runtime.factual.FinalAnswerSource;
import com.uniovi.rag.domain.runtime.policy.AnswerGroundingPolicy;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FactualAnswerVerificationLoopTest {

    @Test
    void returnsDraft_whenVerificationPasses() {
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
        FactualAnswerVerificationLoop.Outcome outcome =
                FactualAnswerVerificationLoop.apply(
                        "¿Se habló de radiación solar?",
                        constraints,
                        "contexto sin radiación solar",
                        "No se menciona la radiación solar.",
                        prompt -> "should not run");
        assertThat(outcome.finalAnswerSource()).isEqualTo(FinalAnswerSource.GENERATED);
        assertThat(outcome.abstentionTriggered()).isFalse();
    }

    @Test
    void forcesAbstention_whenRevisionStillFails() {
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
        FactualAnswerVerificationLoop.Outcome outcome =
                FactualAnswerVerificationLoop.apply(
                        "¿Se habló de radiación solar?",
                        constraints,
                        "Se habló de videovigilancia.",
                        "Sí, se habló de videovigilancia.",
                        prompt -> "Sí, se habló de videovigilancia otra vez.");
        assertThat(outcome.finalAnswerSource()).isEqualTo(FinalAnswerSource.FORCED_ABSTENTION);
        assertThat(outcome.abstentionTriggered()).isTrue();
        assertThat(outcome.answerText())
                .contains("No tengo contexto suficiente en las actas proporcionadas para responder con seguridad.");
    }

    @Test
    void emitsSkippedTelemetry_whenContextIsBlank() {
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
        FactualAnswerVerificationLoop.Outcome outcome =
                FactualAnswerVerificationLoop.apply(
                        "¿Qué se comentó respecto a la fuga de gas?",
                        constraints,
                        "",
                        "No se comentaron datos sobre la fuga de gas.",
                        prompt -> "should not run");
        assertThat(outcome.finalAnswerSource()).isEqualTo(FinalAnswerSource.GENERATED);
        assertThat(outcome.stages()).anyMatch(
                s -> FactualVerifierTelemetry.STAGE_VERIFY_SKIPPED.equals(s.stageName()));
        assertThat(outcome.stages().stream().anyMatch(s -> s.message() != null && s.message().contains("no_retrieved_context")))
                .isTrue();
    }

    @Test
    void returnsRevisedAnswer_whenRevisionPasses() {
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
        FactualAnswerVerificationLoop.Outcome outcome =
                FactualAnswerVerificationLoop.apply(
                        "¿Se habló de radiación solar?",
                        constraints,
                        "Se habló de videovigilancia.",
                        "Sí, se habló de videovigilancia.",
                        prompt -> "No se menciona la radiación solar en las actas.");
        assertThat(outcome.finalAnswerSource()).isEqualTo(FinalAnswerSource.GENERATED);
        assertThat(outcome.abstentionTriggered()).isFalse();
        assertThat(outcome.answerText()).contains("No se menciona la radiación solar");
    }
}
