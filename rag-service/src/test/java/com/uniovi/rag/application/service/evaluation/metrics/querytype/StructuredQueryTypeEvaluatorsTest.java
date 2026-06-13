package com.uniovi.rag.application.service.evaluation.metrics.querytype;

import com.uniovi.rag.domain.model.QueryType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StructuredQueryTypeEvaluatorsTest {

    @Test
    void countScore_matchesNormalizedIntegers() {
        assertThat(StructuredQueryTypeEvaluators.countScore("There are 12 documents", "Answer: 12"))
                .contains(1.0);
        assertThat(StructuredQueryTypeEvaluators.countScore("12", "13")).contains(0.0);
    }

    @Test
    void booleanScore_acceptsYesNoVariants() {
        assertThat(StructuredQueryTypeEvaluators.booleanScore("yes", "Yes, it is true.")).contains(1.0);
        assertThat(StructuredQueryTypeEvaluators.booleanScore("no", "yes")).contains(0.0);
    }

    @Test
    void setF1_computesEntityOverlap() {
        assertThat(StructuredQueryTypeEvaluators.setF1("Ada; Bob", "Ada, Carol")).isPresent();
        assertThat(StructuredQueryTypeEvaluators.setF1("Ada; Bob", "Ada, Carol").get()).isBetween(0.0, 1.0);
    }

    @Test
    void evaluate_returnsEmptyWhenInsufficientGold() {
        assertThat(
                        StructuredQueryTypeEvaluators.evaluate(
                                QueryType.COUNT_DOCUMENTS, "", "12", ""))
                .isEmpty();
    }

    @Test
    void textScore_usesContainmentForFieldQueries() {
        assertThat(StructuredQueryTypeEvaluators.textScore("Paris", "The city is Paris today."))
                .contains(1.0);
    }

    @Test
    void durationEvaluation_matchesMinutes() {
        var result = StructuredQueryTypeEvaluators.durationEvaluation("45 minutes", "The meeting lasted 45 mins.");
        assertThat(result.status()).isEqualTo(com.uniovi.rag.application.service.evaluation.metrics.StructuredScoreStatus.COMPUTED);
        assertThat(result.durationMatch()).isTrue();
        assertThat(result.score()).isEqualTo(1.0);
    }

    @Test
    void fieldEvaluation_matchesIsoDates() {
        var result =
                StructuredQueryTypeEvaluators.fieldEvaluation("2024-06-01", "The event happened on 2024-06-01.");
        assertThat(result.dateMatch()).isTrue();
        assertThat(result.score()).isEqualTo(1.0);
    }

    @Test
    void compareEvaluation_unavailableWithoutStructuredGold() {
        var result = StructuredQueryTypeEvaluators.compareEvaluation("yes", "no");
        assertThat(result.status())
                .isEqualTo(com.uniovi.rag.application.service.evaluation.metrics.StructuredScoreStatus.NOT_AVAILABLE);
    }
}
