package com.uniovi.rag.application.service.runtime.factual;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.domain.runtime.policy.AnswerGroundingPolicy;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FactualGroundingPolicySelectorTest {

    @Test
    void selectsNegativeEvidence_forAbsenceQuestion() {
        AnswerGroundingPolicy policy =
                FactualGroundingPolicySelector.selectPolicy(
                        "¿Se habló de la radiación solar en alguna reunión?",
                        true,
                        null,
                        Optional.empty(),
                        List.of("radiación solar"),
                        Optional.empty(),
                        Optional.empty(),
                        true);
        assertThat(policy).isEqualTo(AnswerGroundingPolicy.NEGATIVE_EVIDENCE);
    }

    @Test
    void selectsNegativeEvidence_forAbsenceCountQuestionEvenWithQueryTypeHint() {
        AnswerGroundingPolicy policy =
                FactualGroundingPolicySelector.selectPolicy(
                        "Número de actas registradas en el año 2028.",
                        true,
                        QueryType.COUNT_DOCUMENTS,
                        Optional.of("2028"),
                        List.of(),
                        Optional.empty(),
                        Optional.empty(),
                        true);
        assertThat(policy).isEqualTo(AnswerGroundingPolicy.NEGATIVE_EVIDENCE);
    }

    @Test
    void selectsNumericOrDate_forCountQuestion() {
        AnswerGroundingPolicy policy =
                FactualGroundingPolicySelector.selectPolicy(
                        "¿Cuántas actas mencionan el ascensor?",
                        true,
                        QueryType.COUNT_DOCUMENTS,
                        Optional.empty(),
                        List.of("ascensor"),
                        Optional.empty(),
                        Optional.empty(),
                        false);
        assertThat(policy).isEqualTo(AnswerGroundingPolicy.NUMERIC_OR_DATE);
    }

    @Test
    void selectsNegativeEvidence_forNonDocumentBoundAbsenceInquiry() {
        AnswerGroundingPolicy policy =
                FactualGroundingPolicySelector.selectPolicy(
                        "¿Qué se comentó respecto a la fuga de gas?",
                        false,
                        QueryType.FIND_PARAGRAPH,
                        Optional.empty(),
                        List.of("fuga de gas"),
                        Optional.empty(),
                        Optional.empty(),
                        true);
        assertThat(policy).isEqualTo(AnswerGroundingPolicy.NEGATIVE_EVIDENCE);
    }

    @Test
    void selectsStrict_forDateAndTopic() {
        AnswerGroundingPolicy policy =
                FactualGroundingPolicySelector.selectPolicy(
                        "Confirma si Jorge aparece en el acta del 25 de agosto de 2026.",
                        true,
                        null,
                        Optional.of("2026-08-25"),
                        List.of("Jorge"),
                        Optional.of("Jorge"),
                        Optional.empty(),
                        false);
        assertThat(policy).isEqualTo(AnswerGroundingPolicy.STRICT_GROUNDED);
    }
}
