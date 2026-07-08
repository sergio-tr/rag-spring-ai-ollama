package com.uniovi.rag.application.service.runtime.query;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class SpanishQueryDateSupportTest {

    @Test
    void findLongDatePhrases_supportsDeAndDelBeforeYear() {
        assertThat(
                        SpanishQueryDateSupport.findLongDatePhrases(
                                "dime los asistentes del acta del 25 de febrero de 2025"))
                .containsExactly("25 de febrero de 2025");
        assertThat(
                        SpanishQueryDateSupport.findLongDatePhrases(
                                "dime los asistentes del acta del 25 de febrero del 2025"))
                .containsExactly("25 de febrero del 2025");
    }

    @Test
    void parseLongDatePhrase_normalizesDelVariant() {
        assertThat(SpanishQueryDateSupport.parseLongDatePhrase("25 de febrero del 2025"))
                .contains(LocalDate.of(2025, 2, 25));
        assertThat(SpanishQueryDateSupport.parseLongDatePhrase("25 de febrero de 2025"))
                .contains(LocalDate.of(2025, 2, 25));
    }

    @Test
    void hasLongDateInText_detectsDelVariant() {
        assertThat(
                        SpanishQueryDateSupport.hasLongDateInText(
                                "cuales son los asistentes del acta del 25 de febrero del 2025?"))
                .isTrue();
    }
}
