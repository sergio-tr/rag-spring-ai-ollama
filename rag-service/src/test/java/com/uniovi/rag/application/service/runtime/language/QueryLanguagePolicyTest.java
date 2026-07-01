package com.uniovi.rag.application.service.runtime.language;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class QueryLanguagePolicyTest {

    @Test
    void detect_spanishFromActaKeyword() {
        assertThat(QueryLanguagePolicy.detect("¿Cuál es la fecha del acta?"))
                .isEqualTo(QueryLanguagePolicy.DetectedLanguage.SPANISH);
    }

    @Test
    void detect_englishFromMeetingKeyword() {
        assertThat(QueryLanguagePolicy.detect("What is the meeting date?"))
                .isEqualTo(QueryLanguagePolicy.DetectedLanguage.ENGLISH);
    }

    @Test
    void detect_englishFromWasDiscussed() {
        assertThat(QueryLanguagePolicy.detect("Was the elevator discussed?"))
                .isEqualTo(QueryLanguagePolicy.DetectedLanguage.ENGLISH);
    }

    @Test
    void answerInstruction_english() {
        assertThat(QueryLanguagePolicy.answerInQueryLanguageInstruction(QueryLanguagePolicy.DetectedLanguage.ENGLISH))
                .contains("English");
    }

    @Test
    void documentLanguageTag_fromEnglishContent() {
        assertThat(QueryLanguagePolicy.documentLanguageTag("Meeting minutes for the board on March 5", "doc.txt"))
                .isEqualTo("en");
    }
}
