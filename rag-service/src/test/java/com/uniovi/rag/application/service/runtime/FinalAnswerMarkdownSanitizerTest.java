package com.uniovi.rag.application.service.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FinalAnswerMarkdownSanitizerTest {

    @Test
    void delegatesToMarkdownAnswerFormatter() {
        String input = "Attendees: * Alice Example\nBob Example";

        String out = FinalAnswerMarkdownSanitizer.sanitize(input);

        assertThat(out).contains("Attendees:\n\n- Alice Example");
        assertThat(out).contains("- Bob Example");
    }

    @Test
    void blankInputIsReturnedAsIs() {
        assertThat(FinalAnswerMarkdownSanitizer.sanitize(null)).isNull();
        assertThat(FinalAnswerMarkdownSanitizer.sanitize("   ")).isEqualTo("   ");
    }
}
