package com.uniovi.rag.application.service.runtime;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** T-M5-BE-doc-bound - document-bound heuristic for Chat quality cases QC-08, QC-10. */
class DocumentBoundQuestionPolicyTest {

    @Test
    void T_M5_BE_docBound_presidentOfMeetingWithoutActaKeyword() {
        assertThat(RuntimeAnswerPrompts.requiresStrictDocumentGrounding("¿Quién fue el presidente de la reunión?"))
                .isTrue();
    }

    @Test
    void T_M5_BE_docBound_budgetAgreementQuestion() {
        assertThat(
                        RuntimeAnswerPrompts.requiresStrictDocumentGrounding(
                                "¿Qué acuerdos se tomaron sobre el presupuesto en el acta del 24/02/2025?"))
                .isTrue();
    }

    @Test
    void T_M5_BE_docBound_actaCountInProject() {
        assertThat(RuntimeAnswerPrompts.requiresStrictDocumentGrounding("¿Cuántas actas hay en este proyecto?"))
                .isTrue();
    }

    @Test
    void T_M5_BE_docBound_generalJokeNotDocumentBound() {
        assertThat(RuntimeAnswerPrompts.requiresStrictDocumentGrounding("Cuéntame un chiste corto"))
                .isFalse();
    }
}
