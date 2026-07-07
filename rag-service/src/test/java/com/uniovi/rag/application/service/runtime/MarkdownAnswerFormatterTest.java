package com.uniovi.rag.application.service.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MarkdownAnswerFormatterTest {

    private static final String ACTA2_ATTENDEES_INPUT =
            """
            Based on the context provided for the meeting acta dated February 25, 2025 (File: ACTA 2.pdf), the attendees are listed as follows: * Antonio Martínez López (Presidente)

            Carmen Herrera Jiménez
            Luis Ramírez Ortega
            Isabel Castro Torres
            Jorge Moreno Navarro
            Beatriz Suárez Aguilar
            Eduardo Rojas Martínez
            Silvia Medina Pérez
            Ricardo Flores Sánchez
            Patricia Navarro Díaz
            Daniel Gutiérrez Moreno
            Rosa Aguilar Fernández
            Francisco Torres Delgado
            Laura Díaz Castro
            Marta González Ramírez
            Juan Pérez Gutiérrez
            Ana Sánchez Herrera
            Pedro Jiménez Suárez
            Roberto Martínez Vázquez
            Natalia Vázquez Gutiérrez (Secretaria) The document states that there were 20 owners in attendance according to the signed list. Source: File: ACTA 2.pdf, Acta 2025-02-25.
            """;

    private static final String ACTA2_ATTENDEES_EXPECTED =
            """
            Based on the context provided for the meeting acta dated February 25, 2025 (File: ACTA 2.pdf), the attendees are listed as follows:

            - Antonio Martínez López (Presidente)
            - Carmen Herrera Jiménez
            - Luis Ramírez Ortega
            - Isabel Castro Torres
            - Jorge Moreno Navarro
            - Beatriz Suárez Aguilar
            - Eduardo Rojas Martínez
            - Silvia Medina Pérez
            - Ricardo Flores Sánchez
            - Patricia Navarro Díaz
            - Daniel Gutiérrez Moreno
            - Rosa Aguilar Fernández
            - Francisco Torres Delgado
            - Laura Díaz Castro
            - Marta González Ramírez
            - Juan Pérez Gutiérrez
            - Ana Sánchez Herrera
            - Pedro Jiménez Suárez
            - Roberto Martínez Vázquez
            - Natalia Vázquez Gutiérrez (Secretaria)

            The document states that there were 20 owners in attendance according to the signed list.

            Source: File: ACTA 2.pdf, Acta 2025-02-25.
            """;

    @Test
    void acta2AttendeesRegressionFormatsListsAndFooters() {
        String out = MarkdownAnswerFormatter.format(ACTA2_ATTENDEES_INPUT);

        assertThat(out).isEqualTo(ACTA2_ATTENDEES_EXPECTED.trim());
    }

    @Test
    void inlineColonBulletBecomesSeparatedList() {
        String out = MarkdownAnswerFormatter.format("Items are: * first item");

        assertThat(out).isEqualTo("Items are:\n\n- first item");
    }

    @Test
    void inlineColonDashBulletBecomesSeparatedList() {
        String out = MarkdownAnswerFormatter.format("Items are: - first item");

        assertThat(out).isEqualTo("Items are:\n\n- first item");
    }

    @Test
    void plainLinesAfterFirstBulletBecomeBullets() {
        String input =
                """
                Attendees:
                - Alice Example
                Bob Example
                Carol Example
                """;

        String out = MarkdownAnswerFormatter.format(input);

        assertThat(out).contains("- Alice Example");
        assertThat(out).contains("- Bob Example");
        assertThat(out).contains("- Carol Example");
    }

    @Test
    void paragraphAfterFinalListItemIsSeparated() {
        String input = "- Alice\nBob\nThe meeting ended at 20:30.";

        String out = MarkdownAnswerFormatter.format(input);

        assertThat(out).contains("- Alice\n- Bob\n\nThe meeting ended at 20:30.");
    }

    @Test
    void sourceFooterGetsItsOwnParagraph() {
        String input = "Answer body with evidence. Source: ACTA 2.pdf.";

        String out = MarkdownAnswerFormatter.format(input);

        assertThat(out).isEqualTo("Answer body with evidence.\n\nSource: ACTA 2.pdf.");
    }

    @Test
    void codeBlocksAreNotModified() {
        String input =
                """
                Intro text.

                ```java
                List<String> names = List.of(
                    "Alice",
                    "Bob"
                );
                ```

                After code.
                """;

        String out = MarkdownAnswerFormatter.format(input);

        assertThat(out).contains("```java");
        assertThat(out).contains("List<String> names");
        assertThat(out).contains("After code.");
    }

    @Test
    void markdownTablesAreNotModified() {
        String input =
                """
                | Name | Role |
                | --- | --- |
                | Alice | President |
                """;

        String out = MarkdownAnswerFormatter.format(input);

        assertThat(out).isEqualTo(input.trim());
    }

    @Test
    void normalizesCrlfToLf() {
        String input = "Line one\r\nLine two\r\n- item";

        String out = MarkdownAnswerFormatter.format(input);

        assertThat(out).doesNotContain("\r");
        assertThat(out).contains("Line one\nLine two");
    }

    @Test
    void preservesSpanishCountAnswerWithFollowingSentence() {
        String input = "Una. La acta 1.pdf tuvo menos de diez participantes.";

        String out = MarkdownAnswerFormatter.format(input);

        assertThat(out).isEqualTo(input);
    }

    @Test
    void preservesNewlinesWhenCollapsingHorizontalWhitespace() {
        String input = "Line  one\nLine   two";

        String out = MarkdownAnswerFormatter.collapseHorizontalWhitespacePreservingNewlines(input);

        assertThat(out).isEqualTo("Line one\nLine two");
    }
}
