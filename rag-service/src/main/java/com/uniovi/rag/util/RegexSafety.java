package com.uniovi.rag.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utilities to reduce ReDoS (Regular Expression Denial of Service) risk from catastrophic backtracking.
 * <p>
 * Defense in depth: Java's {@link Pattern} engine can exhibit super-linear runtime on pathological inputs
 * when alternations and nested quantifiers backtrack. Bounding the {@link CharSequence} length before
 * {@link Pattern#matcher(CharSequence)} (and before {@link String#replaceAll(String, String)},
 * {@link String#split(String)}, etc.) caps worst-case work regardless of pattern shape.
 * <p>
 * Lengths are conservative for meeting-minute text and query strings; adjust if legitimate documents exceed them.
 */
public final class RegexSafety {

    /** Full document / minute body passed to extraction regex (typical acta &lt; 100K chars). */
    public static final int MAX_DOCUMENT_TEXT_FOR_REGEX = 262_144; // 256 KiB

    /** User queries, tool routing, and metadata keys (far smaller than document text). */
    public static final int MAX_QUERY_TEXT_FOR_REGEX = 32_768;

    /** LLM responses and similar strings for cleanup helpers ({@code removeQuestionRepetition}, etc.). */
    public static final int MAX_LLM_TEXT_FOR_REGEX = 131_072; // 128 KiB

    private RegexSafety() {
    }

    /**
     * Returns a substring of at most {@code maxChars} code units, or {@code null} if input is {@code null}.
     */
    public static String truncateString(String input, int maxChars) {
        if (input == null) {
            return null;
        }
        if (input.length() <= maxChars) {
            return input;
        }
        return input.substring(0, maxChars);
    }

    /**
     * Returns a bounded view suitable for matching without copying when already a {@link String}.
     */
    public static CharSequence bounded(CharSequence input, int maxChars) {
        if (input == null) {
            return "";
        }
        if (input.length() <= maxChars) {
            return input;
        }
        return input.subSequence(0, maxChars);
    }

    /**
     * {@link Pattern#matcher(CharSequence)} on input truncated to {@code maxChars}.
     */
    public static Matcher matcher(Pattern pattern, CharSequence input, int maxChars) {
        return pattern.matcher(bounded(input, maxChars));
    }
}
