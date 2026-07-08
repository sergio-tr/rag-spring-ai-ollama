package com.uniovi.rag.application.service.runtime.query;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniovi.rag.domain.runtime.query.EntityExtractionResult;
import org.junit.jupiter.api.Test;

class ActaSlashDateSupportTest {

    @Test
    void parseToIso_twoDigitYear2026() {
        assertThat(ActaSlashDateSupport.parseToIso("25/02/26")).contains("2026-02-25");
    }

    @Test
    void parseToIso_twoDigitYear2025() {
        assertThat(ActaSlashDateSupport.parseToIso("25/02/25")).contains("2025-02-25");
    }

    @Test
    void parseToIso_fourDigitYear() {
        assertThat(ActaSlashDateSupport.parseToIso("25/02/2026")).contains("2026-02-25");
    }

    @Test
    void hasSlashOrDashDateInText_detectsShortYear() {
        assertThat(ActaSlashDateSupport.hasSlashOrDashDateInText("hazme un resumen del 25/02/26")).isTrue();
    }

    @Test
    void actaFieldAnchor_resumenSlashDate_doesNotNeedAnchor() {
        assertThat(ActaFieldAnchorHeuristics.isDatedSummaryRequest("hazme un resumen del 25/02/26"))
                .isTrue();
        assertThat(
                        ActaFieldAnchorHeuristics.needsActaAnchor(
                                "hazme un resumen del 25/02/26",
                                EntityExtractionResult.emptyWithNote(null)))
                .isFalse();
    }
}
