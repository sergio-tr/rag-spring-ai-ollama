package com.uniovi.rag.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class QueryDateSupportTest {

    @Test
    void findLongDatePhrases_supportsDeAndDelBeforeYear() {
        assertThat(
                        QueryDateSupport.findLongDatePhrases(
                                "dime los asistentes del acta del 25 de febrero de 2025"))
                .containsExactly("25 de febrero de 2025");
        assertThat(
                        QueryDateSupport.findLongDatePhrases(
                                "dime los asistentes del acta del 25 de febrero del 2025"))
                .containsExactly("25 de febrero del 2025");
    }

    @Test
    void parseLongDatePhrase_normalizesDelVariant() {
        assertThat(QueryDateSupport.parseLongDatePhrase("25 de febrero del 2025"))
                .contains(LocalDate.of(2025, 2, 25));
        assertThat(QueryDateSupport.parseLongDatePhrase("25 de febrero de 2025"))
                .contains(LocalDate.of(2025, 2, 25));
    }

    @Test
    void hasParseableDateInText_coversIsoSlashAndLongSpanish() {
        assertThat(QueryDateSupport.hasParseableDateInText("acta 2025-02-25")).isTrue();
        assertThat(QueryDateSupport.hasParseableDateInText("acta 25/02/2025")).isTrue();
        assertThat(QueryDateSupport.hasParseableDateInText("acta del 25 de febrero del 2025")).isTrue();
        assertThat(QueryDateSupport.hasParseableDateInText("sin fecha concreta")).isFalse();
    }

    @Test
    void extractDateCandidatesFromText_collectsAllFormats() {
        assertThat(
                        QueryDateSupport.extractDateCandidatesFromText(
                                "ISO 2025-02-25, slash 25/02/2025 y 25 de febrero del 2025"))
                .contains("2025-02-25", "25/02/2025", "25 de febrero del 2025");
    }

    @Test
    void parseFlexible_handlesSlashAndLongSpanish() {
        assertThat(QueryDateSupport.parseFlexible("25/02/2025")).contains(LocalDate.of(2025, 2, 25));
        assertThat(QueryDateSupport.parseFlexible("25 de febrero del 2025"))
                .contains(LocalDate.of(2025, 2, 25));
    }

    @Test
    void extractDateCandidatesFromText_supportsTwoDigitYearSlash() {
        assertThat(QueryDateSupport.extractDateCandidatesFromText("hazme un resumen del 25/02/26"))
                .contains("25/02/26");
        assertThat(QueryDateSupport.parseFlexible("25/02/26")).contains(LocalDate.of(2026, 2, 25));
        assertThat(QueryDateSupport.firstNormalizedIsoDateInText("presidente del acta del 25/02/26"))
                .contains("2026-02-25");
    }

    @Test
    void hasExplicitDateSignalInText_includesYearAnchors() {
        assertThat(QueryDateSupport.hasExplicitDateSignalInText("resume el acta del año 2030")).isTrue();
        assertThat(QueryDateSupport.hasExplicitDateSignalInText("actas en 2025")).isTrue();
    }
}
