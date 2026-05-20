package com.uniovi.rag.application.service.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class JudgeScoreParserTest {

    @Test
    void parseScores_extractsKnownScores() {
        String text = """
                Correctness: [5]
                Context Sufficiency: [3.0]
                Relevance: 2
                Independence: [1]
                Groundedness: [4]
                """;

        Map<String, Integer> scores = JudgeScoreParser.parseScores(text);

        assertThat(scores).containsEntry("correctness", 5);
        assertThat(scores).containsEntry("context_sufficiency", 3);
        assertThat(scores).containsEntry("relevance", 2);
        assertThat(scores).containsEntry("independence", 1);
        assertThat(scores).containsEntry("groundedness", 4);
    }

    @Test
    void parseScores_doesNotInsertNullValues() {
        Map<String, Integer> out = JudgeScoreParser.parseScores("Correctness: 2");
        assertThat(out).containsEntry("correctness", 2);
        assertThat(out).doesNotContainKey("context_sufficiency");
        assertThat(out).doesNotContainKey("relevance");
        assertThat(out).doesNotContainKey("independence");
        assertThat(out).doesNotContainKey("groundedness");
    }

    @Test
    void parseScores_emptyInput_returnsEmptyMap() {
        assertThat(JudgeScoreParser.parseScores(null)).isEmpty();
        assertThat(JudgeScoreParser.parseScores("   ")).isEmpty();
    }
}

