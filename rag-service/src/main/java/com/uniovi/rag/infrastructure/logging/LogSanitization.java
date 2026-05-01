package com.uniovi.rag.infrastructure.logging;

/**
 * Helpers for log messages so user-influenced values are not line-breaking (S5145).
 */
public final class LogSanitization {

    private LogSanitization() {}

    /**
     * Produces a single-line string safe for standard logging of untrusted or user-derived text.
     */
    public static String singleLineForLog(String value) {
        if (value == null) {
            return "null";
        }
        if (value.indexOf('\n') < 0 && value.indexOf('\r') < 0) {
            return value;
        }
        return value.replace("\r", "").replace("\n", " ");
    }
}
