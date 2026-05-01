package com.uniovi.rag.application.service.runtime.tracecomparisonexport;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReplayComparisonExportMismatchLineTest {

    @Test
    void recordHoldsComponents() {
        ReplayComparisonExportMismatchLine line =
                new ReplayComparisonExportMismatchLine("a.b", "VALUE", "orig", "replay");
        assertThat(line.fieldPath()).isEqualTo("a.b");
        assertThat(line.category()).isEqualTo("VALUE");
        assertThat(line.originalSnippet()).isEqualTo("orig");
        assertThat(line.replaySnippet()).isEqualTo("replay");
    }
}
