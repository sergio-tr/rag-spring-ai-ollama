package com.uniovi.rag.application.service.runtime;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;

import com.uniovi.rag.domain.runtime.policy.AnswerGroundingPolicy;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RuntimeAnswerPromptsPolicyFormattingTest {

    @Test
    void ragUserTurn_documentScoped_formatsWithoutIllegalPercent() {
        assertThatCode(
                        () ->
                                RuntimeAnswerPrompts.ragUserTurn(
                                        "¿Acta del 25 de febrero de 2025?",
                                        "CTX",
                                        AnswerGroundingPolicy.ATTEMPT_WITH_CONTEXT,
                                        true,
                                        Optional.of("pre"),
                                        null))
                .doesNotThrowAnyException();
    }

    @Test
    void secretaryQuestionRequiresDocumentGrounding() {
        assertThatCode(() -> RuntimeAnswerPrompts.requiresStrictDocumentGrounding("¿Quién fue la secretaria?"))
                .doesNotThrowAnyException();
        assertThat(RuntimeAnswerPrompts.requiresStrictDocumentGrounding("¿Quién fue la secretaria?"))
                .isTrue();
    }

    @Test
    void ragUserTurn_negativeEvidencePolicy_forbidsSimilarTopicSubstitution() {
        String prompt =
                RuntimeAnswerPrompts.ragUserTurn(
                        "¿Se habló de radiación solar?",
                        "CTX",
                        AnswerGroundingPolicy.NEGATIVE_EVIDENCE,
                        true,
                        Optional.empty(),
                        null);
        assertThat(prompt).contains("Do NOT answer \"yes\"");
        assertThat(prompt).contains("videovigilancia");
    }

    @Test
    void ragUserTurn_numericOrDatePolicy_forbidsUnsupportedArithmetic() {
        String prompt =
                RuntimeAnswerPrompts.ragUserTurn(
                        "¿Cuántas actas mencionan el ascensor?",
                        "CTX",
                        AnswerGroundingPolicy.NUMERIC_OR_DATE,
                        true,
                        Optional.empty(),
                        null);
        assertThat(prompt).contains("Do not calculate from unrelated numbers");
    }
}
