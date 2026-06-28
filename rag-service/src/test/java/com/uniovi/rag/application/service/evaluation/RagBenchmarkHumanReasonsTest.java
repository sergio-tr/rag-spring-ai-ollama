package com.uniovi.rag.application.service.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RagBenchmarkHumanReasonsTest {

    @Test
    void humanize_mapsKnownCodes() {
        assertThat(RagBenchmarkHumanReasons.humanize("CORPUS_EMPTY"))
                .contains("no documents");
        assertThat(RagBenchmarkHumanReasons.humanize("PRESET_NOT_SUPPORTED"))
                .contains("not supported");
    }
}
