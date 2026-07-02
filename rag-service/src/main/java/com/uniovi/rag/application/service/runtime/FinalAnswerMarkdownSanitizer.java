package com.uniovi.rag.application.service.runtime;

/**
 * Final Markdown presentation pass for user-visible assistant answers.
 */
public final class FinalAnswerMarkdownSanitizer {

    private FinalAnswerMarkdownSanitizer() {}

    public static String sanitize(String text) {
        return MarkdownAnswerFormatter.format(text);
    }
}
