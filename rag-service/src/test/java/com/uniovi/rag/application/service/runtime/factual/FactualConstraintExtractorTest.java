package com.uniovi.rag.application.service.runtime.factual;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniovi.rag.domain.runtime.factual.FactualConstraintType;
import com.uniovi.rag.domain.runtime.policy.AnswerGroundingPolicy;
import com.uniovi.rag.domain.runtime.query.EntityExtractionResult;
import com.uniovi.rag.domain.runtime.query.AmbiguityAssessment;
import com.uniovi.rag.domain.runtime.query.ExpectedAnswerShape;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.query.StructuredRewriteResult;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FactualConstraintExtractorTest {

    @Test
    void extractsTopicAndNegativePolicy() {
        QueryPlan plan = minimalPlan("¿Se habló de la radiación solar en alguna reunión?");
        FactualQuestionConstraints constraints = FactualConstraintExtractor.extract(plan.rewrittenQueryText(), plan, Optional.empty());
        assertThat(constraints.groundingPolicy()).isEqualTo(AnswerGroundingPolicy.NEGATIVE_EVIDENCE);
        assertThat(constraints.absenceQuestion()).isTrue();
        assertThat(constraints.topicPhrases()).isNotEmpty();
    }

    @Test
    void extractsDateConstraint() {
        QueryPlan plan = minimalPlan("Duración de la reunión del 25 de febrero de 2026.");
        FactualQuestionConstraints constraints = FactualConstraintExtractor.extract(plan.rewrittenQueryText(), plan, Optional.empty());
        assertThat(constraints.requiredDateIso()).isPresent();
        assertThat(constraints.constraintType()).isIn(FactualConstraintType.DATE, FactualConstraintType.MIXED, FactualConstraintType.NUMERIC);
    }

    @Test
    void extractsThresholdAttendanceAsAbsenceQuestion() {
        QueryPlan plan = minimalPlan("En cuántas actas participaron menos de diez personas.");
        FactualQuestionConstraints constraints = FactualConstraintExtractor.extract(plan.rewrittenQueryText(), plan, Optional.empty());
        assertThat(constraints.absenceQuestion()).isTrue();
        assertThat(constraints.groundingPolicy()).isEqualTo(AnswerGroundingPolicy.NEGATIVE_EVIDENCE);
    }

    @Test
    void extractsFutureYearCountAsAbsenceQuestion() {
        QueryPlan plan = minimalPlan("Número de actas registradas en el año 2028.");
        FactualQuestionConstraints constraints = FactualConstraintExtractor.extract(plan.rewrittenQueryText(), plan, Optional.empty());
        assertThat(constraints.absenceQuestion()).isTrue();
        assertThat(constraints.groundingPolicy()).isEqualTo(AnswerGroundingPolicy.NEGATIVE_EVIDENCE);
    }

    @Test
    void extractsTopicAbsenceInquiryAsNegativeEvidence() {
        QueryPlan plan = minimalPlan("¿Qué se comentó respecto a la fuga de gas?");
        FactualQuestionConstraints constraints = FactualConstraintExtractor.extract(plan.rewrittenQueryText(), plan, Optional.empty());
        assertThat(constraints.absenceQuestion()).isTrue();
        assertThat(constraints.groundingPolicy()).isEqualTo(AnswerGroundingPolicy.NEGATIVE_EVIDENCE);
    }

    @Test
    void extractsFutureYearDurationAsAbsenceQuestion() {
        QueryPlan plan = minimalPlan("¿Cuánto duró la reunión del 25 de agosto de 2028?");
        FactualQuestionConstraints constraints = FactualConstraintExtractor.extract(plan.rewrittenQueryText(), plan, Optional.empty());
        assertThat(constraints.absenceQuestion()).isTrue();
        assertThat(constraints.groundingPolicy()).isEqualTo(AnswerGroundingPolicy.NEGATIVE_EVIDENCE);
        assertThat(constraints.requiredDateIso()).isPresent();
    }

    @Test
    void extractsTopicSobreInquiryAsAbsenceQuestion() {
        QueryPlan plan = minimalPlan("¿Qué se dijo sobre la fuga de gas?");
        FactualQuestionConstraints constraints = FactualConstraintExtractor.extract(plan.rewrittenQueryText(), plan, Optional.empty());
        assertThat(constraints.absenceQuestion()).isTrue();
        assertThat(constraints.groundingPolicy()).isEqualTo(AnswerGroundingPolicy.NEGATIVE_EVIDENCE);
        assertThat(constraints.topicPhrases()).anyMatch(t -> t.toLowerCase().contains("fuga"));
    }

    @Test
    void buildsConstraintsPromptBlock() {
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
        String block = FactualConstraintExtractor.constraintsPromptBlock(constraints);
        assertThat(block).contains("QuestionConstraints").contains("radiación solar");
    }

    private static QueryPlan minimalPlan(String question) {
        return new QueryPlan(
                QueryPlan.VERSION_P6_QU_CORE_V1,
                question,
                question,
                question,
                question,
                "UNCLASSIFIED",
                Optional.empty(),
                com.uniovi.rag.domain.runtime.query.ClassifierStatus.DISABLED,
                com.uniovi.rag.domain.runtime.query.QueryIntent.UNKNOWN,
                Map.of(),
                List.of(),
                List.of(),
                EntityExtractionResult.emptyWithNote(""),
                StructuredRewriteResult.identityDisabled(question, "test"),
                ExpectedAnswerShape.UNKNOWN,
                AmbiguityAssessment.sufficient(),
                "test",
                "",
                List.of());
    }
}
