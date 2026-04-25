package com.uniovi.rag.service.evaluation;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JudgeScoreParserTest {

    @Test
    void parseScores_extractsAllFiveDimensions() {
        String text =
                """
                Correctness: [5]
                Context Sufficiency: 3
                Relevance: [2.0]
                Independence: 1
                Groundedness: [4]
                """;
        Map<String, Integer> out = JudgeScoreParser.parseScores(text);
        assertThat(out)
                .containsEntry("correctness", 5)
                .containsEntry("context_sufficiency", 3)
                .containsEntry("relevance", 2)
                .containsEntry("independence", 1)
                .containsEntry("groundedness", 4);
    }

    @Test
    void parseScores_returnsNullValuesWhenMissing() {
        Map<String, Integer> out = JudgeScoreParser.parseScores("Correctness: 2");
        assertThat(out.get("correctness")).isEqualTo(2);
        assertThat(out.get("context_sufficiency")).isNull();
        assertThat(out.get("relevance")).isNull();
        assertThat(out.get("independence")).isNull();
        assertThat(out.get("groundedness")).isNull();
    }

    @Test
    void parseScores_emptyInput_returnsEmptyMap() {
        assertThat(JudgeScoreParser.parseScores(null)).isEmpty();
        assertThat(JudgeScoreParser.parseScores("   ")).isEmpty();
    }
}

