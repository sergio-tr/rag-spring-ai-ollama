package com.uniovi.rag.application.service.runtime.query;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class IncompleteQueryHeuristicsTest {

    @Test
    void detectsTrailingPrepositionScope() {
        assertThat(IncompleteQueryHeuristics.detect("qué se habla de cámaras en ?")).isPresent();
        assertThat(IncompleteQueryHeuristics.detect("qué se habla de cámaras en ?").get().reason())
                .isEqualTo(IncompleteQueryHeuristics.Reason.TRAILING_PREPOSITION);
    }

    @Test
    void detectsIncompleteCountFilter() {
        assertThat(IncompleteQueryHeuristics.detect("cuenta las actas en las que")).isPresent();
        assertThat(IncompleteQueryHeuristics.detect("cuenta las actas en las que").get().reason())
                .isEqualTo(IncompleteQueryHeuristics.Reason.INCOMPLETE_COUNT_FILTER);
    }

    @Test
    void allowsCompleteCorpusTopicQuestion() {
        assertThat(IncompleteQueryHeuristics.detect("en qué actas se habla sobre cámaras de seguridad"))
                .isEmpty();
    }

    @Test
    void allowsCompleteRelativeClause() {
        assertThat(
                        IncompleteQueryHeuristics.detect(
                                "lista las actas en las que se habla de cámaras de seguridad"))
                .isEmpty();
    }

    @Test
    void allowsCompleteCountQuestion() {
        assertThat(IncompleteQueryHeuristics.detect("cuenta las actas del 2025")).isEmpty();
    }
}
